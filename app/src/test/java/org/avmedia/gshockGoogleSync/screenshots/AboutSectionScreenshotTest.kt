package org.avmedia.gshockGoogleSync.screenshots

import org.avmedia.gshockGoogleSync.ui.settings.AboutSection
import org.junit.Test

/** The bottom of Settings: About with the app's and the library's version and the App log entry. */
class AboutSectionScreenshotTest : ScreenshotTest() {
    @Test fun aboutLight() = snap("settings_about", false) { AboutSection(onOpenAppLog = {}) }
    @Test fun aboutDark() = snap("settings_about", true) { AboutSection(onOpenAppLog = {}) }
}
