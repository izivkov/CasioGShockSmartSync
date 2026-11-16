package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.session.Session
import org.avmedia.gshockGoogleSync.session.SessionPhase
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the per-alarm settings the DW-H5600 cannot store itself ([AlarmDayRules]):
 *  1. day-of-week rules: switch each ruled alarm on/off for its next ring;
 *  2. vibe-only vs sound: set the watch's single sound mode for the alarm that is due
 *     (VIBE_ONLY or CUSTOM), only if at least one alarm is marked vibe-only.
 * Runs once when the watch connects (before the regular connection actions) and, while the watch
 * stays connected, again 1 and [AlarmDayRules.SNOOZE_HOLD_MIN] minutes after every alarm time.
 */
@Singleton
class H5600ScheduleRunner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: GShockRepository,
    private val watchTimeUpdater: org.avmedia.gshockGoogleSync.ui.actions.WatchTimeUpdater,
) {
    private val mutex = Mutex()

    init {
        // Any time sync (from any screen, any moment) is followed by this location: see
        // H5600SettingsIO.resendHomeLocation. Fresh each time, real or weather coordinates.
        api.setH5600HomeLocationProvider { effectiveLocation() }
    }

    /**
     * Coordinates to send in 0x24: the phone's location, or, with "Temperature instead of sunrise"
     * on and a temperature known, fake coordinates whose sunrise reads 0:TT (see [TempSun]).
     */
    private fun effectiveLocation(): Pair<Double, Double>? {
        val loc = org.avmedia.gshockGoogleSync.services.LocationProvider.getLocation(context) ?: return null
        if (TempSun.isEnabled(context)) {
            val temp = TempSun.last(context)?.first
            if (temp != null) {
                val zone = java.time.ZoneId.systemDefault()
                TempSun.fakeCoordinates(
                    java.time.LocalDate.now(zone), loc.latitude, loc.longitude, zone, Math.round(temp).toInt(),
                    TempSun.riseCorrection(context), TempSun.setCorrection(context),
                    riseHour = TempSun.lastCategory(context) ?: 0, // W:TT, W = 1 sunny / 2 cloudy / 3 rain
                )?.also {
                    context.getSharedPreferences("h5600_prefs", Context.MODE_PRIVATE).edit()
                        .putString("last_sent_top", "%d:%02d".format(TempSun.lastCategory(context) ?: 0,
                            Math.round(temp).toInt().coerceIn(0, TempSun.MAX_TEMP)))
                        .putLong("last_sent_at", System.currentTimeMillis()).apply()
                }
                    ?.let { return it }
            }
        }
        return loc.latitude to loc.longitude
    }

    /**
     * Temperature trick: fetch Open-Meteo when due (every [TempSun.REFRESH_MIN] min, or [force]),
     * send the fake coordinates and time-sync so the watch recalculates. Returns a status text.
     */
    suspend fun refreshTempSun(force: Boolean): String? {
        if (!TempSun.isEnabled(context) || !api.isH5600SettingsSupported()) return null
        if (!force && !TempSun.isStale(context)) return null
        val loc = org.avmedia.gshockGoogleSync.services.LocationProvider.getLocation(context)
            ?: return "temp: no location"
        return runCatching {
            val c = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                TempSun.fetchTemperature(context, loc.latitude, loc.longitude)
            }
            sendLocation(force = true)
            watchTimeUpdater.updateTime() // the watch only recalculates sun data on a time/city write
            "weather %d, %.1f °C → sunrise %d:%02d".format(
                TempSun.lastCategory(context) ?: 0, c,
                TempSun.lastCategory(context) ?: 0, Math.round(c).toInt().coerceIn(0, TempSun.MAX_TEMP),
            )
        }.getOrElse { "temp failed (${it.message})" }.also { Timber.i("H5600 $it") }
    }

    /**
     * Sends the phone's (coarse) location for sunrise/sunset. Skipped when it moved < ~1 km since
     * the last send less than 24 h ago, unless [force]. Returns a short status text.
     */
    fun sendLocation(force: Boolean): String {
        if (!api.isH5600SettingsSupported()) return "not supported"
        val (lat, lon) = effectiveLocation()
            ?: return "no location available (location off or permission missing)".also { Timber.w("H5600 location: $it") }
        val p = context.getSharedPreferences("h5600_prefs", Context.MODE_PRIVATE)
        val lastLat = p.getFloat("loc_lat", 999f).toDouble()
        val lastLon = p.getFloat("loc_lon", 999f).toDouble()
        val lastAt = p.getLong("loc_at", 0L)
        val moved = Math.abs(lat - lastLat) > 0.01 || Math.abs(lon - lastLon) > 0.01
        val stale = System.currentTimeMillis() - lastAt > 24 * 3600_000L
        // Always hand the location to the library: it re-sends it after every time/city sync,
        // which otherwise wipes it on the watch.
        api.rememberH5600HomeLocation(lat, lon)
        if (!force && !moved && !stale) return "location unchanged (re-sent after time sync)"
        return runCatching {
            api.setH5600HomeLocation(lat, lon)
            p.edit().putFloat("loc_lat", lat.toFloat()).putFloat("loc_lon", lon.toFloat())
                .putLong("loc_at", System.currentTimeMillis()).apply()
            "sent %.3f, %.3f".format(lat, lon)
        }.getOrElse { "failed: ${it.message}" }.also { Timber.i("H5600 location: $it") }
    }

    @Volatile private var lastSunResend: LocalDateTime? = null

    /**
     * The watch sometimes drops its sun location on its own (seen 2026-11-16: dashes with no write
     * from us). With the weather option off nothing else re-sends it, so do a loc/time sync every
     * quarter hour (the weather option does the same via its 15-min fetch).
     */
    private suspend fun resendSunIfDue(): String? {
        if (TempSun.isEnabled(context) || !api.isH5600SettingsSupported()) return null
        val now = LocalDateTime.now()
        val last = lastSunResend
        if (last != null && now.isBefore(TempSun.nextFetch(last))) return null
        lastSunResend = now
        return runCatching {
            sendLocation(force = true)
            watchTimeUpdater.updateTime() // the watch recalculates sun data only on a time/city write
            "sun data re-sent"
        }.getOrElse { "sun re-send failed (${it.message})" }
    }

    /** Called once per connection when the session is Ready. */
    suspend fun onConnected(applySchedule: Boolean) {
        if (!api.isH5600SettingsSupported()) return
        runCatching { withTimeout(15_000) { api.h5600ConnectionHandshake() } }
            .onSuccess { Timber.i("H5600 0x39 handshake done") }
            .onFailure { Timber.e(it, "H5600 0x39 handshake failed") }
        sendLocation(force = false)
        // Smart Sync's connect routine rewrites the home / world-city packets (1D/1E/1F), which makes
        // the watch drop its sun location -> dashes. Do a full loc/time sync on every connection
        // (same as the "Send loc/time" button): time sync with Fine Adjustment, location re-sent after.
        runCatching { withTimeout(20_000) { watchTimeUpdater.updateTime() } }
            .onSuccess { Timber.i("H5600: loc/time synced on connect") }
            .onFailure { Timber.e(it, "H5600: loc/time sync on connect failed") }
        if (applySchedule) applyIfEnabled()
    }

    suspend fun applyIfEnabled() = mutex.withLock {
        if (!api.isH5600SettingsSupported()) return@withLock
        val pool = AlarmPool.load(context) // null until the Alarms tab has been opened once

        val now = LocalDateTime.now()
        val stamp = now.format(DateTimeFormatter.ofPattern("EEE dd MMM ")) + org.avmedia.gshockGoogleSync.utils.AppTimeFormat.format(now.toLocalTime())
        val notes = mutableListOf<String>()
        if (pool != null) runCatching {
            withTimeout(30_000) {
                // The Alarms tab may be reading alarms at the same moment; retry once on a collision.
                val watch = runCatching { withTimeout(8_000) { api.getAlarms() } }
                    .getOrElse { delay(1_500); withTimeout(8_000) { api.getAlarms() } }

                val plan = AlarmPool.plan(pool, AlarmPool.loadedIds(context), now)
                val wanted = AlarmPool.toWatchAlarms(plan, pool, watch)
                if (wanted != watch) {
                    api.setAlarms(ArrayList(wanted))
                    notes += "watch alarms: " + wanted.take(AlarmPool.SLOTS)
                        .joinToString(" ") { if (it.enabled) org.avmedia.gshockGoogleSync.utils.AppTimeFormat.hm(it.hour, it.minute) else "–" }
                }
                AlarmPool.saveLoadedIds(context, plan)

                // Smart-alarm slot (0x50): the next smart alarm within 24 h, or off.
                val smartId = AlarmPool.planSmart(pool, AlarmPool.loadedSmartId(context), now)
                val smart = pool.firstOrNull { it.id == smartId }
                val current = runCatching { withTimeout(8_000) { api.getH5600SmartAlarm() } }.getOrNull()
                if (current != null) {
                    val wantedSmart = smart?.let {
                        org.avmedia.gshockapi.model.SmartAlarm(it.hour, it.minute, true, it.snooze, it.smartWindowMin)
                    } ?: current.copy(enabled = false)
                    if (wantedSmart != current) {
                        api.setH5600SmartAlarm(wantedSmart)
                        notes += "smart alarm: " + (smart?.let {
                            org.avmedia.gshockGoogleSync.utils.AppTimeFormat.hm(it.hour, it.minute) + " (−${it.smartWindowMin} min)"
                        } ?: "off")
                    }
                    AlarmPool.saveLoadedSmartId(context, smartId)
                }

                AlarmPool.desiredSoundMode(pool, now)?.let { desired ->
                    val before = api.getSoundMode()
                    api.setSoundMode(desired) // no write if already equal
                    if (before != desired) notes += "sound ${before ?: "?"} → $desired"
                }
            }
        }.onFailure { Timber.e(it, "H5600 alarm schedule failed"); notes += "failed (${it.message})" }

        refreshTempSun(force = false)?.let { notes += it }
        resendSunIfDue()?.let { notes += it }

        // Dynamic sunset-step toggle: Sun data from 4 h before sunset to 10 min after, Steps otherwise.
        if (SunFace.isEnabled(context)) runCatching {
            val loc = org.avmedia.gshockGoogleSync.services.LocationProvider.getLocation(context)
                ?: error("no location")
            val desired = SunFace.desiredFace(now, loc.latitude, loc.longitude, java.time.ZoneId.systemDefault())
            withTimeout(15_000) {
                val before = api.getWatchFaceItem()
                if (before != desired) {
                    api.setWatchFaceItem(desired)
                    notes += "face ${before ?: "?"} → $desired"
                }
            }
        }.onFailure { Timber.e(it, "H5600 sun face failed"); notes += "face failed (${it.message})" }

        Timber.i("H5600 schedule: $notes")
        H5600Schedule.recordApplied(context, "$stamp: " + (notes.joinToString("; ").ifEmpty { "no change needed" }))
    }

    /**
     * Next time something may need to change: 1 and HOLD minutes after every alarm time (slots /
     * sound), and the start / end of the sunset window (watch face).
     */
    private fun nextWakeUp(now: LocalDateTime): LocalDateTime? {
        val pool = AlarmPool.load(context)?.filter { it.enabled }.orEmpty()
        val alarmTimes = pool.flatMap { a ->
            listOf(a.minuteOfDay + 1, a.minuteOfDay + AlarmDayRules.SNOOZE_HOLD_MIN.toInt()) +
                // smart alarm: also switch the sound mode just before its window opens
                (if (a.isSmart) listOf((a.minuteOfDay - a.smartWindowMin - 1 + 24 * 60) % (24 * 60)) else emptyList())
        }
            .map { m ->
                val t = m % (24 * 60)
                H5600Schedule.nextBoundary(H5600Schedule.Config(true, t, t), now)
            }
        val sun = if (SunFace.isEnabled(context)) {
            org.avmedia.gshockGoogleSync.services.LocationProvider.getLocation(context)?.let {
                SunFace.nextSwitch(now, it.latitude, it.longitude, java.time.ZoneId.systemDefault())
            }
        } else null
        // Quarter-hourly: weather fetch (option on) or plain sun-data re-send (option off).
        val temp = TempSun.nextFetch(now)
        return (alarmTimes + listOfNotNull(sun) + temp).minOrNull()
    }

    /** For a Ready session: re-apply at every relevant time until the session closes. */
    suspend fun applyAtBoundariesWhileConnected(session: Session) = coroutineScope {
        val ready = session.state.first { it.phase is SessionPhase.Ready || it.phase is SessionPhase.Closed }
        if (ready.phase !is SessionPhase.Ready) return@coroutineScope
        val loop = launch {
            while (true) {
                val now = LocalDateTime.now()
                // Re-evaluated every round (settings may change); idle re-check every 30 min.
                val next = nextWakeUp(now) ?: now.plusMinutes(30)
                delay(Duration.between(now, next).toMillis().coerceAtMost(30 * 60_000L) + 5_000)
                applyIfEnabled()
            }
        }
        session.state.first { it.phase is SessionPhase.Closed }
        loop.cancel()
    }
}
