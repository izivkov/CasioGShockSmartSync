package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.gshockGoogleSync.ui.health.H5600Session
import org.avmedia.gshockapi.io.H5600HealthIO

/**
 * Casio setting request codes read raw (hex, not decoded), so the bytes can be matched against what is
 * changed on the watch. Adding a code is adding it to [CODES]: its variable `watch.raw.0xNN` and its
 * attribute on the "Watch settings (raw)" sensor follow from the table.
 */
object RawSettings {
    val CODES: List<Int> = listOf(
        0x51, 0x52, 0x41, 0x50, 0x82, 0x4F, 0x4A, 0x38, 0x59, 0x4E, 0x54,
        0x55, 0x57, 0x58, 0x40, 0x44, 0x2A, 0x2E, 0x45, 0x43, 0x13, 0x42, 0x3D, 0x53,
    )

    /** The codes the decoded features (face, health settings) read, whatever the raw switch says. */
    val DECODED_CODES: List<Int> = listOf(0x51, 0x41, 0x4F, 0x13, 0x43, 0x45, 0x50, 0x42, 0x3D, 0x53)

    /** What a sync may read: the whole table only while the raw switch is on. */
    fun codesFor(rawOn: Boolean): List<Int> = if (rawOn) CODES else DECODED_CODES

    fun name(code: Int): String = "0x%02X".format(code)

    fun variable(code: Int): String = "watch.raw.${name(code)}"

    /** Never read while a LifeLog transfer runs: both use the same link. */
    val shared = RawSettingsReader(
        busy = { H5600HealthIO.isActive || H5600Session.inFlight.get() },
        watch = { org.avmedia.gshockGoogleSync.watch.CurrentWatch.key() },
    )
}

/**
 * Reads [RawSettings.CODES] one after the other, each at most once per [intervalMs] (a failed read counts,
 * so a code the watch ignores is not retried on every sync). A failure leaves the value unknown and never
 * stops the others. One call reads for at most [BUDGET_MS]; the next call carries on with the codes still due.
 */
class RawSettingsReader(
    private val clock: () -> Long = System::currentTimeMillis,
    private val busy: () -> Boolean = { false },
    private val intervalMs: Long = 30 * 60 * 1000L,
    /** The connected watch's key: the values and read times are that watch's; another watch starts empty. */
    private val watch: () -> String = { "" },
) {
    private val attempted = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private val hex = LinkedHashMap<String, String>()
    private var lastRead: Long? = null
    private var readFor: String = ""

    /** Another watch is connected: nothing of the last one is kept or shown. */
    private fun sameWatch() {
        val now = watch()
        synchronized(hex) {
            if (now == readFor) return
            readFor = now
            attempted.clear()
            hex.clear()
            lastRead = null
        }
    }

    suspend fun refresh(raw: RawRequester, codes: List<Int> = RawSettings.CODES) {
        sameWatch()
        val start = clock()
        for (code in codes) {
            if (busy() || clock() - start > BUDGET_MS) return
            val last = attempted[code]
            if (last != null && clock() - last < intervalMs) continue
            attempted[code] = clock()
            try {
                val value = raw.request(code, REPLY_TIMEOUT_MS).firstNotNullOfOrNull { WatchDecoders.rawAnswer(it, code) }
                synchronized(hex) {
                    if (value != null) {
                        hex[RawSettings.name(code)] = value
                        lastRead = clock()
                    } else {
                        hex.remove(RawSettings.name(code))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                attempted.remove(code)
                throw e
            } catch (t: Throwable) {
                synchronized(hex) { hex.remove(RawSettings.name(code)) }
            }
        }
    }

    /** After a write: the next refresh reads [code] again instead of waiting for the interval. */
    fun forget(code: Int) {
        synchronized(hex) {
            attempted.remove(code)
            hex.remove(RawSettings.name(code))
        }
    }

    /** Code name ("0x41") to hex ("41 01 02"), for the codes that were read. */
    fun values(): Map<String, String> { sameWatch(); return synchronized(hex) { LinkedHashMap(hex) } }

    /** Time of the last successful read of any code, epoch millis; null when none. */
    fun lastReadMillis(): Long? { sameWatch(); return synchronized(hex) { lastRead } }

    private companion object {
        const val BUDGET_MS = 4_000L
        const val REPLY_TIMEOUT_MS = 1_500L
    }
}
