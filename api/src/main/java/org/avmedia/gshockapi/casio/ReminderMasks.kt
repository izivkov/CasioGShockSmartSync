package org.avmedia.gshockapi.casio

class ReminderMasks {
    companion object {
        const val YEARLY_MASK = 0b00001000
        const val MONTHLY_MASK = 0b00010000
        const val WEEKLY_MASK = 0b00000100

        const val SUNDAY_MASK = 0b00000001
        const val MONDAY_MASK = 0b00000010
        const val TUESDAY_MASK = 0b00000100
        const val WEDNESDAY_MASK = 0b00001000
        const val THURSDAY_MASK = 0b00010000
        const val FRIDAY_MASK = 0b00100000
        const val SATURDAY_MASK = 0b01000000

        const val ENABLED_MASK = 0b00000001
    }
}
