package org.avmedia.gshockGoogleSync

sealed class Screens(val route: String) {
    data object Time : Screens("Time")
    data object Health : Screens("Health")
    data object Alarms : Screens("Alarms")
    data object Events : Screens("Events")
    data object Actions : Screens("Actions")
    data object Settings : Screens("Settings")

    /** Reached from the watch and Actions screens, not from the bottom bar. */
    data object WatchFace : Screens("WatchFace")
    data object WatchHealth : Screens("WatchHealth")
    data object NotificationSettings : Screens("NotificationSettings")
    data object WorkoutSettings : Screens("WorkoutSettings")
    data object WorkoutActivity : Screens("WorkoutActivity/{activity}") {
        fun route(activity: Int) = "WorkoutActivity/$activity"
    }
}