package org.avmedia.gshockGoogleSync.webhook

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The "Read raw watch settings (for decoding)" switch on the Home Assistant screen. Off by default; it turns
 * itself off [HOURS_ON] hours after it was switched on. While off, only the decoded settings read their own codes.
 */
class RawReadSwitch(
    private val store: KeyValueStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private fun until(): Long = store.get(KEY)?.toLongOrNull() ?: 0L

    fun isOn(): Boolean = clock() < until()

    fun turnOn() = store.put(KEY, (clock() + HOURS_ON * 3_600_000L).toString())

    fun turnOff() = store.delete(KEY)

    /** "HH:mm" local time the switch turns itself off, or null when off. */
    fun onUntilText(zone: ZoneId = ZoneId.systemDefault()): String? =
        if (isOn()) DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(until()).atZone(zone)) else null

    companion object {
        const val HOURS_ON = 24L
        private const val KEY = "raw.read.until"
    }
}
