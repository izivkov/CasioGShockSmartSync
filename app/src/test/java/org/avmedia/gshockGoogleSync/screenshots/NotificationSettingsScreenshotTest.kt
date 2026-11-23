package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.runtime.Composable
import io.mockk.mockk
import org.avmedia.gshockGoogleSync.ui.watchhealth.NotificationSettingsContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.NotificationSettingsUi
import org.avmedia.gshockapi.io.NotificationManagerCodec
import org.avmedia.gshockapi.io.NotificationSoundCodec
import org.junit.Test

/** Notification settings: custom mode (per-event switches), on and off (no switches), one mode custom, and no answer. */
class NotificationSettingsScreenshotTest : ScreenshotTest() {
    private fun ui(mode: Int, vibration: Int = mode) = NotificationSettingsUi(
        sounds = NotificationSoundCodec.Setting(mode, vibration, 0x0105, 0x4002, 2),
        manager = NotificationManagerCodec.Setting(2, 1, 1),
        message = "Sound pattern saved and read back from the watch.",
    )

    @Composable private fun Screen(ui: NotificationSettingsUi) = NotificationSettingsContent(ui, {}, mockk(relaxed = true))

    @Test fun customLight() = snap("notification_settings_custom", false) { Screen(ui(NotificationSoundCodec.CUSTOM)) }
    @Test fun customDark() = snap("notification_settings_custom", true) { Screen(ui(NotificationSoundCodec.CUSTOM)) }
    @Test fun soundCustomVibrationOnLight() = snap("notification_settings_sound_custom", false) { Screen(ui(NotificationSoundCodec.CUSTOM, 2)) }
    @Test fun offLight() = snap("notification_settings_off", false) { Screen(ui(0)) }
    @Test fun onLight() = snap("notification_settings_on", false) { Screen(ui(2)) }
    @Test fun noAnswerLight() = snap("notification_settings_no_answer", false) {
        Screen(NotificationSettingsUi(message = "The watch did not answer. Connect the watch and try again."))
    }
}
