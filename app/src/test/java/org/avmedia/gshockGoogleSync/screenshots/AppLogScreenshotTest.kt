package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.runtime.Composable
import org.avmedia.gshockGoogleSync.ui.settings.AppLogContent
import org.avmedia.gshockGoogleSync.ui.settings.visibleLogLines
import org.avmedia.gshockGoogleSync.log.LogSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings > App log with sample lines of every source, newest first. */
class AppLogScreenshotTest : ScreenshotTest() {
    private val raw = listOf(
        "2026-11-23 08:01:02.100 [Watch] 49 00 received, 49 01 written",
        "2026-11-23 08:05:10.250 [Health] LifeLog step refresh failed (TimeoutException)",
        "2026-11-23 08:10:00.000 [Home Assistant] Home Assistant push: failed after 4 tries: host not reachable",
        "2026-11-23 08:10:31.400 [Home Assistant] Home Assistant update sensors: sent after 2 tries",
        "2026-11-23 08:12:00.000 [App] Session closed with an error",
    )

    @Composable private fun Screen(filter: LogSource? = null) =
        AppLogContent(visibleLogLines(raw, filter), filter, {}, {}, {}, {})

    @Test fun filterShowsOnlyThatSourceNewestFirst() {
        assertEquals(5, visibleLogLines(raw, null).size)
        assertEquals("Session closed with an error", visibleLogLines(raw, null).first().text)
        assertEquals(listOf(LogSource.Health), visibleLogLines(raw, LogSource.Health).map { it.source })
    }

    @Test fun allLight() = snap("app_log", false) { Screen() }
    @Test fun allDark() = snap("app_log", true) { Screen() }
    @Test fun homeAssistantOnlyLight() = snap("app_log_home_assistant", false) { Screen(LogSource.HomeAssistant) }
}
