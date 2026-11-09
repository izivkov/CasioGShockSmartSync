package org.avmedia.gshockGoogleSync.session

enum class ConnectionTrigger {
    NORMAL,             // Normal button connection (LOWER-LEFT long-press)
    ACTION_BUTTON,      // Action button pressed (LOWER-RIGHT short-press)
    AUTO_TIME,          // Scheduled background time update
    FIND_PHONE,         // Find Phone pressed on watch
    ALWAYS_CONNECTED    // Periodic connection for always-connected watch
}
