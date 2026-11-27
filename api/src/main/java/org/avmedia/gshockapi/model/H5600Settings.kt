package org.avmedia.gshockapi.model

/** DW-H5600 sound / notification mode: byte[1] of packet 0x42. */
enum class SoundMode(val code: Int) {
    ALL_OFF(0x00),
    SOUND_ONLY(0x02),
    CUSTOM(0x05),
    VIBE_ONLY(0x08),
    ALL_ON(0x0A), // all five confirmed on the user's watch 2026-11-16
    ;

    companion object {
        fun fromCode(code: Int): SoundMode? = entries.firstOrNull { it.code == code }
    }
}

/**
 * DW-H5600 smart alarm (packet 0x50, one slot): `50 <hour> <minute> <flags> <window>`.
 * flags bit 0x01 = on, 0x02 = snooze; window = minutes before (15/30/45/60). Decoded 2026-11-16.
 */
data class SmartAlarm(
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val snooze: Boolean,
    val windowMin: Int,
)

/** DW-H5600 watch-face display item: bytes[2..5] of packet 0x51 (codes from the official app, 2026-11-16). */
enum class WatchFaceItem(val code: Int) {
    STEPS(0x00),
    HEART_RATE(0x0A),
    HEART_RATE_MIN_MAX(0x0B),
    SLEEP(0x0E),
    SUNRISE_SUNSET(0x12),
    MOON(0x13),
    ;

    companion object {
        fun fromCode(code: Int): WatchFaceItem? = entries.firstOrNull { it.code == code }
    }
}
