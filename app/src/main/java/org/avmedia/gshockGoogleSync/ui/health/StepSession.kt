package org.avmedia.gshockGoogleSync.ui.health

import CachedIO
import org.avmedia.gshockapi.model.StepCounterData

/**
 * The step data of the current connection for the watches that count steps themselves (the ABL-100): the last read that had
 * steps. It is kept in [CachedIO], so it lives as long as the connection (the library clears the cache when one is set up).
 * Opening the Health screen shows it at once; a read that fails or comes back empty never replaces it.
 */
internal object StepSession {
    private const val KEY = "STEP_COUNTER"

    /** The last read of this connection that had steps; null when there is none yet. */
    val data: StepCounterData?
        get() = CachedIO.getOrNull(KEY)

    /** Keeps [stepData] for the connection; a read without any step data is not kept. */
    fun store(stepData: StepCounterData) {
        if (!hasNoStepData(stepData)) CachedIO.put(KEY, stepData)
    }
}
