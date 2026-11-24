package org.avmedia.gshockGoogleSync.ui.watchhealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.casio.ConnectionKeepAlive
import org.avmedia.gshockapi.io.ActivityFaceCodec
import org.avmedia.gshockapi.io.ActivityItemCodec
import org.avmedia.gshockapi.io.ActivityListCodec
import org.avmedia.gshockapi.io.ActivityModels
import org.avmedia.gshockapi.io.HeartRateFlagsCodec
import org.avmedia.gshockapi.io.HeartRateSettingCodec
import org.avmedia.gshockapi.io.RunMeasurementCodec
import org.avmedia.gshockapi.io.TrainingAlertCodec

data class WorkoutListUi(
    val working: Boolean = false,
    /** The activities the watch shows (0x53), in order. */
    val activities: List<Int>? = null,
    val message: String? = null,
)

/** The Workout settings list: the activities the watch shows (0x53), which can be switched and put in order. */
@HiltViewModel
class WorkoutSettingsListViewModel @Inject constructor(private val access: WatchSettingsAccess) : ViewModel() {
    private val _ui = MutableStateFlow(WorkoutListUi())
    val ui: StateFlow<WorkoutListUi> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(working = true) }
            readList(null)
        }
    }

    private suspend fun readList(message: String?) {
        val frame = access.read(ActivityListCodec.CODE)
        _ui.value =
            when {
                frame == null -> WorkoutListUi(message = "The watch did not answer. Connect the watch and try again.")
                !ActivityListCodec.layoutOk(frame) -> {
                    // The bytes are for the App log only.
                    ConnectionKeepAlive.log("0x53 answer not understood by the app: ${ActivityListCodec.hex(frame)}")
                    WorkoutListUi(message = "The activity list is not in the layout the app knows. It will not be changed.")
                }
                else -> WorkoutListUi(activities = ActivityListCodec.parse(frame), message = message)
            }
    }

    fun setEnabled(code: Int, on: Boolean) = change("Activity list") { ActivityListCodec.toggle(it, code, on) }

    fun move(code: Int, delta: Int) = change("Activity order") { ActivityListCodec.move(it, code, delta) { c -> c in ActivityModels.offered(ActivityModels.H5600) } }

    private fun change(what: String, edit: (List<Int>) -> List<Int>) {
        val now = _ui.value
        val current = now.activities ?: return
        if (now.working) return
        val wanted = edit(current)
        if (wanted == current) return
        if (wanted.isEmpty()) {
            _ui.update { it.copy(message = "At least one activity must stay on the watch.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(working = true, message = null) }
            val touched = (1..ActivityListCodec.MAX).toSet()
            val result = access.update(
                ActivityListCodec.CODE, touched,
                { frame ->
                    ActivityListCodec.encode(frame, wanted).also { out ->
                        ConnectionKeepAlive.log("0x53 read ${ActivityListCodec.hex(frame)}; to write ${out?.let { ActivityListCodec.hex(it) } ?: "nothing (refused)"}")
                    }
                },
                matches = ActivityListCodec::sameSlots,
            )
            ConnectionKeepAlive.log("0x53 result: $result")
            readList(result.text(what))
        }
    }
}

data class WorkoutActivityUi(
    val activity: Int = 0,
    val working: Boolean = false,
    val message: String? = null,
    val run: RunMeasurementCodec.Setting? = null,
    val heartRate: HeartRateSettingCodec.Setting? = null,
    val heartRateFlags: HeartRateFlagsCodec.Flags? = null,
    val alerts: TrainingAlertCodec.Setting? = null,
    val screens: List<List<Int>>? = null,
)

/**
 * One activity's settings: 0x40, 0x41 and 0x55 through the 0x54 wrapper, and 0x4E read with the activity.
 * Only proven fields are written; each write is read, changed in its bytes only, written and read back.
 */
@HiltViewModel
class WorkoutActivityViewModel @Inject constructor(private val access: WatchSettingsAccess) : ViewModel() {
    private val _ui = MutableStateFlow(WorkoutActivityUi())
    val ui: StateFlow<WorkoutActivityUi> = _ui.asStateFlow()

    fun load(activity: Int) {
        if (_ui.value.activity == activity) return
        _ui.value = WorkoutActivityUi(activity = activity)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(working = true) }
            readAll()
            _ui.update { it.copy(working = false) }
        }
    }

    private suspend fun inner(activity: Int, cls: Int): ByteArray? {
        val frame = access.readItem(activity, cls) ?: return null
        val item = ActivityItemCodec.parse(frame, activity, cls) ?: return null
        return ActivityItemCodec.inner(frame, item)
    }

    private suspend fun readAll() {
        val activity = _ui.value.activity
        val run = inner(activity, ActivityItemCodec.RUN)?.let { RunMeasurementCodec.parse(it) }
        val hrFrame = inner(activity, ActivityItemCodec.HEART_RATE)
        val alerts = inner(activity, ActivityItemCodec.TRAINING_ALERT)?.let { TrainingAlertCodec.parse(it) }
        val screens = access.readForActivity(ActivityFaceCodec.CODE, activity)?.let { ActivityFaceCodec.parse(it, activity) }
        _ui.update {
            it.copy(
                run = run,
                heartRate = hrFrame?.let { f -> HeartRateSettingCodec.parse(f) },
                heartRateFlags = hrFrame?.let { f -> HeartRateFlagsCodec.parse(f) },
                alerts = alerts,
                screens = screens,
                message = if (listOf(run, hrFrame, alerts, screens).all { v -> v == null }) "The watch did not answer for this activity." else it.message,
            )
        }
    }

    fun setAutoPause(on: Boolean) = item("Auto pause", ActivityItemCodec.RUN, RunMeasurementCodec.FLAG_BYTES) { RunMeasurementCodec.withAutoPause(it, on) }

    fun setAutoRunDetection(on: Boolean) = item("Auto run detection", ActivityItemCodec.RUN, RunMeasurementCodec.FLAG_BYTES) { RunMeasurementCodec.withAutoRunDetection(it, on) }

    fun setPhoneLocation(on: Boolean) = item("Use the phone's location", ActivityItemCodec.RUN, RunMeasurementCodec.FLAG_BYTES) { RunMeasurementCodec.withPhoneLocation(it, on) }

    fun setHeartRateMode(mode: Int) = item("Heart rate measurement", ActivityItemCodec.HEART_RATE, HeartRateSettingCodec.MODE_BYTES) { HeartRateSettingCodec.withMode(it, mode) }

    fun setInZoneNotice(on: Boolean) = item("In-zone notice", ActivityItemCodec.HEART_RATE, HeartRateFlagsCodec.FLAG_BYTES) { HeartRateFlagsCodec.withInZoneNotice(it, on) }

    fun setUseTarget(on: Boolean) = item("Use target", ActivityItemCodec.HEART_RATE, HeartRateFlagsCodec.FLAG_BYTES) { HeartRateFlagsCodec.withUseTarget(it, on) }

    fun setZoneAlert(on: Boolean) = item("Zone alert", ActivityItemCodec.HEART_RATE, HeartRateFlagsCodec.FLAG_BYTES) { HeartRateFlagsCodec.withZoneAlert(it, on) }

    fun setDistanceRepeat(value: Int) = item("Distance alert", ActivityItemCodec.TRAINING_ALERT, TrainingAlertCodec.DISTANCE_BYTES) { TrainingAlertCodec.withDistanceRepeat(it, value) }

    fun setCaloriesRepeat(value: Int) = item("Calories alert", ActivityItemCodec.TRAINING_ALERT, TrainingAlertCodec.CALORIES_BYTES) { TrainingAlertCodec.withCaloriesRepeat(it, value) }

    fun setAltitudeRepeat(value: Int) = item("Altitude alert", ActivityItemCodec.TRAINING_ALERT, TrainingAlertCodec.ALTITUDE_BYTES) { TrainingAlertCodec.withAltitudeRepeat(it, value) }

    private fun item(what: String, cls: Int, touched: Set<Int>, change: (ByteArray) -> ByteArray?) {
        if (_ui.value.working) return
        viewModelScope.launch {
            _ui.update { it.copy(working = true, message = null) }
            val result = access.updateItem(_ui.value.activity, cls, touched, change)
            readAll()
            _ui.update { it.copy(working = false, message = result.text(what)) }
        }
    }
}
