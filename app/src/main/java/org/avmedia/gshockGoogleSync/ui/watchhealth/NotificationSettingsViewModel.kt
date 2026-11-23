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
import org.avmedia.gshockapi.io.NotificationManagerCodec
import org.avmedia.gshockapi.io.NotificationSoundCodec

data class NotificationSettingsUi(
    val working: Boolean = false,
    val message: String? = null,
    val sounds: NotificationSoundCodec.Setting? = null,
    val manager: NotificationManagerCodec.Setting? = null,
)

/**
 * The Notification settings screen. Reads 0x42 and 0x3D; every write is read, changed in its decoded fields only,
 * written and read back by [WatchSettingsAccess]. 0x3D is written only when language or off-at-night changes.
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(private val access: WatchSettingsAccess) : ViewModel() {
    private val _ui = MutableStateFlow(NotificationSettingsUi())
    val ui: StateFlow<NotificationSettingsUi> = _ui.asStateFlow()

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
        val sounds = access.read(NotificationSoundCodec.CODE)?.let { NotificationSoundCodec.parse(it) }
        val manager = access.read(NotificationManagerCodec.CODE)?.let { NotificationManagerCodec.parse(it) }
        _ui.update {
            it.copy(
                sounds = sounds,
                manager = manager,
                message = if (sounds == null && manager == null) (access.explain(NotificationSoundCodec.CODE, NotificationManagerCodec.CODE) ?: "The watch did not answer. Connect the watch and try again.") else (access.explain(NotificationSoundCodec.CODE, NotificationManagerCodec.CODE) ?: it.message),
            )
        }
    }

    fun setSoundMode(mode: Int) = sound("Sound mode", NotificationSoundCodec.MODE_BYTES) { NotificationSoundCodec.withSoundMode(it, mode) }

    fun setVibrationMode(mode: Int) = sound("Vibration mode", NotificationSoundCodec.MODE_BYTES) { NotificationSoundCodec.withVibrationMode(it, mode) }

    fun setSoundEvent(event: Int, on: Boolean) =
        sound(NotificationSoundCodec.EVENTS[event] + " sound", NotificationSoundCodec.SOUND_MASK_BYTES) { NotificationSoundCodec.withSoundEvent(it, event, on) }

    fun setVibrationEvent(event: Int, on: Boolean) =
        sound(NotificationSoundCodec.EVENTS[event] + " vibration", NotificationSoundCodec.VIBRATION_MASK_BYTES) { NotificationSoundCodec.withVibrationEvent(it, event, on) }

    fun setPattern(pattern: Int) = sound("Sound pattern", NotificationSoundCodec.PATTERN_BYTES) { NotificationSoundCodec.withPattern(it, pattern) }

    fun setLanguage(language: Int) =
        write(NotificationManagerCodec.CODE, "Notification language", NotificationManagerCodec.LANGUAGE_BYTES) { NotificationManagerCodec.withLanguage(it, language) }

    fun setOffAtNight(value: Int) =
        write(NotificationManagerCodec.CODE, "Off at night", NotificationManagerCodec.NIGHT_BYTES) { NotificationManagerCodec.withOffAtNight(it, value) }

    private fun sound(what: String, touched: Set<Int>, change: (ByteArray) -> ByteArray?) = write(NotificationSoundCodec.CODE, what, touched, change)

    private fun write(code: Int, what: String, touched: Set<Int>, change: (ByteArray) -> ByteArray?) {
        if (_ui.value.working) return
        viewModelScope.launch {
            _ui.update { it.copy(working = true, message = null) }
            val back: (ByteArray, ByteArray) -> ByteArray =
                if (code == NotificationManagerCodec.CODE) NotificationManagerCodec::expectedBack else { _, written -> written }
            val result = access.update(code, touched, change, back)
            readAll()
            _ui.update { it.copy(working = false, message = result.text(what)) }
        }
    }
}
