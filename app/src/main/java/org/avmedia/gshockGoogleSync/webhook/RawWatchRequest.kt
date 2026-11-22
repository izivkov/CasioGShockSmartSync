package org.avmedia.gshockGoogleSync.webhook

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.ble.IDataReceived
import org.avmedia.gshockapi.casio.MessageDispatcher
import org.avmedia.gshockapi.io.IO

fun interface RawRequester {
    suspend fun request(code: Int, timeoutMs: Long): List<String>
}

/**
 * Sends one Casio read-request code (one byte, request characteristic 26eb002c) and returns the raw
 * notifications that answer it ("0x28 06 1D ..."), outside the library's per-model profile.
 *
 * The library's notification handler is replaced only while a request is outstanding: the answers
 * are kept here and not handed to the library (some, such as 0x10, would be taken as real data).
 * After each request the library's own handler is put back. One request at a time.
 */
@Singleton
class RawWatchRequest @Inject constructor() : RawRequester {
    private val mutex = Mutex()
    @Volatile private var waiting = false
    @Volatile private var lastRxMillis = 0L
    private val buffer = ArrayList<String>()

    private val collector = IDataReceived { data ->
        if (data == null) return@IDataReceived
        if (waiting) {
            synchronized(buffer) { buffer.add(data) }
            lastRxMillis = System.currentTimeMillis()
        } else {
            forwardToLibrary(data)
        }
    }

    private fun forwardToLibrary(data: String) {
        try {
            MessageDispatcher.onReceived(data, WatchInfo.protocol)
        } catch (t: Throwable) {
            WebhookDiagnostics.logError("Raw request: library handler failed: ${WebhookDiagnostics.oneLine(t)}")
        }
    }

    override suspend fun request(code: Int, timeoutMs: Long): List<String> = mutex.withLock {
        synchronized(buffer) { buffer.clear() }
        lastRxMillis = 0L
        try {
            Connection.setDataCallback(collector)
        } catch (t: Throwable) {
            WebhookDiagnostics.logError("Raw request: cannot hook notifications: ${WebhookDiagnostics.oneLine(t)}")
        }
        waiting = true
        try {
            IO.writeCmd(GetSetMode.GET, byteArrayOf(code.toByte()))
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                delay(100)
                val rx = lastRxMillis
                if (rx != 0L && System.currentTimeMillis() - rx > 400) break
            }
        } finally {
            waiting = false
            try {
                Connection.setDataCallback(IDataReceived { data -> data?.let { forwardToLibrary(it) } })
            } catch (ignored: Throwable) {
            }
        }
        synchronized(buffer) { buffer.toList() }
    }
}
