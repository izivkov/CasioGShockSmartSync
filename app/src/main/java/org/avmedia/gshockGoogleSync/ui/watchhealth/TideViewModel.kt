package org.avmedia.gshockGoogleSync.ui.watchhealth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.io.SettingWriteResult
import org.avmedia.gshockapi.io.TideRecords
import org.avmedia.gshockapi.io.TideSettingCodec
import org.avmedia.gshockapi.io.TideStation
import org.avmedia.gshockapi.io.TideTransferIO
import org.avmedia.gshockapi.io.TideUserPointCodec

/** Which form of the port record to send. */
enum class TideForm { FOUR, SIXTY }

data class TideUi(
    val working: Boolean = false,
    val setting: TideSettingCodec.Setting? = null,
    /** The three own points; null where the watch did not answer or the answer is not in the known layout. */
    val points: List<TideUserPointCodec.Point?> = listOf(null, null, null),
    /** Names kept in the app only (the watch has no name for an own point). */
    val names: List<String> = listOf("", "", ""),
    val message: String? = null,
)

/** The Tide screen: the 0x2E setting (unit 2), the three own points (units 3 to 5) and a port sent with the 0x23 transfer. */
@HiltViewModel
class TideViewModel @Inject constructor(
    private val access: WatchSettingsAccess,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _ui = MutableStateFlow(TideUi(names = (1..TideUserPointCodec.POINTS).map { LocalDataStorage.get(context, nameKey(it), "") ?: "" }))
    val ui: StateFlow<TideUi> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_ui.value.working) return
        viewModelScope.launch {
            _ui.update { it.copy(working = true) }
            readAll(null)
        }
    }

    private suspend fun readAll(message: String?) {
        val setting = access.readTide(TideSettingCodec.UNIT)?.let { TideSettingCodec.parse(it) }
        val points = (1..TideUserPointCodec.POINTS).map { n -> access.readTide(TideUserPointCodec.unit(n))?.let { TideUserPointCodec.parse(it, n) } }
        _ui.update {
            it.copy(
                working = false, setting = setting, points = points,
                message = message ?: if (setting == null) "The watch did not answer, or the tide setting is not in the layout the app knows." else it.message,
            )
        }
    }

    /** Which data the watch shows: 0 preset port, 1 to 3 own point, 4 the port sent by the app. */
    fun select(source: Int) = run("Tide data") { access.updateTide(TideSettingCodec.UNIT, TideSettingCodec.SOURCE_BYTES) { TideSettingCodec.withSource(it, source) }.text("Tide data") }

    fun setFeet(feet: Boolean) = run("Unit") { access.updateTide(TideSettingCodec.UNIT, TideSettingCodec.FEET_BYTES) { TideSettingCodec.withFeet(it, feet) }.text(if (feet) "Feet" else "Metres") }

    /** Writes own point [n] (1 to 3), then selects it (unit 2 index 0 = n), in that order (notes I2). The name stays in the app. */
    fun savePoint(n: Int, name: String, point: TideUserPointCodec.Point) = run("Own point $n") {
        LocalDataStorage.put(context, nameKey(n), name.trim())
        _ui.update { it.copy(names = it.names.toMutableList().also { l -> l[n - 1] = name.trim() }) }
        val written = access.updateTide(TideUserPointCodec.unit(n), TideUserPointCodec.TOUCHED) { TideUserPointCodec.withPoint(it, n, point) }
        if (written is SettingWriteResult.Failed) {
            written.message
        } else {
            val chosen = access.updateTide(TideSettingCodec.UNIT, TideSettingCodec.SOURCE_BYTES) { TideSettingCodec.withSource(it, n) }
            if (chosen is SettingWriteResult.Failed) "Own point $n was saved, but showing it failed: ${chosen.message}" else "Own point $n saved and shown on the watch."
        }
    }

    /** Sends [station] as a port record, then points the setting at it (index 3 and 4 = id, index 0 = 4). Nothing is written to the setting when the transfer failed. */
    fun sendPort(station: TideStation, form: TideForm) = run("Port") {
        val record = if (form == TideForm.FOUR) TideRecords.build4(station) else TideRecords.build60(station)
        val id = if (form == TideForm.FOUR) TideRecords.FORM4_ID else TideRecords.FORM60_ID
        if (record == null) {
            "${station.name} has too few constants for this form. Nothing was sent."
        } else if (access.isBusy) {
            "The watch is sending health data. Try again in a minute."
        } else {
            val error = TideTransferIO.send(record)
            if (error != null) {
                "$error Nothing was changed in the tide setting."
            } else {
                val result = access.updateTide(TideSettingCodec.UNIT, TideSettingCodec.SOURCE_BYTES + TideSettingCodec.APP_PORT_BYTES) { frame ->
                    TideSettingCodec.withAppPort(frame, id)?.let { TideSettingCodec.withSource(it, TideSettingCodec.SOURCE_APP_PORT) }
                }
                if (result is SettingWriteResult.Failed) "${station.name} was sent, but selecting it failed: ${result.message}" else "${station.name} sent to the watch and selected."
            }
        }
    }

    private fun run(what: String, block: suspend () -> String) {
        if (_ui.value.working) return
        viewModelScope.launch {
            _ui.update { it.copy(working = true, message = "$what: talking to the watch...") }
            val text = try {
                block()
            } catch (t: Throwable) {
                "$what failed: ${t.message ?: t.javaClass.simpleName}"
            }
            readAll(text)
        }
    }

    private fun nameKey(n: Int) = "tide_point_name_$n"
}
