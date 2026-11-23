package org.avmedia.gshockapi.io

/**
 * When a LifeLog read is followed by a workout download. The watch flags waiting workouts in `0x28` (notes G4 step 8; the
 * flag itself is not yet seen on the watch), so a read also looks once an hour without the flag. The download skips
 * workouts already fetched, so looking costs one group header read. Pure.
 */
object WorkoutReadTrigger {
    const val CHECK_EVERY_MS = 3_600_000L

    /** [flag] = `0x28` index 6 (null when unknown), [lastCheckMs] = end of the last finished download (null = none yet). */
    fun shouldDownload(flag: Int?, lastCheckMs: Long?, nowMs: Long): Boolean =
        lastCheckMs == null || nowMs < lastCheckMs || nowMs - lastCheckMs >= CHECK_EVERY_MS
}
