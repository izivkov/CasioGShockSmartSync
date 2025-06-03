package org.avmedia.gshockapi

data class Alarm(
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val hasHourlyChime: Boolean = false
) {
    override fun toString(): String =
        "Alarm(hour=$hour, minute=$minute, enabled=$enabled, hasHourlyChime=$hasHourlyChime)"

    companion object {
        private var alarms = ArrayList<Alarm>()

        fun addSorted(source: Array<Alarm>) {
            val position = if (source.size == 1) 0 else alarms.size
            alarms.addAll(position, source.toList())
        }

        fun clear() {
            alarms.clear()
        }

        fun getAlarms(): ArrayList<Alarm> = alarms
    }
}
