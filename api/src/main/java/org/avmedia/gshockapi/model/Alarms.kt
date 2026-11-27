package org.avmedia.gshockapi.model

import androidx.annotation.Keep

@Keep
object Alarms {
    const val HOURLY_CHIME_MASK = 0b10000000
    const val ENABLED_MASK = 0b01000000
    const val ONE_TIME_MASK = 0b00100000
    const val ALARM_CONSTANT_VALUE = 0x40
}
