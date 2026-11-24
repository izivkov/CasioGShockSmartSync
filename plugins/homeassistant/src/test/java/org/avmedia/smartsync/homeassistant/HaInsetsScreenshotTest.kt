package org.avmedia.smartsync.homeassistant

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app draws edge to edge and the Home Assistant title sat under the status bar.
 * Once registered the name field is read-only and says "fixed at registration".
 * A 40 dp status bar is given to the screen; the title must start below it. The grey strip in the picture is that bar.
 */
class HaInsetsScreenshotTest : ScreenshotTest() {
    private val barDp = 40

    @Test
    fun titleStartsBelowTheStatusBar() {
        var view: View? = null
        var barPx = 0
        composeRule.setContent {
            view = LocalView.current
            barPx = with(LocalDensity.current) { barDp.dp.roundToPx() }
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        HaScreenContent(
                            HaUi(
                                form = HaConfig("https://ha.example.com", "secret-token", "GBX-H5600KI-5ER", "GBX-H5600"),
                                watch = HaWatchUi(registered = true, firmware = "1.2"),
                            )
                        )
                        Box(Modifier.fillMaxWidth().height(barDp.dp).background(Color(0x80808080)))
                    }
                }
            }
        }
        composeRule.runOnIdle {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, barPx, 0, 0))
                .setVisible(WindowInsetsCompat.Type.statusBars(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(view!!, insets)
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/ha_status_bar_inset_light.png")
        val top = composeRule.onAllNodesWithText("Home Assistant", substring = true).onFirst().fetchSemanticsNode().boundsInRoot.top
        assertTrue("title top $top px, status bar $barPx px", top >= barPx)
        // Once registered the name cannot change, so the field says so (and is read-only).
        composeRule.onAllNodesWithText("fixed at registration", substring = true).onFirst().assertExists()
    }
}
