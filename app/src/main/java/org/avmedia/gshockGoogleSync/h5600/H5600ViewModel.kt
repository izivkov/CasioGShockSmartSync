package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockapi.model.Alarm
import org.avmedia.gshockapi.model.SoundMode
import org.avmedia.gshockapi.model.WatchFaceItem
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class H5600ViewModel @Inject constructor(
    private val api: GShockRepository,
    private val runner: H5600ScheduleRunner,
    private val watchTimeUpdater: org.avmedia.gshockGoogleSync.ui.actions.WatchTimeUpdater,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val supported: Boolean = false,
        val busy: Boolean = false,
        val soundMode: SoundMode? = null,
        val watchFace: WatchFaceItem? = null,
        val watchFaceCode: Int? = null,
        val soundHex: String = "",
        val faceNumber: Int? = null, // 1 = simple (weekday), 2 = customisable
        val hourlyChime: Boolean? = null,
        val dynamicFace: Boolean = false,
        val realSun: String = "",
        val realWeather: String = "",
        val watchShould: String = "",
        val tempSun: Boolean = false,
        val tempText: String = "",
        val riseCorr: Int = 0,
        val setCorr: Int = 0,
        val sunsetText: String = "",
        val schedule: H5600Schedule.Config = H5600Schedule.Config(),
        val lastApplied: String? = null,
        val alarms: List<Alarm> = emptyList(),
        val alarmRules: Map<Int, Set<java.time.DayOfWeek>> = emptyMap(),
        val vibeOnly: Set<Int> = emptySet(),
    )

    // Start from the last values read from the watch; null (spinner) only if never read.
    private val _state = MutableStateFlow(
        UiState(
            soundMode = org.avmedia.gshockGoogleSync.utils.LastKnown.int(context, "sound_mode")?.let(SoundMode::fromCode),
            soundHex = org.avmedia.gshockGoogleSync.utils.LastKnown.string(context, "sound_hex") ?: "",
            watchFaceCode = org.avmedia.gshockGoogleSync.utils.LastKnown.int(context, "watch_face"),
            faceNumber = org.avmedia.gshockGoogleSync.utils.LastKnown.int(context, "face_number"),
            hourlyChime = org.avmedia.gshockGoogleSync.utils.LastKnown.bool(context, "hourly_chime"),
            watchFace = org.avmedia.gshockGoogleSync.utils.LastKnown.int(context, "watch_face")?.let(WatchFaceItem::fromCode),
        ),
    )
    val state: StateFlow<UiState> = _state

    init {
        refresh()
    }

    private fun reloadPrefs() = _state.update {
        val loc = org.avmedia.gshockGoogleSync.services.LocationProvider.getLocation(context)
        val zone = java.time.ZoneId.systemDefault()
        val set = loc?.let { l -> SunFace.sunset(java.time.LocalDate.now(), l.latitude, l.longitude, zone) }
        val today = java.time.LocalDate.now(zone)
        val realRs = loc?.let { l -> TempSun.riseSetUtcMin(today, l.latitude, l.longitude) }
        val fmt = { m: Double -> org.avmedia.gshockGoogleSync.utils.AppTimeFormat.format(TempSun.toLocal(today, m, zone).toLocalTime()) }
        val p = context.getSharedPreferences("h5600_prefs", Context.MODE_PRIVATE)
        it.copy(
            realSun = realRs?.let { (r, st) -> "sunrise ${fmt(r)} · sunset ${fmt(st)}" } ?: "no location yet",
            realWeather = TempSun.last(context)?.let { (c, at) ->
                "%.1f °C, %s (%s) at %s".format(c, TempSun.categoryName(TempSun.lastCategory(context)),
                    TempSun.lastRaw(context) ?: "-",
                    org.avmedia.gshockGoogleSync.utils.AppTimeFormat.format(
                        java.time.LocalTime.ofInstant(java.time.Instant.ofEpochMilli(at), zone)))
            } ?: "not fetched yet",
            watchShould = realRs?.let { (r, st) ->
                val sunset = fmt(st)
                if (TempSun.isEnabled(context)) {
                    val top = TempSun.last(context)?.let { (c, _) ->
                        "%d:%02d".format(TempSun.lastCategory(context) ?: 0, Math.round(c).toInt().coerceIn(0, TempSun.MAX_TEMP))
                    } ?: "?"
                    val sent = p.getString("last_sent_top", null)?.let { t ->
                        " (last sent $t at " + org.avmedia.gshockGoogleSync.utils.AppTimeFormat.format(
                            java.time.LocalTime.ofInstant(java.time.Instant.ofEpochMilli(p.getLong("last_sent_at", 0)), zone)) + ")"
                    } ?: ""
                    "$top / $sunset$sent"
                } else "${fmt(r)} / $sunset"
            } ?: "",
            tempSun = TempSun.isEnabled(context),
            riseCorr = TempSun.riseCorrection(context),
            setCorr = TempSun.setCorrection(context),
            tempText = TempSun.last(context)?.let { (c, at) ->
                "last " + (when (TempSun.lastCategory(context)) { 1 -> "sunny"; 2 -> "cloudy"; 3 -> "rain"; 4 -> "extreme"; else -> "?" }) +
                    ", %.1f °C at %s (Open-Meteo)".format(c,
                    org.avmedia.gshockGoogleSync.utils.AppTimeFormat.format(java.time.LocalTime.ofInstant(java.time.Instant.ofEpochMilli(at), zone)))
            } ?: "no temperature yet",
            dynamicFace = SunFace.isEnabled(context),
            sunsetText = set?.let { t ->
                "sunset %s → Sun data %s–%s".format(
                    org.avmedia.gshockGoogleSync.utils.AppTimeFormat.format(t.toLocalTime()),
                    org.avmedia.gshockGoogleSync.utils.AppTimeFormat.format(t.minusHours(SunFace.LEAD_HOURS).toLocalTime()),
                    org.avmedia.gshockGoogleSync.utils.AppTimeFormat.format(t.plusMinutes(SunFace.TRAIL_MIN).toLocalTime()),
                )
            } ?: "no location yet",
            schedule = H5600Schedule.load(context),
            lastApplied = H5600Schedule.lastApplied(context),
            alarmRules = AlarmDayRules.load(context),
            vibeOnly = AlarmDayRules.loadVibeOnly(context),
        )
    }

    fun refresh() {
        val supported = api.isH5600SettingsSupported()
        _state.update { it.copy(supported = supported) }
        reloadPrefs()
        if (!supported) return
        run("read") {
            // Each read is bounded: the watch sometimes answers "FF 81 xx" (rejected) or not at all
            // when several requests arrive at once. A failed read keeps the last known value.
            suspend fun <T> read(block: suspend () -> T): T? =
                runCatching { kotlinx.coroutines.withTimeoutOrNull(8_000) { block() } }.getOrNull()
            val hex = read { api.getH5600SoundPacketHex() }
            val mode = if (hex != null) read { api.getSoundMode() } else null
            val code = read { api.getWatchFaceCode() }
            val faceNo = read { api.getH5600WatchFaceNumber() }
            val chime = read { api.getHourlyChime() }
            Timber.i("H5600 re-read: 0x42=$hex mode=$mode face=$code")
            _state.update {
                it.copy(
                    soundMode = mode ?: it.soundMode,
                    soundHex = hex ?: it.soundHex,
                    watchFaceCode = code ?: it.watchFaceCode,
                    watchFace = (code ?: it.watchFaceCode)?.let(WatchFaceItem::fromCode),
                    faceNumber = faceNo ?: it.faceNumber,
                    hourlyChime = chime ?: it.hourlyChime,
                )
            }
            mode?.let { m -> org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "sound_mode", m.code) }
            hex?.let { h -> org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "sound_hex", h) }
            code?.let { c -> org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "watch_face", c) }
            faceNo?.let { f -> org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "face_number", f) }
            chime?.let { c -> org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "hourly_chime", c) }
        }
    }

    fun setSoundMode(mode: SoundMode) {
        val previous = _state.value.soundMode
        _state.update { it.copy(soundMode = mode) } // move the UI immediately
        run("set sound mode", onError = { _state.update { it.copy(soundMode = previous) } }) {
            api.setSoundMode(mode)
            _state.update { it.copy(soundMode = api.getSoundMode()) } // confirm from the watch
        }
    }

    fun setHourlyChime(on: Boolean) {
        val previous = _state.value.hourlyChime
        _state.update { it.copy(hourlyChime = on) }
        run("hourly time signal", onError = { _state.update { it.copy(hourlyChime = previous) } }) {
            api.setHourlyChime(on)
            val v = api.getHourlyChime()
            _state.update { it.copy(hourlyChime = v) }
            v?.let { org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "hourly_chime", it) }
        }
    }

    fun setFaceNumber(face: Int) {
        val previous = _state.value.faceNumber
        _state.update { it.copy(faceNumber = face) }
        run("switch watch face", onError = { _state.update { it.copy(faceNumber = previous) } }) {
            api.setH5600WatchFaceNumber(face)
            val f = api.getH5600WatchFaceNumber()
            _state.update { it.copy(faceNumber = f) }
            f?.let { org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "face_number", it) }
        }
    }

    fun setWatchFace(item: WatchFaceItem) {
        val previous = _state.value.watchFace
        _state.update { it.copy(watchFace = item) }
        run("set watch face", onError = { _state.update { it.copy(watchFace = previous) } }) {
            api.setWatchFaceItem(item)
            val code = api.getWatchFaceCode()
            _state.update { it.copy(watchFaceCode = code, watchFace = code?.let(WatchFaceItem::fromCode)) }
        }
    }

    fun updateSchedule(cfg: H5600Schedule.Config) {
        H5600Schedule.save(context, cfg)
        _state.update { it.copy(schedule = cfg) }
        applyNow()
    }

    /** Applies the schedule + alarm rules now, then re-reads the watch state. */
    fun applyNow() = run("apply schedule") {
        runner.applyIfEnabled()
        reloadPrefs()
        val mode = api.getSoundMode()
        val alarms = api.getAlarms()
        val code = api.getWatchFaceCode() // the dynamic toggle may have changed the face
        _state.update {
            it.copy(soundMode = mode, alarms = alarms, watchFaceCode = code, watchFace = code?.let(WatchFaceItem::fromCode))
        }
    }

    /** On: apply now (face follows the sunset window). Off: just re-read; the face stays as it is. */
    /** On: fetch the temperature now and send the fake coordinates. Off: send the real location again. */
    fun setTempSun(on: Boolean) {
        TempSun.setEnabled(context, on)
        reloadPrefs()
        run(if (on) "temperature sunrise" else "real sunrise") {
            val msg = if (on) runner.refreshTempSun(force = true)
            else { runner.sendLocation(force = true); watchTimeUpdater.updateTime(); "real sunrise restored" }
            reloadPrefs()
            msg?.let { AppSnackbar(it) }
        }
    }

    /** Fine-tune the watch-formula corrections, then re-send the coordinates + time sync. */
    fun adjustTempSun(riseDelta: Int, setDelta: Int) {
        TempSun.saveCorrections(context, TempSun.riseCorrection(context) + riseDelta, TempSun.setCorrection(context) + setDelta)
        reloadPrefs()
        if (TempSun.isEnabled(context)) run("re-send temperature sunrise") {
            runner.sendLocation(force = true)
            watchTimeUpdater.updateTime()
        }
    }

    /** Fetch the weather for display only (nothing is written to the watch). */
    fun refreshWeatherOnly() = run("refresh weather") {
        val loc = org.avmedia.gshockGoogleSync.services.LocationProvider.getLocation(context) ?: error("no location")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            TempSun.fetchTemperature(context, loc.latitude, loc.longitude)
        }
        reloadPrefs()
    }

    fun setDynamicFace(on: Boolean) {
        SunFace.setEnabled(context, on)
        reloadPrefs()
        applyNow()
    }

    /**
     * The watch only recalculates sunrise/sunset when the time / cities are written, so a bare
     * location packet is not enough: remember the location, then run a full time sync, which
     * re-sends the location right after its city writes (see GShockAPI.setTime).
     */
    fun sendLocation() = run("send location") {
        val result = runner.sendLocation(force = true)
        // Same time sync as the Time tab: applies Fine Adjustment and the time-zone option.
        watchTimeUpdater.updateTime()
        AppSnackbar("Sunrise/sunset: $result, time synced")
    }

    private fun run(what: String, onError: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: Exception) {
                Timber.e(e, "H5600 $what failed")
                onError()
                AppSnackbar("DW-H5600: $what failed: ${e.message}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        /** 0x13 as this watch reported it at 2026-11-16 18:57:31, before stock Smart Sync zeroed bytes 12-16. */
        val ORIGINAL_0x13 = intArrayOf(
            0x13, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x48, 0x17, 0x00, 0x08, 0x14,
        )
    }
}
