package org.avmedia.gshockGoogleSync.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base for the screenshot tests. They only RECORD pictures (task recordRoborazziGithubDebug); they never
 * compare, so a changed screen does not fail the build. Each screen has its own test file: extend this,
 * and call [snap] once per test method (one for light, one for dark).
 *
 * Phone size 412x915 dp. Pictures land in app/build/outputs/roborazzi/<name>_<light|dark>.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-420dpi")
abstract class ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Shows [content] in the app theme, light or dark, full screen, and records `<name>_<theme>.png`. */
    protected fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent { ThemedFrame(dark, content) }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_${if (dark) "dark" else "light"}.png")
    }
}

@Composable
private fun ThemedFrame(dark: Boolean, content: @Composable () -> Unit) {
    val base = LocalConfiguration.current
    val config = Configuration(base).apply {
        val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
    }
    CompositionLocalProvider(LocalConfiguration provides config) {
        GShockSmartSyncTheme(darkTheme = dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
        }
    }
}
