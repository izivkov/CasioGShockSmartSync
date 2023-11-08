package org.avmedia.gShockPhoneSync.ui.time

import java.time.LocalTime
import java.time.format.DateTimeFormatter

object TimerModel {
    private var inSeconds = 0

    fun set(time: String) {
        inSeconds = try {
            val localTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm:ss"))
            localTime.hour * 3600 + localTime.minute * 60 + localTime.second
        } catch (e: Error) {
            0
        }
    }

    fun set(time: Int) {
        inSeconds = time
    }

    fun get(): Int {
        return inSeconds
    }
}