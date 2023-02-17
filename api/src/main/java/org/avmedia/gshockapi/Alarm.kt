package org.avmedia.gshockapi

open class Alarm(
    var hour: Int,
    var minute: Int,
    var enabled: Boolean,
    var hasHourlyChime: Boolean = false
) {
    override fun toString(): String {
        return "Alarm(hour=$hour, minute=$minute, enabled=$enabled, hasHourlyChime=$hasHourlyChime)"
    }
}