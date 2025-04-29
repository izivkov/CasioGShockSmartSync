package org.avmedia.gshockapi

class Settings {
    var hourlyChime: Boolean = false

    var keyVibration: Boolean = false

    /**
     * "12h" or "24h"
     */
    var timeFormat = ""

    /**
     * "DD:MM" or "MM:DD"
     */
    var dateFormat = ""

    /*
    "English", "French", "Spanish", "German", "Italian" or "Russian"
     */
    var language = ""

    /**
     * true or false
     */
    var autoLight = false

    /**
     * "2s" or "4s"
     */
    var lightDuration = ""

    /**
     * true or false
     */
    var powerSavingMode = false

    /**
     * true or false
     */
    var buttonTone = true

    /**
     * true or false
     */
    var timeAdjustment = true

    var adjustmentTimeMinutes = 30

    var fineAdjustment = 0

    // for always-connected watches only, like the ECB-30
    var DnD = false
}