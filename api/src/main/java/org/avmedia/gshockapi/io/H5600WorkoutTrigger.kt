package org.avmedia.gshockapi.io

/**
 * When a LifeLog read is followed by a workout download. The watch flags waiting workouts in `0x28` (notes G4 step 8; the
 * flag itself is not yet seen on the watch), so a read also looks once an hour without the flag. The download skips
 * workouts already fetched, so looking costs one group header read. Pure.
 */
object WorkoutReadTrigger {
    const val CHECK_EVERY_MS = 3_600_000L

    /** Index 6 counts after the code byte, so it is frame position 7 (owner log 2026-11-23h 21:31:39: `28 06 17 00 00 FA 00 01 04 00` = 1). */
    const val FLAG_POSITION = 7

    /** `0x28` index 6 of a raw answer (code byte first); null when the answer is missing or short. */
    fun flagOf(condition: ByteArray?): Int? = condition?.getOrNull(FLAG_POSITION)?.toInt()?.and(0xFF)

    /** The watch holds workouts the app has not fetched. */
    fun waiting(flag: Int?): Boolean = flag == 1 || flag == 2

    /** [flag] = `0x28` index 6 (null when unknown), [lastCheckMs] = end of the last finished download (null = none yet). */
    fun shouldDownload(flag: Int?, lastCheckMs: Long?, nowMs: Long): Boolean =
        waiting(flag) || lastCheckMs == null || nowMs < lastCheckMs || nowMs - lastCheckMs >= CHECK_EVERY_MS
}
