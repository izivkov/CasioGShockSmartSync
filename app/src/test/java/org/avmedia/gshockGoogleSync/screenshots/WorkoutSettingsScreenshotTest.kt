package org.avmedia.gshockGoogleSync.screenshots

import androidx.compose.runtime.Composable
import io.mockk.mockk
import org.avmedia.gshockGoogleSync.ui.watchhealth.WorkoutActivityContent
import org.avmedia.gshockGoogleSync.ui.watchhealth.WorkoutActivityUi
import org.avmedia.gshockGoogleSync.ui.watchhealth.WorkoutListUi
import org.avmedia.gshockGoogleSync.ui.watchhealth.WorkoutSettingsListContent
import org.avmedia.gshockapi.io.HeartRateFlagsCodec
import org.avmedia.gshockapi.io.HeartRateSettingCodec
import org.avmedia.gshockapi.io.RunMeasurementCodec
import org.avmedia.gshockapi.io.TrainingAlertCodec
import org.junit.Test

/** Activity settings: the list of activities, one activity with everything read, and an activity the watch did not answer for. */
class WorkoutSettingsScreenshotTest : ScreenshotTest() {
    private val running = WorkoutActivityUi(
        activity = 1,
        run = RunMeasurementCodec.Setting(true, false, true, 1000, 3),
        heartRate = HeartRateSettingCodec.Setting(1, 2, 120, 150),
        heartRateFlags = HeartRateFlagsCodec.Flags(true, false, true),
        alerts = TrainingAlertCodec.Setting(300, 600, 1, 1000, 2000, 2, 50, 0, 100),
        formAlert = "01 02 03 00",
        screens = listOf(listOf(2, 3, 7), listOf(11, 19, 0)),
        message = "Auto pause saved and read back from the watch.",
    )

    @Composable private fun Activity(ui: WorkoutActivityUi) = WorkoutActivityContent(ui, {}, mockk(relaxed = true))

    @Test fun listLight() = snap("workout_settings_list", false) { WorkoutSettingsListContent(WorkoutListUi(activities = listOf(1, 2, 3, 6, 7, 8, 9, 12, 13, 14)), {}, {}, {}) }
    @Test fun listDark() = snap("workout_settings_list", true) { WorkoutSettingsListContent(WorkoutListUi(activities = listOf(1, 2, 3, 6, 7)), {}, {}, {}) }
    /** The watch also reports Biking (3) and Pool (7): hidden, never shown, only the four of the manual are offered. */
    @Test fun listHiddenCodesLight() = snap("workout_settings_list_hidden", false) { WorkoutSettingsListContent(WorkoutListUi(activities = listOf(1, 3, 2, 7, 14)), {}, {}, {}) }
    @Test fun listNoAnswerLight() = snap("workout_settings_list_no_answer", false) {
        WorkoutSettingsListContent(WorkoutListUi(message = "The watch did not answer. Connect the watch and try again."), {}, {}, {})
    }
    @Test fun listRawLight() = snap("workout_settings_list_raw", false) {
        WorkoutSettingsListContent(WorkoutListUi(message = "The activity list is not in the layout the app knows. It will not be changed.", rawHex = "53 01 4D 00"), {}, {}, {})
    }
    @Test fun activityLight() = snap("workout_settings_activity", false) { Activity(running) }
    @Test fun activityDark() = snap("workout_settings_activity", true) { Activity(running) }
    @Test fun activityNoAnswerLight() = snap("workout_settings_activity_no_answer", false) {
        Activity(WorkoutActivityUi(activity = 7, message = "The watch did not answer for this activity."))
    }
}
