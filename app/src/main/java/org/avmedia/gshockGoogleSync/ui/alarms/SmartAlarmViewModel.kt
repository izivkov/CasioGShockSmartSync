package org.avmedia.gshockGoogleSync.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchSettingsAccess
import org.avmedia.gshockGoogleSync.ui.watchhealth.text
import org.avmedia.gshockapi.io.SmartAlarmCodec

/**
 * The smart alarm card of the Alarms tab: reads 0x50; each change is the checked write of [WatchSettingsAccess]
 * (read, change only the decoded field, write, read back), as it was in Watch health settings.
 */
@HiltViewModel
class SmartAlarmViewModel @Inject constructor(private val access: WatchSettingsAccess) : ViewModel() {
    private val _ui = MutableStateFlow(SmartAlarmUi())
    val ui: StateFlow<SmartAlarmUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.update { it.copy(working = true) }
            read()
            _ui.update { it.copy(working = false) }
        }
    }

    private suspend fun read() {
        val alarm = access.read(SmartAlarmCodec.CODE)?.let { SmartAlarmCodec.parse(it) }
        _ui.update {
            it.copy(
                alarm = alarm ?: it.alarm,
                message = if (alarm == null) (access.explain(SmartAlarmCodec.CODE) ?: access.explainParse(SmartAlarmCodec.CODE) ?: "The watch did not answer.") else it.message,
            )
        }
    }

    fun setTime(hour: Int, minute: Int) = write("Smart alarm time", SmartAlarmCodec.TIME_BYTES) { SmartAlarmCodec.withTime(it, hour, minute) }

    fun setOn(on: Boolean) = write("Smart alarm", SmartAlarmCodec.FLAG_BYTES) { SmartAlarmCodec.withOn(it, on) }

    fun setSnooze(on: Boolean) = write("Smart alarm snooze", SmartAlarmCodec.FLAG_BYTES) { SmartAlarmCodec.withSnooze(it, on) }

    fun setWindow(minutes: Int) = write("Smart alarm wake window", SmartAlarmCodec.WINDOW_BYTES) { SmartAlarmCodec.withWindow(it, minutes) }

    private fun write(what: String, touched: Set<Int>, change: (ByteArray) -> ByteArray?) {
        if (_ui.value.working) return
        viewModelScope.launch {
            _ui.update { it.copy(working = true, message = null) }
            val result = access.update(SmartAlarmCodec.CODE, touched, change)
            read()
            _ui.update { it.copy(working = false, message = result.text(what)) }
        }
    }
}
