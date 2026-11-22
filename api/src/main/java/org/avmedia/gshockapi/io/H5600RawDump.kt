package org.avmedia.gshockapi.io

import java.io.File

/** What one LifeLog transfer delivered, before decoding. */
class H5600Capture(
    val notifications: List<ByteArray>,
    val announcements: Map<Int, Int>,
    val stream: ByteArray,
)

/** Keeps the raw bytes of the last transfers in a folder, for sharing. Not implemented yet. */
class H5600RawDump(private val dir: File, private val keep: Int = KEEP_TRANSFERS) {
    fun save(capture: H5600Capture, nowMillis: Long): File = throw NotImplementedError()

    /** Transfer folders, newest first. */
    fun transfers(): List<File> = emptyList()

    /** Zips the newest transfer into [target]; null when there is none. */
    fun zipLatest(target: File): File? = null

    companion object {
        const val KEEP_TRANSFERS = 7
    }
}
