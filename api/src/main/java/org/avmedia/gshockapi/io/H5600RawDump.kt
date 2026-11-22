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
    /** Why the transfer failed; null when it finished. A failed one is kept too and marked in its name and file. */
    val failure: String? = null,
)

/**
 * Keeps the raw bytes of the transfers of the last 30 days in a folder (for decoding them again later), so a person can share them.
 * One folder per transfer holds `stream.bin` (the reassembled, decoded-from-XOR stream) and
 * `notifications.txt` (the received notifications, in the format of the test recordings:
 * `A <index hex> <length>` lines, then `N <hex>` lines in arrival order). Nothing is sent anywhere.
 */
class H5600RawDump(
    private val dir: File,
    private val keep: Int = KEEP_TRANSFERS,
    private val keepDays: Int = KEEP_DAYS,
) {
    fun save(capture: H5600Capture, nowMillis: Long): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val base = "lifelog-" + stamp.format(Date(nowMillis)) + if (capture.failure != null) "-failed" else ""
        var folder = File(dir, base)
        var n = 1
        while (folder.exists()) folder = File(dir, "$base-${n++}")
        check(folder.mkdirs()) { "cannot create ${folder.path}" }
        File(folder, STREAM_FILE).writeBytes(capture.stream)
        File(folder, NOTIFICATIONS_FILE).writeText(notificationsText(capture, nowMillis))
        transfers().filterIndexed { i, f -> i >= keep || ageMillis(f, nowMillis) > keepDays * DAY_MS }.forEach { it.deleteRecursively() }
        return folder
    }

    /** How old a transfer folder is, read from its name (UTC stamp); 0 when the name cannot be read, so it is kept. */
    private fun ageMillis(folder: File, nowMillis: Long): Long {
        val stamp = folder.name.removePrefix("lifelog-").take(15)
        val at = runCatching { SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(stamp)?.time }.getOrNull()
        return if (at == null) 0 else nowMillis - at
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
            // The connection events (49 00 checks, 47 notices, setup writes) with their times, when there are any.
            File(dir, CONNECTION_LOG).takeIf { it.isFile }?.let { log ->
                zip.putNextEntry(ZipEntry(CONNECTION_LOG))
                log.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return target
    }

    private fun notificationsText(capture: H5600Capture, nowMillis: Long): String =
        buildString {
            append("# DW-H5600 LifeLog transfer, received ").append(nowMillis).append(" (epoch ms)\n")
            capture.failure?.let { append("# FAILED: ").append(it.replace('\n', ' ')).append('\n') }
            for ((index, length) in capture.announcements) append("A %02x %d\n".format(index, length))
            for (n in capture.notifications) append("N ").append(n.joinToString("") { "%02x".format(it.toInt() and 0xFF) }).append('\n')
        }

    companion object {
        /** Upper bound only; the age limit [KEEP_DAYS] is what normally decides (a read every 2 hours is about 360). */
        const val KEEP_TRANSFERS = 400
        const val KEEP_DAYS = 30
        private const val DAY_MS = 24L * 60 * 60 * 1000
        const val CONNECTION_LOG = "connection.log"
        private const val STREAM_FILE = "stream.bin"
        private const val NOTIFICATIONS_FILE = "notifications.txt"
    }
}
