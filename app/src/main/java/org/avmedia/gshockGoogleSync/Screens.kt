package org.avmedia.gshockGoogleSync

sealed class Screens(val route: String) {
    data object Time : Screens("Time")
    data object Health : Screens("Health")
    data object Alarms : Screens("Alarms")
    data object Events : Screens("Events")
    data object Actions : Screens("Actions")
    data object Settings : Screens("Settings")
    /** Not in the bottom bar: opened from the Actions screen. */
    data object Webhooks : Screens("Webhooks")
}