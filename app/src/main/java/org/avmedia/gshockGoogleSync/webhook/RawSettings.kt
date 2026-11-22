package org.avmedia.gshockGoogleSync.webhook

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
        0x55, 0x57, 0x58, 0x40, 0x44, 0x2A, 0x2E, 0x45, 0x43, 0x13,
    )

    fun name(code: Int): String = "0x%02X".format(code)

    fun variable(code: Int): String = "watch.raw.${name(code)}"

    /** Never read while a LifeLog transfer runs: both use the same link. */
    val shared = RawSettingsReader(busy = { H5600HealthIO.isActive || H5600Session.inFlight.get() })
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
) {
    private val attempted = HashMap<Int, Long>()
    private val hex = LinkedHashMap<String, String>()
    private var lastRead: Long? = null

    suspend fun refresh(raw: RawRequester) {
        val start = clock()
        for (code in RawSettings.CODES) {
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

    /** Code name ("0x41") to hex ("41 01 02"), for the codes that were read. */
    fun values(): Map<String, String> = synchronized(hex) { LinkedHashMap(hex) }

    /** Time of the last successful read of any code, epoch millis; null when none. */
    fun lastReadMillis(): Long? = synchronized(hex) { lastRead }

    private companion object {
        const val BUDGET_MS = 4_000L
        const val REPLY_TIMEOUT_MS = 1_500L
    }
}
