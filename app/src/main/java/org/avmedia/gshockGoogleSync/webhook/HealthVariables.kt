package org.avmedia.gshockGoogleSync.webhook

import org.avmedia.gshockapi.model.StepCounterData

/** Maps a decoded DW-H5600 LifeLog ([StepCounterData] with `h5600`) to the `health.*` variable values. */
object HealthVariables {
    /** Names of every health value, so the registry lists them even before any data exists. */
    val NAMES: List<String> = emptyList()

    val DESCRIPTIONS: Map<String, String> = emptyMap()

    fun values(data: StepCounterData?): Map<String, Any?> = emptyMap()
}
