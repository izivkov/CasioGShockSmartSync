package org.avmedia.gshockapi

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object TimerModel {
    private var inSeconds = 0

    @RequiresApi(Build.VERSION_CODES.O)
    fun set(time: String) {
        inSeconds = try {
            val time1 = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm:ss"))
            time1.hour * 3600 + time1.minute * 60 + time1.second
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