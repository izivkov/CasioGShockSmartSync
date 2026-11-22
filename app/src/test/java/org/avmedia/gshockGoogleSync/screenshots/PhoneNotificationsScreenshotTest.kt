package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.avmedia.gshockGoogleSync.notifications.AppEntry
import org.avmedia.gshockGoogleSync.ui.settings.AppPickerContent
import org.avmedia.gshockGoogleSync.ui.settings.PhoneNotificationsContent
import org.junit.Test

/** The phone notification setting: off, on without apps, on with apps, and the app picker. */
class PhoneNotificationsScreenshotTest : ScreenshotTest() {
    private val apps = listOf(
        AppEntry("com.whatsapp", "WhatsApp"), AppEntry("com.google.android.gm", "Gmail"), AppEntry("com.google.android.calendar", "Calendar"),
        AppEntry("org.thoughtcrime.securesms", "Signal"), AppEntry("com.spotify.music", "Spotify"), AppEntry("com.android.chrome", "Chrome"),
    )

    @Composable private fun Card(enabled: Boolean, count: Int) =
        Column(Modifier.fillMaxSize().padding(top = 16.dp)) { PhoneNotificationsContent(enabled, count, {}, {}) }

    @Test fun offLight() = snap("phone_notifications_off", false) { Card(false, 0) }
    @Test fun offDark() = snap("phone_notifications_off", true) { Card(false, 0) }
    @Test fun onNoAppsLight() = snap("phone_notifications_on_empty", false) { Card(true, 0) }
    @Test fun onAppsLight() = snap("phone_notifications_on", false) { Card(true, 2) }
    @Test fun pickerLight() = snap("phone_notifications_picker", false) {
        AppPickerContent(apps, setOf("com.whatsapp", "com.google.android.gm"), { _, _ -> }, {})
    }
    @Test fun pickerSearchDark() = snap("phone_notifications_picker_search", true) {
        AppPickerContent(apps, setOf("com.google.android.gm"), { _, _ -> }, {}, initialQuery = "goo")
    }
}
