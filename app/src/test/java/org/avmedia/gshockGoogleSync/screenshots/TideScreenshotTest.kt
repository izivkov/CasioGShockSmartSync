package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.runtime.Composable
import org.avmedia.gshockGoogleSync.ui.watchhealth.TideContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.TideUi
import org.avmedia.gshockapi.io.TideSettingCodec
import org.avmedia.gshockapi.io.TideUserPointCodec
import org.junit.Test

/** The Tide screen: a port sent by the app, own points (one never set), the height unit, the short credit line, and no answer (the point form is a dialog with text fields, which a screenshot cannot settle). */
class TideScreenshotTest : ScreenshotTest() {
    private val points = listOf(
        TideUserPointCodec.Point(4, 20, 51.23, 2.93, 1f, false),
        TideUserPointCodec.FACTORY, // never set: "Not set", not the factory numbers
        TideUserPointCodec.Point(11, 5, 50.1, -5.5, 0f, true),
    )

    private fun ui(source: Int, message: String? = null) =
        TideUi(setting = TideSettingCodec.Setting(source, 100, 1, false), points = points, names = listOf("Oostende pier", "", "Home beach"), message = message)

    @Composable private fun Screen(ui: TideUi, open: Int? = null) = TideContent(ui, {}, {}, {}, {}, { _, _, _ -> }, { _, _ -> }, openPoint = open)

    @Test fun portLight() = snap("tide", false) { Screen(ui(4, "Oostende sent to the watch and selected.")) }
    @Test fun portDark() = snap("tide", true) { Screen(ui(1)) }
    @Test fun noAnswerLight() = snap("tide_no_answer", false) {
        Screen(TideUi(message = "The watch did not answer, or the tide setting is not in the layout the app knows."))
    }
}
