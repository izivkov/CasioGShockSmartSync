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
import org.avmedia.gshockGoogleSync.notifications.NotificationAccessCheck
import org.avmedia.gshockGoogleSync.notifications.PhoneNotificationMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.junit.Test

/** The phone notification setting (All apps / Only selected apps / Off), the access warning, and the app picker. */
class PhoneNotificationsScreenshotTest : ScreenshotTest() {
    private val apps = listOf(
        AppEntry("com.whatsapp", "WhatsApp"), AppEntry("com.google.android.gm", "Gmail"), AppEntry("com.google.android.calendar", "Calendar"),
        AppEntry("org.thoughtcrime.securesms", "Signal"), AppEntry("com.spotify.music", "Spotify"), AppEntry("com.android.chrome", "Chrome"),
    )

    /** Plain coloured squares as stand-in icons; Spotify stays on the placeholder (still loading). */
    private val icons: suspend (String) -> ImageBitmap? = { pkg ->
        if (pkg == "com.spotify.music") {
            null
        } else {
            val bitmap = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bitmap).drawColor(android.graphics.Color.rgb(40 + (pkg.length * 29) % 180, 60 + (pkg.hashCode() and 0x7F), 100 + (pkg.length * 53) % 120))
            bitmap.asImageBitmap()
        }
    }

    @Composable private fun Card(mode: PhoneNotificationMode, count: Int, warning: String? = null) =
        Column(Modifier.fillMaxSize().padding(top = 16.dp)) { PhoneNotificationsContent(mode, count, {}, {}, warning) }

    @Test fun allLight() = snap("phone_notifications_all", false) { Card(PhoneNotificationMode.ALL, 0) }
    @Test fun allDark() = snap("phone_notifications_all", true) { Card(PhoneNotificationMode.ALL, 0) }
    @Test fun offLight() = snap("phone_notifications_off", false) { Card(PhoneNotificationMode.OFF, 0) }
    @Test fun offDark() = snap("phone_notifications_off", true) { Card(PhoneNotificationMode.OFF, 0) }
    @Test fun onNoAppsLight() = snap("phone_notifications_on_empty", false) { Card(PhoneNotificationMode.SELECTED, 0) }
    @Test fun onAppsLight() = snap("phone_notifications_on", false) { Card(PhoneNotificationMode.SELECTED, 2) }
    @Test fun onAppsDark() = snap("phone_notifications_on", true) { Card(PhoneNotificationMode.SELECTED, 2) }
    @Test fun accessOffLight() = snap("phone_notifications_access_off", false) { Card(PhoneNotificationMode.ALL, 0, NotificationAccessCheck.WARNING) }
    @Test fun accessOffDark() = snap("phone_notifications_access_off", true) { Card(PhoneNotificationMode.ALL, 0, NotificationAccessCheck.WARNING) }
    @Test fun pickerLoadingLight() = snap("phone_notifications_picker_loading", false) {
        AppPickerContent(null, emptySet(), { _, _ -> }, {})
    }
    @Test fun pickerLight() = snap("phone_notifications_picker", false) {
        AppPickerContent(apps, setOf("com.whatsapp", "com.google.android.gm"), { _, _ -> }, {}, iconLoader = icons)
    }
    @Test fun pickerSearchDark() = snap("phone_notifications_picker_search", true) {
        AppPickerContent(apps, setOf("com.google.android.gm"), { _, _ -> }, {}, initialQuery = "goo", iconLoader = icons)
    }
}
