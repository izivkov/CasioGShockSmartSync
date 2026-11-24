package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.ZoneOffset
import org.avmedia.gshockGoogleSync.history.WorkoutMapper
import org.avmedia.gshockGoogleSync.history.WorkoutTestData
import org.avmedia.gshockGoogleSync.ui.health.history.WorkoutDetail
import org.avmedia.gshockGoogleSync.ui.health.history.WorkoutEvents
import org.avmedia.gshockGoogleSync.ui.health.history.WorkoutsContent
import org.avmedia.gshockGoogleSync.ui.health.history.WorkoutsUi
import org.junit.Test

/** The Workouts views: the "Last workout" card, the list, one opened workout with laps and charts. */
class WorkoutsScreenshotTest : ScreenshotTest() {
    private val sessions = listOf(
        WorkoutTestData.session(sport = 1, laps = 5, samples = 90),
        WorkoutTestData.session(start = WorkoutTestData.START - 2 * 86_400_000, sport = 8, kcal = null, distance = null),
        WorkoutTestData.session(start = WorkoutTestData.START - 5 * 86_400_000, sport = 11, hrAvg = null, distance = null),
    ).map { WorkoutMapper.map(it, 1L) }
    private val zone = ZoneOffset.UTC
    private val card = WorkoutsUi(workouts = sessions.map { it.workout }, zone = zone)
    private val list = card.copy(listOpen = true)
    private val open = list.copy(selected = WorkoutDetail(sessions[0].workout, sessions[0].laps, sessions[0].samples))

    @Composable private fun Screen(ui: WorkoutsUi) =
        WorkoutsContent(ui, WorkoutEvents(), Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp))

    @Test fun listLight() = snap("workouts_list", false) { Screen(list) }
    @Test fun listDark() = snap("workouts_list", true) { Screen(list) }
    @Test fun detailLight() = snap("workouts_detail", false) { Screen(open) }
    @Test fun detailDark() = snap("workouts_detail", true) { Screen(open) }
    @Test fun cardLight() = snap("workouts_last_card", false) { Screen(card) }
    @Test fun cardDark() = snap("workouts_last_card", true) { Screen(card) }
}
