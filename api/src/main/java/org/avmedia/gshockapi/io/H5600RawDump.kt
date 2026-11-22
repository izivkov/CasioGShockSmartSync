package org.avmedia.gshockapi.io

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** What one LifeLog transfer delivered, before decoding. */
class H5600Capture(
    val notifications: List<ByteArray>,
    val announcements: Map<Int, Int>,
    val stream: ByteArray,
)

/**
 * Keeps the raw bytes of the last transfers in a folder, so a person can share them.
 * One folder per transfer holds `stream.bin` (the reassembled, decoded-from-XOR stream) and
 * `notifications.txt` (the received notifications, in the format of the test recordings:
 * `A <index hex> <length>` lines, then `N <hex>` lines in arrival order). Nothing is sent anywhere.
 */
class H5600RawDump(private val dir: File, private val keep: Int = KEEP_TRANSFERS) {
    fun save(capture: H5600Capture, nowMillis: Long): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val base = "lifelog-" + stamp.format(Date(nowMillis))
        var folder = File(dir, base)
        var n = 1
        while (folder.exists()) folder = File(dir, "$base-${n++}")
        check(folder.mkdirs()) { "cannot create ${folder.path}" }
        File(folder, STREAM_FILE).writeBytes(capture.stream)
        File(folder, NOTIFICATIONS_FILE).writeText(notificationsText(capture, nowMillis))
        transfers().drop(keep).forEach { it.deleteRecursively() }
        return folder
    }

    /** Transfer folders, newest first. */
    fun transfers(): List<File> =
        dir.listFiles { f -> f.isDirectory && f.name.startsWith("lifelog-") }?.sortedByDescending { it.name }.orEmpty()

    /** Zips the newest transfer into [target]; null when there is none. */
    fun zipLatest(target: File): File? {
        val latest = transfers().firstOrNull() ?: return null
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            for (name in listOf(STREAM_FILE, NOTIFICATIONS_FILE)) {
                zip.putNextEntry(ZipEntry(name))
                File(latest, name).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return target
    }

    private fun notificationsText(capture: H5600Capture, nowMillis: Long): String =
        buildString {
            append("# DW-H5600 LifeLog transfer, received ").append(nowMillis).append(" (epoch ms)\n")
            for ((index, length) in capture.announcements) append("A %02x %d\n".format(index, length))
            for (n in capture.notifications) append("N ").append(n.joinToString("") { "%02x".format(it.toInt() and 0xFF) }).append('\n')
        }

    companion object {
        const val KEEP_TRANSFERS = 7
        private const val STREAM_FILE = "stream.bin"
        private const val NOTIFICATIONS_FILE = "notifications.txt"
    }
}
