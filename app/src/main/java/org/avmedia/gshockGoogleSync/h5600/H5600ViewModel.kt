package org.avmedia.gshockGoogleSync.h5600

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockapi.model.SoundMode
import org.avmedia.gshockapi.model.WatchFaceItem
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class H5600ViewModel @Inject constructor(
    private val api: GShockRepository,
    private val runner: H5600ScheduleRunner,
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
        val dynamicFace: Boolean = false,
        val realSun: String = "",
        val realWeather: String = "",
        val watchShould: String = "",
        val tempSun: Boolean = false,
        val tempText: String = "",
        val riseCorr: Int = 0,
        val setCorr: Int = 0,
        val sunsetText: String = "",
        val lastApplied: String? = null,
    )

    // Start from the last values read from the watch; null (spinner) only if never read.
    private val _state = MutableStateFlow(
        UiState(
            soundMode = org.avmedia.gshockGoogleSync.utils.LastKnown.int(context, "sound_mode")?.let(SoundMode::fromCode),
            soundHex = org.avmedia.gshockGoogleSync.utils.LastKnown.string(context, "sound_hex") ?: "",
            watchFaceCode = org.avmedia.gshockGoogleSync.utils.LastKnown.int(context, "watch_face"),
            faceNumber = org.avmedia.gshockGoogleSync.utils.LastKnown.int(context, "face_number"),
            watchFace = org.avmedia.gshockGoogleSync.utils.LastKnown.int(context, "watch_face")?.let(WatchFaceItem::fromCode),
        ),
    )
    val state: StateFlow<UiState> = _state

    // Declared before init: refresh() below already uses them (was a crash on opening the tab).
    // Watch actions run one after another in tap order, without blocking the UI: each change is
    // shown at once (optimistic), [busy] only drives the pull-to-refresh spinner.
    private val queue = kotlinx.coroutines.sync.Mutex()
    private val running = java.util.concurrent.atomic.AtomicInteger(0)

    init {
        refresh()
        // Times on this tab are formatted when built: rebuild them when the 12h/24h choice changes.
        viewModelScope.launch {
            org.avmedia.gshockGoogleSync.utils.AppTimeFormat.is24h.drop(1).collect { reloadPrefs() }
        }
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
            lastApplied = H5600Schedule.lastApplied(context),
        )
    }

    fun refresh() {
        val supported = api.isH5600SettingsSupported()
        _state.update { it.copy(supported = supported) }
        reloadPrefs()
        if (!supported) return
        run("read") {
            // The library retries unanswered / rejected reads; a read that still fails keeps the
            // last known value. 0x42 is read once (mode = byte 1); the two 0x51 reads run together
            // and share one request.
            val hex = readOrNull { api.getH5600SoundPacketHex() }
            val mode = hex?.split(" ")?.getOrNull(1)?.toIntOrNull(16)?.let(SoundMode::fromCode)
            val (code, faceNo) = kotlinx.coroutines.coroutineScope {
                val c = async { readOrNull { api.getWatchFaceCode() } }
                val f = async { readOrNull { api.getH5600WatchFaceNumber() } }
                c.await() to f.await()
            }
            Timber.i("H5600 re-read: 0x42=$hex mode=$mode face=$code")
            _state.update {
                it.copy(
                    soundMode = mode ?: it.soundMode,
                    soundHex = hex ?: it.soundHex,
                    watchFaceCode = code ?: it.watchFaceCode,
                    watchFace = (code ?: it.watchFaceCode)?.let(WatchFaceItem::fromCode),
                    faceNumber = faceNo ?: it.faceNumber,
                )
            }
            mode?.let { m -> org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "sound_mode", m.code) }
            hex?.let { h -> org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "sound_hex", h) }
            code?.let { c -> org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "watch_face", c) }
            faceNo?.let { f -> org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "face_number", f) }
        }
    }

    /**
     * Read back after a write until the watch reports [expected]: it applies writes a moment
     * later, so an immediate read can still return the old value (seen: 70 ms after writing 0x51).
     * Returns the last value read (or [expected] if every read failed).
     */
    private suspend fun <T> confirm(expected: T, read: suspend () -> T?): T {
        var last: T? = null
        repeat(3) {
            val v = readOrNull { read() }
            if (v != null) last = v
            if (v == expected) return v
            kotlinx.coroutines.delay(1_200)
        }
        return last ?: expected
    }

    fun setSoundMode(mode: SoundMode) {
        val previous = _state.value.soundMode
        _state.update { it.copy(soundMode = mode) } // move the UI immediately
        run("set sound mode", onError = { _state.update { it.copy(soundMode = previous) } }) {
            api.setSoundMode(mode)
            val v = confirm(mode) { api.getSoundMode() } // confirm from the watch
            _state.update { it.copy(soundMode = v) }
            org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "sound_mode", v.code)
        }
    }

    fun setFaceNumber(face: Int) {
        val previous = _state.value.faceNumber
        _state.update { it.copy(faceNumber = face) }
        run("switch watch face", onError = { _state.update { it.copy(faceNumber = previous) } }) {
            api.setH5600WatchFaceNumber(face)
            val f: Int? = confirm(face) { api.getH5600WatchFaceNumber() }
            _state.update { it.copy(faceNumber = f) }
            f?.let { org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "face_number", it) }
        }
    }

    fun setWatchFace(item: WatchFaceItem) {
        val previous = _state.value.watchFace
        _state.update { it.copy(watchFace = item) }
        run("set watch face", onError = { _state.update { it.copy(watchFace = previous) } }) {
            api.setWatchFaceItem(item)
            val code = confirm(item.code) { api.getWatchFaceCode() }
            _state.update { it.copy(watchFaceCode = code, watchFace = WatchFaceItem.fromCode(code)) }
            org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "watch_face", code)
        }
    }

    /** On: fetch the temperature now and send the fake coordinates. Off: send the real location again. */
    fun setTempSun(on: Boolean) {
        TempSun.setEnabled(context, on)
        reloadPrefs()
        run(if (on) "temperature sunrise" else "real sunrise") {
            val msg = if (on) runner.refreshTempSun(force = true)
            else { runner.syncLocationAndTime(); "real sunrise restored" }
            reloadPrefs()
            msg?.let { AppSnackbar(it) }
        }
    }

    /** Fine-tune the watch-formula corrections, then re-send the coordinates + time sync. */
    fun adjustTempSun(riseDelta: Int, setDelta: Int) {
        TempSun.saveCorrections(context, TempSun.riseCorrection(context) + riseDelta, TempSun.setCorrection(context) + setDelta)
        reloadPrefs()
        if (TempSun.isEnabled(context)) run("re-send temperature sunrise") { runner.syncLocationAndTime() }
    }

    /** Fetch the weather for display only (nothing is written to the watch). */
    fun refreshWeatherOnly() = run("refresh weather") {
        val loc = org.avmedia.gshockGoogleSync.services.LocationProvider.getLocation(context) ?: error("no location")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            TempSun.fetchTemperature(context, loc.latitude, loc.longitude)
        }
        reloadPrefs()
    }

    /** On: the face follows the sunset window right away. Off: the face stays as it is. */
    fun setDynamicFace(on: Boolean) {
        SunFace.setEnabled(context, on)
        reloadPrefs()
        if (on) run("dynamic face") {
            runner.applySunFace() // only the face, not the whole scheduler
            reloadPrefs()
            readOrNull { api.getWatchFaceCode() }?.let { code ->
                _state.update { it.copy(watchFaceCode = code, watchFace = WatchFaceItem.fromCode(code)) }
                org.avmedia.gshockGoogleSync.utils.LastKnown.put(context, "watch_face", code)
            }
        }
    }

    /**
     * The watch only recalculates sunrise/sunset when the time / cities are written, so a bare
     * location packet is not enough: a full time sync (Fine Adjustment and time-zone option as on
     * the Time tab) with the location written inside it.
     */
    fun sendLocation() = run("send location") {
        runner.syncLocationAndTime()
        AppSnackbar("Sunrise/sunset: location sent, time synced")
    }

    private fun run(what: String, onError: () -> Unit = {}, block: suspend () -> Unit) {
        _state.update { it.copy(busy = running.incrementAndGet() > 0) }
        viewModelScope.launch {
            try { queue.withLock { runOne(what, onError, block) } } finally {
                _state.update { it.copy(busy = running.decrementAndGet() > 0) }
            }
        }
    }

    private suspend fun runOne(what: String, onError: () -> Unit, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e // screen left: not a failure
            Timber.e(e, "H5600 $what timed out")
            onError()
            AppSnackbar("DW-H5600: $what timed out")
        } catch (e: Exception) {
            Timber.e(e, "H5600 $what failed")
            onError()
            AppSnackbar("DW-H5600: $what failed: ${e.message}")
        }
    }

    /** A watch read whose failure keeps the last known value; real cancellation is rethrown. */
    private suspend fun <T> readOrNull(block: suspend () -> T?): T? =
        try {
            kotlinx.coroutines.withTimeoutOrNull(12_000) { block() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("H5600 read failed: ${e.message}"); null
        }
}
