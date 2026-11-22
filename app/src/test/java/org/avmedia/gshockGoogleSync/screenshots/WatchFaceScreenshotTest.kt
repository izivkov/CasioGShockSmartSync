package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.runtime.Composable
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchFaceContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchFaceUi
import org.avmedia.gshockapi.io.WatchFaceCodec
import org.junit.Test

/** The Watch face screen: the custom face (top right choice), another face, details open, and no answer. */
class WatchFaceScreenshotTest : ScreenshotTest() {
    private fun ui(face: Int, message: String? = null) = WatchFaceUi(face = WatchFaceCodec.Face(face, listOf(0, 10, 12, 19)), message = message)

    @Composable private fun Screen(ui: WatchFaceUi, details: Boolean = false) =
        WatchFaceContent(ui, {}, {}, {}, { _, _ -> }, detailsOpen = details)

    @Test fun customLight() = snap("watch_face_custom", false) { Screen(ui(2, "Top right (Moon age) written. Read back.")) }
    @Test fun customDark() = snap("watch_face_custom", true) { Screen(ui(2)) }
    @Test fun otherFaceLight() = snap("watch_face_other", false) { Screen(ui(3)) }
    @Test fun detailsLight() = snap("watch_face_details", false) { Screen(ui(2), details = true) }
    @Test fun noAnswerLight() = snap("watch_face_no_answer", false) {
        Screen(WatchFaceUi(message = "The watch did not answer, or the face setting is not in the layout the app knows."))
    }
}
