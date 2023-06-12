package org.avmedia.gshockapi

import org.avmedia.gshockapi.casio.AlarmDecoder
import org.json.JSONObject

open class Alarm(
    var hour: Int,
    var minute: Int,
    var enabled: Boolean,
    var hasHourlyChime: Boolean = false
) {
    override fun toString(): String {
        return "Alarm(hour=$hour, minute=$minute, enabled=$enabled, hasHourlyChime=$hasHourlyChime)"
    }

    companion object {
        val alarms: ArrayList<Alarm> = ArrayList<Alarm>()

        fun clear() {
            alarms.clear()
        }
    }
}