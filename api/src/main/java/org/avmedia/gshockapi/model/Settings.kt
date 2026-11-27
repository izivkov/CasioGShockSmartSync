package org.avmedia.gshockapi.model

import androidx.annotation.Keep

@Keep
data class Settings(
    var hourlyChime: Boolean = false,
    var keyVibration: Boolean = false,
    var timeFormat: String = "", // "12h" or "24h"
    var dateFormat: String = "", // "DD:MM" or "MM:DD"
    var language: String =
        "", // "English", "French", "Spanish", "German", "Italian" or "Russian"
    var autoLight: Boolean = false,
    var lightDuration: String = "", // "2s" or "4s"
    var powerSavingMode: Boolean = false,
    var buttonTone: Boolean = true,
    var timeAdjustment: Boolean = true,
    var adjustmentTimeMinutes: Int = 30,
    var fineAdjustment: Int = 0,
    var DnD: Boolean = false, // for always-connected watches only, like the ECB-30
    var font: String = "Standard", // "Standard" or "Classic"
    var soundMode: String? = null, // "MUTE", "CUSTOM", or "VIBE_ONLY"
    var watchFaceNumber: Int? = null, // 1 or 2
    var watchFaceItem: String? = null, // e.g. "STEPS", "HEART_RATE"
    var smartAlarm: SmartAlarm? = null,
)
