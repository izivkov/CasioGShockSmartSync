package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.session.Session
import org.avmedia.gshockGoogleSync.services.LocationProvider
import org.avmedia.gshockGoogleSync.session.SessionPhase
import org.avmedia.gshockGoogleSync.utils.AppTimeFormat
import org.avmedia.gshockapi.model.SmartAlarm
import timber.log.Timber
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone-side scheduler for what the DW-H5600 cannot do itself:
 *  - alarms ([AlarmPool]): loads the next alarms into the 4 slots + the smart-alarm slot, sets the
 *    watch's single sound mode for the alarm due next, and switches finished one-time alarms off —
 *    1 minute after every alarm time and, for alarms with snooze, again when snoozing is over;
 *  - sun data: a loc/time sync on connect and every quarter hour (weather trick: [TempSun]);
 *  - dynamic watch face ([SunFace]) at the edges of the sunset window.
 * Runs once when the watch connects (before the regular connection actions) and, while it stays
 * connected, each part only when it is due.
 */
@Singleton
class H5600ScheduleRunner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: GShockRepository,
    private val watchTimeUpdater: org.avmedia.gshockGoogleSync.ui.actions.WatchTimeUpdater,
) {
    private val mutex = Mutex()

    // ---- Background queue: screens hand work over and stay responsive ----

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)
    private val running = java.util.concurrent.atomic.AtomicInteger(0)
    private val _syncing = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** True while queued watch work is running or waiting (shown as "sending to watch…"). */
    val syncing: kotlinx.coroutines.flow.StateFlow<Boolean> = _syncing

    private fun background(what: String, block: suspend () -> Unit) {
        _syncing.value = running.incrementAndGet() > 0
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "H5600 $what failed")
            } finally {
                _syncing.value = running.decrementAndGet() > 0
            }
        }
    }

    @Volatile private var alarmsQueued = false

    private val _alarmsPending = kotlinx.coroutines.flow.MutableStateFlow(false)

    /**
     * True when the watch has not got the latest alarm list yet (the last update failed). The phone
     * list is the source of truth, so a failure is retried — never rolled back: after 10 s, 30 s,
     * 1 min, 2 min, then on every pass the scheduler makes (connect, alarm times).
     */
    val alarmsPending: kotlinx.coroutines.flow.StateFlow<Boolean> = _alarmsPending
    private val retryDelaysMs = listOf(10_000L, 30_000L, 60_000L, 120_000L)
    @Volatile private var retryAttempt = 0
    private var retryJob: kotlinx.coroutines.Job? = null

    private fun onAlarmPass(ok: Boolean) {
        _alarmsPending.value = !ok
        retryJob?.cancel()
        if (ok) { retryAttempt = 0; return }
        val wait = retryDelaysMs.getOrNull(retryAttempt++) ?: return // later: next connect / alarm boundary
        Timber.w("H5600 alarm update failed; retry ${retryAttempt} in ${wait / 1000} s")
        retryJob = scope.launch { delay(wait); requestApplyAlarms() }
    }

    /**
     * Alarms tab edits: the list is already saved on the phone; this brings the watch in line in the
     * background. Edits made while an update is still waiting are merged into it (it reads the
     * saved list once it runs), and updates run one after another.
     */
    fun requestApplyAlarms() {
        if (alarmsQueued) return
        alarmsQueued = true
        background("alarm update") {
            mutex.withLock {
                alarmsQueued = false // later edits queue a new pass; this one sees all edits up to now
                if (api.isH5600SettingsSupported()) {
                    val notes = mutableListOf<String>()
                    onAlarmPass(applyAlarmsLocked(notes, userAction = true))
                    record(notes)
                }
            }
            wake.trySend(Unit)
        }
    }

    /** Notification (bar + lock screen) and the floating card over other apps, for each snoozing alarm. */
    private fun showSnoozing(alarms: List<AlarmPool.PhoneAlarm>) {
        SnoozeNotifier.update(context, alarms)
        SnoozeOverlay.update(context, alarms) { id ->
            SnoozeNotifier.cancel(context, id)
            requestStopSnooze(id)
        }
    }

    /** Stop snooze from a screen or the notification, in the background. */
    fun requestStopSnooze(id: String) = background("stop snooze") { stopSnooze(id) }

    init {
        // Every time sync (from any screen, any moment) writes this location inside it
        // (H5600SettingsIO.writeLocationInTimeSync). Fresh each time, real or weather coordinates.
        api.setH5600HomeLocationProvider { effectiveLocation() }
    }

    /**
     * Coordinates to send in 0x24: the phone's location, or, with "Temperature instead of sunrise"
     * on and a temperature known, fake coordinates whose sunrise reads 0:TT (see [TempSun]).
     */
    private fun effectiveLocation(): Pair<Double, Double>? {
        val loc = LocationProvider.getLocation(context) ?: return null
        if (TempSun.isEnabled(context)) {
            val temp = TempSun.last(context)?.first
            if (temp != null) {
                val zone = ZoneId.systemDefault()
                TempSun.fakeCoordinates(
                    LocalDate.now(zone), loc.latitude, loc.longitude, zone, Math.round(temp).toInt(),
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
     * then loc/time sync so the watch recalculates with the fake coordinates. Returns a status text.
     */
    suspend fun refreshTempSun(force: Boolean): String? = mutex.withLock { refreshTempSunLocked(force) }

    private suspend fun refreshTempSunLocked(force: Boolean): String? {
        if (!TempSun.isEnabled(context) || !api.isH5600SettingsSupported()) return null
        if (!force && !TempSun.isStale(context)) return null
        val loc = LocationProvider.getLocation(context) ?: return "temp: no location"
        val notes = mutableListOf<String>()
        val text = watchCall("temp", notes) {
            val c = withContext(Dispatchers.IO) { TempSun.fetchTemperature(context, loc.latitude, loc.longitude) }
            syncTimeAndLocation()
            "weather %d, %.1f °C → sunrise %d:%02d".format(
                TempSun.lastCategory(context) ?: 0, c,
                TempSun.lastCategory(context) ?: 0, Math.round(c).toInt().coerceIn(0, TempSun.MAX_TEMP),
            )
        } ?: notes.firstOrNull()
        return text.also { Timber.i("H5600 $it") }
    }

    /** Hands the location to the library, which writes it inside the next time sync (TimeIO). */
    private fun rememberLocation() {
        effectiveLocation()?.let { (lat, lon) -> api.rememberH5600HomeLocation(lat, lon) }
    }

    /** Time sync (location written inside it); the watch recalculates sun data only on such a write. */
    private suspend fun syncTimeAndLocation() {
        rememberLocation()
        withTimeout(20_000) { watchTimeUpdater.updateTime() }
        lastSunResend = LocalDateTime.now()
    }

    @Volatile private var lastSunResend: LocalDateTime? = null

    /** "Send loc/time" button, temperature-trick corrections, real sunrise restored. */
    suspend fun syncLocationAndTime() = mutex.withLock {
        if (api.isH5600SettingsSupported()) syncTimeAndLocation()
    }

    /**
     * The watch sometimes drops its sun location on its own (seen 2026-11-16: dashes with no write
     * from us). With the weather option off nothing else re-sends it, so do a loc/time sync every
     * quarter hour (the weather option does the same via its 15-min fetch).
     */
    private suspend fun resendSunIfDue(): String? {
        if (TempSun.isEnabled(context) || !api.isH5600SettingsSupported()) return null
        val last = lastSunResend
        if (last != null && LocalDateTime.now().isBefore(TempSun.nextFetch(last))) return null
        return watchCall("sun re-send") { syncTimeAndLocation(); "sun data re-sent" }
    }

    /**
     * A time sync cancels a running snooze on the watch (tested 2026-11-17: snoozed 12:10 alarm, a
     * time sync at 12:10:46, no repeat at 12:15). So automatic syncs wait while an alarm with snooze
     * may still be snoozing; they catch up on the next quarter-hour tick after the window.
     */
    private fun snoozeActive(now: LocalDateTime = LocalDateTime.now()): Boolean =
        AlarmPool.load(context)?.let { AlarmPool.snoozing(it, now).isNotEmpty() } == true

    /** Called once per connection when the session is Ready. */
    suspend fun onConnected(applySchedule: Boolean) {
        if (!api.isH5600SettingsSupported()) return
        watchCall("0x39 handshake") { withTimeout(15_000) { api.h5600ConnectionHandshake() } }
        // Smart Sync's connect routine rewrites the home / world-city packets (1D/1E/1F), which makes
        // the watch drop its sun location -> dashes. One loc/time sync per connection fixes it, and
        // also counts as this quarter's sun re-send — unless an alarm may be snoozing (see above).
        if (snoozeActive()) Timber.i("H5600: loc/time sync on connect postponed (alarm may be snoozing)")
        else watchCall("loc/time sync on connect") { syncTimeAndLocation() }
        if (applySchedule) applyIfEnabled()
    }

    /**
     * Stops alarm [id]'s snooze: a time sync (which cancels the snooze on the watch), then the hold
     * ends right away (slot freed, sound mode for the next alarm, one-time alarm off).
     */
    suspend fun stopSnooze(id: String) = mutex.withLock {
        if (!api.isH5600SettingsSupported()) return@withLock
        val now = LocalDateTime.now()
        val pool = AlarmPool.load(context) ?: return@withLock
        val target = AlarmPool.snoozing(pool, now).firstOrNull { it.first.id == id } ?: return@withLock
        val notes = mutableListOf<String>()
        if (watchCall("stop snooze", notes) { syncTimeAndLocation() } == null) {
            // Not stopped: bring the button and the notification back so it can be tried again.
            record(notes)
            showSnoozing(AlarmPool.snoozing(pool, now).map { it.first })
            onPoolChanged?.invoke()
            org.avmedia.gshockGoogleSync.ui.common.AppSnackbar("Stop snooze did not reach the watch — try again")
            return@withLock
        }
        AlarmPool.save(context, AlarmPool.dismiss(pool, id, now))
        notes += "snooze stopped: " + AppTimeFormat.hm(target.first.hour, target.first.minute)
        onAlarmPass(applyAlarmsLocked(notes, userAction = true))
        record(notes)
        onPoolChanged?.invoke()
        wake.trySend(Unit)
    }

    /** Everything now: alarms, sound mode, smart alarm, sun/weather refresh if due, watch face. */
    suspend fun applyIfEnabled() = mutex.withLock {
        if (!api.isH5600SettingsSupported()) return@withLock
        val notes = mutableListOf<String>()
        onAlarmPass(applyAlarmsLocked(notes))
        applyPeriodicLocked(notes)
        applySunFaceLocked(notes)
        record(notes)
    }

    /** Called after each alarm pass (one-time alarm switched off, snooze window started or ended). */
    @Volatile var onPoolChanged: (() -> Unit)? = null

    /** Only the alarm part (Alarms tab edits). Also wakes the boundary loop to re-plan. */
    suspend fun applyAlarms() = mutex.withLock {
        if (!api.isH5600SettingsSupported()) return@withLock
        val notes = mutableListOf<String>()
        onAlarmPass(applyAlarmsLocked(notes, userAction = true))
        record(notes)
        wake.trySend(Unit)
    }

    /** Wakes the boundary loop early (alarms changed), so it re-plans its next wake-up. */
    private val wake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    /** Only the dynamic watch face (Custom-tab switch). */
    suspend fun applySunFace() = mutex.withLock {
        if (!api.isH5600SettingsSupported()) return@withLock
        val notes = mutableListOf<String>()
        applySunFaceLocked(notes)
        if (notes.isNotEmpty()) record(notes)
    }

    private fun record(notes: List<String>) {
        val now = LocalDateTime.now()
        val stamp = now.format(DateTimeFormatter.ofPattern("EEE dd MMM ")) + AppTimeFormat.format(now.toLocalTime())
        Timber.i("H5600 schedule: $notes")
        H5600Schedule.recordApplied(context, "$stamp: " + (notes.joinToString("; ").ifEmpty { "no change needed" }))
    }

    /** Alarm slots, smart-alarm slot and sound mode for the alarm due next. */
    /**
     * [userAction]: an edit on the Alarms tab or Stop snooze — written even while an alarm may be
     * snoozing. Automatic passes leave the alarm and smart-alarm slots alone during a snooze window,
     * because rewriting them (like a time sync) seems to cancel the snooze on the watch.
     */
    private suspend fun applyAlarmsLocked(notes: MutableList<String>, userAction: Boolean = false): Boolean {
        val loaded = AlarmPool.load(context) ?: return true // null until the Alarms tab has been opened once
        val now = LocalDateTime.now()
        // One-time alarms that have rung (and finished snoozing) switch themselves off.
        val pool = AlarmPool.expireOnce(loaded, now)
        if (pool != loaded) {
            AlarmPool.save(context, pool)
            notes += "one-time alarm off: " + pool.filterIndexed { i, a -> !a.enabled && loaded[i].enabled }
                .joinToString(" ") { AppTimeFormat.hm(it.hour, it.minute) }
        }
        val ok = watchCall("alarm schedule", notes) {
            withTimeout(45_000) {
                val watch = api.getAlarms() // the library shares/retries concurrent reads
                val plan = AlarmPool.plan(pool, AlarmPool.loadedIds(context), now)
                val wanted = AlarmPool.toWatchAlarms(plan, pool, watch)
                val holdWrites = !userAction && AlarmPool.snoozing(pool, now).isNotEmpty()
                if (!AlarmPool.sameOnWatch(wanted, watch)) {
                    Timber.i("H5600 alarms differ:\n watch =${watch.map { "${it.hour}:${it.minute} on=${it.enabled} sz=${it.snooze} 1x=${it.oneTime} ch=${it.hasHourlyChime}" }}\n wanted=${wanted.map { "${it.hour}:${it.minute} on=${it.enabled} sz=${it.snooze} 1x=${it.oneTime} ch=${it.hasHourlyChime}" }}")
                }
                if (holdWrites && !AlarmPool.sameOnWatch(wanted, watch)) {
                    notes += "alarm slots: change waits until snoozing is over"
                    return@withTimeout
                }
                if (!AlarmPool.sameOnWatch(wanted, watch)) {
                    api.setAlarms(ArrayList(wanted))
                    notes += "watch alarms: " + wanted.take(AlarmPool.SLOTS)
                        .joinToString(" ") { if (it.enabled) AppTimeFormat.hm(it.hour, it.minute) else "–" }
                }
                AlarmPool.saveLoadedIds(context, plan)

                // Smart-alarm slot (0x50): the next smart alarm within 24 h, or off.
                val smartId = AlarmPool.planSmart(pool, AlarmPool.loadedSmartId(context), now)
                val smart = pool.firstOrNull { it.id == smartId }
                val current = api.getH5600SmartAlarm()
                if (current != null) {
                    val wantedSmart = smart?.let { SmartAlarm(it.hour, it.minute, true, it.snooze, it.smartWindowMin) }
                        ?: current.copy(enabled = false)
                    if (wantedSmart != current && !holdWrites) {
                        api.setH5600SmartAlarm(wantedSmart)
                        notes += "smart alarm: " + (smart?.let { AppTimeFormat.hm(it.hour, it.minute) + " (−${it.smartWindowMin} min)" } ?: "off")
                    }
                    AlarmPool.saveLoadedSmartId(context, smartId)
                }

                AlarmPool.desiredSoundMode(pool, now)?.let { desired ->
                    val before = api.setSoundMode(desired) // one read; no write if already equal
                    if (before != desired) notes += "sound ${before ?: "?"} → $desired"
                }
            }
        }
        showSnoozing(AlarmPool.snoozing(pool, now).map { it.first })
        onPoolChanged?.invoke() // Alarms tab: on-watch tags, snooze buttons, one-time alarms switched off
        return ok != null
    }

    /** Quarter-hourly: weather fetch (option on) or plain sun-data re-send (option off). */
    private suspend fun applyPeriodicLocked(notes: MutableList<String>) {
        if (snoozeActive()) return // a time sync would cancel the snooze; catch up after the window
        refreshTempSunLocked(force = false)?.let { notes += it }
        resendSunIfDue()?.let { notes += it }
    }

    /** Dynamic sunset-step toggle: Sun data from 4 h before sunset to 10 min after, Steps otherwise. */
    private suspend fun applySunFaceLocked(notes: MutableList<String>) {
        if (!SunFace.isEnabled(context)) return
        watchCall("sun face", notes) {
            val loc = LocationProvider.getLocation(context) ?: error("no location")
            val desired = SunFace.desiredFace(LocalDateTime.now(), loc.latitude, loc.longitude, ZoneId.systemDefault())
            val before = withTimeout(15_000) { api.setWatchFaceItem(desired) } // no write if already equal
            if (before != desired) notes += "face ${before ?: "?"} → $desired"
        }
    }

    /**
     * Runs a watch call, logging failures (and noting them in [notes]) instead of throwing; real
     * cancellation (session closed, screen left) is rethrown so nothing runs on a dead connection.
     */
    private suspend fun <T> watchCall(what: String, notes: MutableList<String>? = null, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            Timber.e(e, "H5600 $what timed out"); notes?.add("$what failed (timeout)"); null
        } catch (e: Exception) {
            Timber.e(e, "H5600 $what failed"); notes?.add("$what failed (${e.message})"); null
        }

    private fun minuteOfDay(m: Int) = ((m % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    /** Next time [minute] (minute of day) occurs strictly after [now]. */
    private fun nextOccurrence(minute: Int, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atStartOfDay().plusMinutes(minuteOfDay(minute).toLong())
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** Next alarm-related boundary: 1 and HOLD minutes after every alarm, and before smart windows. */
    private fun nextAlarmBoundary(now: LocalDateTime): LocalDateTime? =
        AlarmPool.load(context)?.filter { it.enabled }.orEmpty()
            .flatMap { a ->
                listOf(a.minuteOfDay + 1, a.minuteOfDay + AlarmPool.holdMin(a).toInt()) +
                    // smart alarm: also switch the sound mode just before its window opens
                    (if (a.isSmart) listOf(a.minuteOfDay - a.smartWindowMin - 1) else emptyList())
            }
            .map { nextOccurrence(it, now) }
            .minOrNull()

    private fun nextFaceSwitch(now: LocalDateTime): LocalDateTime? =
        if (!SunFace.isEnabled(context)) null
        else LocationProvider.getLocation(context)?.let {
            SunFace.nextSwitch(now, it.latitude, it.longitude, ZoneId.systemDefault())
        }

    /**
     * For a Ready session: while connected, run each part only when it is due — alarms at alarm
     * boundaries, the face at sunset-window edges, the sun/weather refresh every quarter hour —
     * instead of re-reading everything every 15 minutes.
     */
    suspend fun applyAtBoundariesWhileConnected(session: Session) = coroutineScope {
        val ready = session.state.first { it.phase is SessionPhase.Ready || it.phase is SessionPhase.Closed }
        if (ready.phase !is SessionPhase.Ready) return@coroutineScope
        val loop = launch {
            while (true) {
                val now = LocalDateTime.now()
                // Re-evaluated every round (settings may change); idle re-check every 30 min.
                val alarmAt = nextAlarmBoundary(now)
                val faceAt = nextFaceSwitch(now)
                val tickAt = TempSun.nextFetch(now)
                val next = listOfNotNull(alarmAt, faceAt, tickAt).minOrNull() ?: now.plusMinutes(30)
                val sleepMs = Duration.between(now, next).toMillis().coerceAtMost(30 * 60_000L) + 5_000
                // Woken early when alarms change: re-plan without running anything that is not due.
                kotlinx.coroutines.withTimeoutOrNull(sleepMs) { wake.receive() }
                val woke = LocalDateTime.now()
                mutex.withLock {
                    if (!api.isH5600SettingsSupported()) return@withLock
                    val notes = mutableListOf<String>()
                    if (alarmAt != null && !woke.isBefore(alarmAt)) onAlarmPass(applyAlarmsLocked(notes))
                    applyPeriodicLocked(notes) // each part checks its own due time
                    if (faceAt != null && !woke.isBefore(faceAt)) applySunFaceLocked(notes)
                    if (notes.isNotEmpty()) record(notes)
                }
            }
        }
        session.state.first { it.phase is SessionPhase.Closed }
        loop.cancel()
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
