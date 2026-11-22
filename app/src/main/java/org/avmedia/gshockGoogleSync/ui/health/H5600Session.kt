package org.avmedia.gshockGoogleSync.ui.health

import org.avmedia.gshockapi.model.StepCounterData

/**
 * The DW-H5600 LifeLog result, kept for the life of the app session so that opening the Health
 * screen does not start a new watch transfer. Cleared only by [clear].
 */
internal object H5600Session {
    /** Called when a LifeLog read has just been stored (the "health data downloaded" webhook trigger). */
    @Volatile
    var onDownloaded: (() -> Unit)? = null

    @Volatile
    var data: StepCounterData? = null
        set(value) {
            field = value
            if (value != null) onDownloaded?.invoke()
        }

    fun clear() {
        data = null
    }
}
