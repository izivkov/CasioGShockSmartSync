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
import org.avmedia.gshockGoogleSync.history.WorkoutRow
import org.avmedia.gshockGoogleSync.history.WorkoutSampleRow
import org.avmedia.gshockGoogleSync.history.WorkoutTestData
import org.avmedia.gshockGoogleSync.ui.health.history.WorkoutDetail
import org.avmedia.gshockGoogleSync.ui.health.history.WorkoutEvents
import org.avmedia.gshockGoogleSync.ui.health.history.WorkoutsContent
import org.avmedia.gshockGoogleSync.ui.health.history.WorkoutsUi
import org.junit.Test

/** The Workouts views: the "Last workout" card, the list, one opened workout with laps and charts, and a 25 s walk. */
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

    /** A 25 s walk without GPS: no distance (all zeros), heart rate only. */
    private val walk = WorkoutRow(
        WorkoutTestData.START, 9, "Walking", totalSec = 25, distance = 0.0, avgPace = 0, avgSpeed = 0.0, kcal = 2, hrAvg = 125,
        hrMax = 131, hrMin = 123, ascent = 0, descent = 0, cadenceAvg = 0,
    )
    private val walkSamples = List(6) { WorkoutSampleRow(WorkoutTestData.START, 9, it, elapsedSec = it * 5, pace = 0, hr = listOf(126, 129, 131, 127, 123, 119)[it]) }
    private val short = list.copy(workouts = listOf(walk) + card.workouts, selected = WorkoutDetail(walk, emptyList(), walkSamples))

    @Composable private fun Screen(ui: WorkoutsUi) =
        WorkoutsContent(ui, WorkoutEvents(), Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp))

    @Test fun listLight() = snap("workouts_list", false) { Screen(list) }
    @Test fun listDark() = snap("workouts_list", true) { Screen(list) }
    @Test fun detailLight() = snap("workouts_detail", false) { Screen(open) }
    @Test fun detailDark() = snap("workouts_detail", true) { Screen(open) }
    @Test fun shortLight() = snap("workouts_detail_short", false) { Screen(short) }
    @Test fun shortDark() = snap("workouts_detail_short", true) { Screen(short) }
    @Test fun cardLight() = snap("workouts_last_card", false) { Screen(card) }
    @Test fun cardDark() = snap("workouts_last_card", true) { Screen(card) }
}
