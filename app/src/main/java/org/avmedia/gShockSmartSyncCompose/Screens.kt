package org.avmedia.gShockSmartSyncCompose

sealed class Screens(val route: String) {
    object Time : Screens("Time")
    object Alarms : Screens("Alarms")
    object Events : Screens("Events")
    object Actions : Screens("Actions")
    object Settings : Screens("Settings")
}