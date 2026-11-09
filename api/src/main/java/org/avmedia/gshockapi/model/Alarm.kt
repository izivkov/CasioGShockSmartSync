package org.avmedia.gshockapi.model

import androidx.annotation.Keep

@Keep
data class Alarm(
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val hasHourlyChime: Boolean = false,
    val oneTime: Boolean = false,
    val name: String? = null,
) {
    override fun toString(): String =
        "Alarm(hour=$hour, minute=$minute, enabled=$enabled, hasHourlyChime=$hasHourlyChime, oneTime=$oneTime, name=$name)"
}
