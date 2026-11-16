package org.avmedia.gshockGoogleSync.h5600

import org.avmedia.gshockapi.io.H5600LifeLog
import org.avmedia.gshockapi.model.StepCounterData
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Maps a DW-H5600 LifeLog result onto the Health tab's StepCounterData, with a short-lived cache. */
object H5600Steps {
    private const val CACHE_MS = 15 * 60_000L

    @Volatile
    private var cache: Pair<Long, StepCounterData>? = null

    /** When the cached data was synced from the watch (epoch ms), or null. */
    fun syncedAtMillis(): Long? = cache?.first

    fun cached(now: Long = System.currentTimeMillis()): StepCounterData? =
        cache?.takeIf { now - it.first < CACHE_MS }?.second

    /** Forces the next fetch to run a fresh LifeLog sync (pull-to-refresh). */
    fun invalidate() {
        cache = null
    }

    fun store(data: StepCounterData, now: Long = System.currentTimeMillis()) {
        cache = now to data
    }

    private fun toLocal(utc: LocalDateTime, zone: ZoneId): LocalDateTime =
        utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()

    /**
     * Pure. Today's total = the LifeLog header steps (matched the official app in capture -4).
     * hourlySteps = today's 5-minute slots summed per local hour, midnight up to the header hour.
     */
    fun toStepCounterData(r: H5600LifeLog.Result, zone: ZoneId): StepCounterData {
        val headerLocal = toLocal(r.headerTimeUtc, zone)
        val today = headerLocal.toLocalDate()
        val perHour = IntArray(24)
        r.slots.forEach { slot ->
            val t = toLocal(slot.timeUtc, zone)
            if (t.toLocalDate() == today) perHour[t.hour] += slot.steps
        }
        val hourly = (0..headerLocal.hour).map { perHour[it] }
        timber.log.Timber.i("H5600 steps: header ${r.headerSteps} at $headerLocal, ${r.slots.size} slots, hourly=$hourly")
        return StepCounterData(
            timestamp = headerLocal,
            dayOfWeek = headerLocal.dayOfWeek.value,
            month = headerLocal.monthValue,
            dayOfMonth = headerLocal.dayOfMonth,
            hourlySteps = hourly,
            dailyHistory = emptyList(), // daily-summary frame (0x63) not decoded yet
            currentDaySteps = r.headerSteps,
            hourlyByHour = hourly,
        )
    }
}
