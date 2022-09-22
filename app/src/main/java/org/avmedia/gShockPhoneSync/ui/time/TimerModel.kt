package org.avmedia.gShockPhoneSync.ui.time

import java.time.LocalTime
import java.time.format.DateTimeFormatter

object TimerModel {
    var inSeconds = 0

    fun set(time: String) {
        inSeconds = try {
            val time = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm:ss"))
            time.hour * 3600 + time.minute * 60 + time.second
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