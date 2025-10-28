package org.avmedia.gshockapi

data class Settings(
    var hourlyChime: Boolean = false,
    var keyVibration: Boolean = false,
    var timeFormat: String = "",  // "12h" or "24h"
    var dateFormat: String = "",  // "DD:MM" or "MM:DD"
    var language: String = "",    // "English", "French", "Spanish", "German", "Italian" or "Russian"
    var autoLight: Boolean = false,
    var lightDuration: String = "", // "2s" or "4s"
    var powerSavingMode: Boolean = false,
    var buttonTone: Boolean = true,
    var timeAdjustment: Boolean = true,
    var adjustmentTimeMinutes: Int = 30,
    var fineAdjustment: Int = 0,
    var DnD: Boolean = false      // for always-connected watches only, like the ECB-30
)