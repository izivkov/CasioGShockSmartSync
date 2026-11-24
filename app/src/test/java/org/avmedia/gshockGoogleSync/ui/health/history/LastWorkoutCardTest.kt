package org.avmedia.gshockGoogleSync.ui.health.history

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.ZoneOffset
import org.avmedia.gshockGoogleSync.history.WorkoutMapper
import org.avmedia.gshockGoogleSync.history.WorkoutTestData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Workouts on the Health screen: one "Last workout" card once one is stored, a tap opens the list; no read button or status text. */
@RunWith(RobolectricTestRunner::class)
class LastWorkoutCardTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val stored = listOf(WorkoutMapper.map(WorkoutTestData.session(), 1L).workout)

    private fun showWith(workouts: List<org.avmedia.gshockGoogleSync.history.WorkoutRow>) = rule.setContent {
        var ui by remember { mutableStateOf(WorkoutsUi(workouts = workouts, zone = ZoneOffset.UTC)) }
        WorkoutsContent(ui, WorkoutEvents(onOpenList = { open -> ui = ui.copy(listOpen = open) }))
    }

    @Test fun nothingIsShownBeforeAWorkoutIsStored() {
        showWith(emptyList())
        assertEquals(0, rule.onAllNodesWithText("Last workout").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Workouts").fetchSemanticsNodes().size)
    }

    @Test fun theCardShowsTheLastWorkoutAndATapOpensTheList() {
        showWith(stored)
        rule.onNodeWithText("Last workout").assertIsDisplayed()
        rule.onNodeWithText("Running").assertIsDisplayed()
        rule.onNodeWithText("30:00, 410 kcal, avg 142 bpm, max 171 bpm").assertIsDisplayed()
        for (gone in listOf("Read workouts", "Share raw", "Stopped", "unconfirmed are the likely")) {
            assertEquals(gone, 0, rule.onAllNodesWithText(gone, substring = true).fetchSemanticsNodes().size)
        }
        rule.onNodeWithText("Last workout").performClick()
        rule.onNodeWithText("Workouts").assertIsDisplayed()
        rule.onNodeWithText("Export CSV").assertIsDisplayed()
    }
}
