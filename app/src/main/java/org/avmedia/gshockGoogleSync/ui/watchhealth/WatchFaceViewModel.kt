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
import org.avmedia.gshockapi.io.WatchFaceCodec

data class WatchFaceUi(
    val working: Boolean = false,
    val face: WatchFaceCodec.Face? = null,
    val message: String? = null,
    /** The faces the connected model has: 1 to [faceCount]. */
    val faceCount: Int = 4,
)

/** The Watch face screen: read 0x51, change the face or one item, read it back. */
@HiltViewModel
class WatchFaceViewModel @Inject constructor(private val access: WatchSettingsAccess) : ViewModel() {
    private val _ui = MutableStateFlow(WatchFaceUi())
    val ui: StateFlow<WatchFaceUi> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(working = true) }
            val frame = access.read(WatchFaceCodec.CODE)
            val face = frame?.let { WatchFaceCodec.parse(it) }
            _ui.update {
                it.copy(
                    working = false,
                    face = face,
                    message = if (face == null) "The watch did not answer, or the face setting is not in the layout the app knows." else it.message,
                )
            }
        }
    }

    /** Faces 1 to 4 only: [WatchFaceCodec.withFace] refuses anything else, so nothing is written for it. */
    fun setFace(face: Int) = write(WatchFaceCodec.faceName(face), WatchFaceCodec.FACE_BYTES) { WatchFaceCodec.withFace(it, face) }

    /** The top-right item of the custom face (slot 3). Slots 1, 2 and 4 are never written from the screen. */
    fun setTopRight(item: Int) =
        write("Top right (${WatchFaceCodec.itemName(item)})", WatchFaceCodec.TOP_RIGHT_BYTES) { WatchFaceCodec.withTopRight(it, item) }

    private fun write(
        what: String,
        touched: Set<Int>,
        change: (ByteArray) -> ByteArray?,
    ) {
        if (_ui.value.working) return
        viewModelScope.launch {
            _ui.update { it.copy(working = true, message = null) }
            val result = access.update(WatchFaceCodec.CODE, touched, change)
            _ui.update { it.copy(message = result.text(what)) }
            val frame = access.read(WatchFaceCodec.CODE)
            _ui.update { it.copy(working = false, face = frame?.let { f -> WatchFaceCodec.parse(f) } ?: it.face) }
        }
    }
}
