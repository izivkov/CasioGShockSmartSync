package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.runtime.Composable
import io.mockk.mockk
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchHealthSettingsContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.WatchHealthUi
import org.avmedia.gshockapi.io.BasicHealthFieldsCodec
import org.avmedia.gshockapi.io.HeartRateSettingCodec
import org.avmedia.gshockapi.io.LengthUnit
import org.avmedia.gshockapi.io.PhysicalSettingsCodec
import org.avmedia.gshockapi.io.ProfileCodec
import org.junit.Test

/** Watch health settings: the Profile section first, in kilometres and in miles, and the unit shown as a choice. */
class WatchHealthScreenshotTest : ScreenshotTest() {
    private fun ui(unit: LengthUnit, user: Boolean = true) = WatchHealthUi(
        heartRate = HeartRateSettingCodec.Setting(2, 2, 120, 130),
        sleep = PhysicalSettingsCodec.Sleep(23 to 15, 6 to 45),
        body = PhysicalSettingsCodec.Body(3, 190, 58, 45, maxHrByUser = user, restingHrByUser = false, vo2maxByUser = false),
        basic = BasicHealthFieldsCodec.Fields(unit, true, 22 to 30, 6 to 15),
        stepGoal = 8000,
        profile = ProfileCodec.Profile(170, 70, 1990, 1, 1, true, imperialLength = unit == LengthUnit.MILES),
    )

    @Composable private fun Screen(ui: WatchHealthUi) = WatchHealthSettingsContent(ui, {}, mockk(relaxed = true))

    @Test fun kilometresLight() = snap("watch_health_km", false) { Screen(ui(LengthUnit.KM)) }
    @Test fun kilometresDark() = snap("watch_health_km", true) { Screen(ui(LengthUnit.KM)) }
    @Test fun milesLight() = snap("watch_health_miles", false) { Screen(ui(LengthUnit.MILES)) }
    @Test fun noAnswerLight() = snap("watch_health_no_answer", false) { Screen(WatchHealthUi(message = "The watch did not answer. Connect the watch and try again.")) }
}
