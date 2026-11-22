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
import org.avmedia.gshockapi.io.BasicHealthFieldsCodec
import org.avmedia.gshockapi.io.HeartRateSettingCodec
import org.avmedia.gshockapi.io.PhysicalSettingsCodec
import org.avmedia.gshockapi.io.ProfileCodec
import org.avmedia.gshockapi.io.SmartAlarmCodec
import org.avmedia.gshockapi.io.StepGoalCodec

data class WatchHealthUi(
    val working: Boolean = false,
    val message: String? = null,
    val heartRate: HeartRateSettingCodec.Setting? = null,
    val sleep: PhysicalSettingsCodec.Sleep? = null,
    val basic: BasicHealthFieldsCodec.Fields? = null,
    val stepGoal: Int? = null,
    val profile: ProfileCodec.Profile? = null,
    val alarm: SmartAlarmCodec.Setting? = null,
)

/**
 * The Watch health settings screen. Reads 0x41, 0x4F, 0x13, 0x43, 0x45 and 0x50; writes all but 0x50
 * (the smart alarm layout is not confirmed). A write is read, changed in its decoded fields only, written
 * and read back by [WatchSettingsAccess]; a failure is shown once and never retried.
 */
@HiltViewModel
class WatchHealthSettingsViewModel @Inject constructor(private val access: WatchSettingsAccess) : ViewModel() {
    private val _ui = MutableStateFlow(WatchHealthUi())
    val ui: StateFlow<WatchHealthUi> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(working = true) }
            readAll()
            _ui.update { it.copy(working = false) }
        }
    }

    private suspend fun readAll() {
        val hr = access.read(HeartRateSettingCodec.CODE)?.let { HeartRateSettingCodec.parse(it) }
        val sleep = access.read(PhysicalSettingsCodec.CODE)?.let { PhysicalSettingsCodec.parse(it) }
        val basic = access.read(BasicHealthFieldsCodec.CODE)?.let { BasicHealthFieldsCodec.parse(it) }
        val goal = access.read(StepGoalCodec.CODE)?.let { StepGoalCodec.parse(it) }
        val profile = access.read(ProfileCodec.CODE)?.let { ProfileCodec.parse(it) }
        val alarm = access.read(SmartAlarmCodec.CODE)?.let { SmartAlarmCodec.parse(it) }
        val none = listOf(hr, sleep, basic, goal, profile, alarm).all { it == null }
        _ui.update {
            it.copy(
                heartRate = hr, sleep = sleep, basic = basic, stepGoal = goal, profile = profile, alarm = alarm,
                message = if (none) "The watch did not answer. Connect the watch and try again." else it.message,
            )
        }
    }

    fun setHeartRateMode(mode: Int) = write(HeartRateSettingCodec.CODE, "Heart rate measurement", HeartRateSettingCodec.MODE_BYTES) { HeartRateSettingCodec.withMode(it, mode) }

    fun setBedtime(hour: Int, minute: Int) = write(PhysicalSettingsCodec.CODE, "Bedtime", PhysicalSettingsCodec.BEDTIME_BYTES) { PhysicalSettingsCodec.withBedtime(it, hour, minute) }

    fun setWakeUp(hour: Int, minute: Int) = write(PhysicalSettingsCodec.CODE, "Wake-up time", PhysicalSettingsCodec.WAKE_BYTES) { PhysicalSettingsCodec.withWakeUp(it, hour, minute) }

    fun setNightWindow(start: Pair<Int, Int>, end: Pair<Int, Int>) = write(BasicHealthFieldsCodec.CODE, "Night window", BasicHealthFieldsCodec.NIGHT_BYTES) { BasicHealthFieldsCodec.withNightWindow(it, start, end) }

    fun setStepReminder(on: Boolean) = write(BasicHealthFieldsCodec.CODE, "Step reminder", BasicHealthFieldsCodec.FLAGS_BYTES) { BasicHealthFieldsCodec.withStepReminder(it, on) }

    fun setMiles(on: Boolean) = write(BasicHealthFieldsCodec.CODE, "Length unit", BasicHealthFieldsCodec.FLAGS_BYTES) { BasicHealthFieldsCodec.withUnitBit(it, on) }

    fun setStepGoal(steps: Int) = write(StepGoalCodec.CODE, "Step goal", StepGoalCodec.BYTES) { StepGoalCodec.withSteps(it, steps) }

    fun setHeightAndWeight(heightCm: Int, weightKg: Int) = write(ProfileCodec.CODE, "Profile", ProfileCodec.BYTES) { ProfileCodec.withHeightAndWeight(it, heightCm, weightKg) }

    private fun write(
        code: Int,
        what: String,
        touched: Set<Int>,
        change: (ByteArray) -> ByteArray?,
    ) {
        if (_ui.value.working) return
        viewModelScope.launch {
            _ui.update { it.copy(working = true, message = null) }
            val result = access.update(code, touched, change)
            readAll()
            _ui.update { it.copy(working = false, message = result.text(what)) }
        }
    }
}
