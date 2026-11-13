package org.avmedia.gshockGoogleSync.session

/**
 * Tells whether a screen has unsent changes by comparing its current values with a baseline: the
 * values as last read from the watch, or as last sent to it.
 *
 * The session's pending-save mark for [component] is set while the current values differ from the
 * baseline and cleared when they are equal again, so changing a value and changing it back leaves
 * nothing pending. Values must have structural equality (data classes, lists, strings, numbers)
 * and must not be mutated in place after being handed over.
 */
class ChangeTracker<T : Any>(
    private val component: ComponentId,
    private val sessionManager: SessionManager,
) {
    private var baseline: T? = null

    @Synchronized
    fun baseline(): T? = baseline

    /** Sets the values to compare against and re-checks [current] against them. */
    @Synchronized
    fun setBaseline(newBaseline: T?, current: T) {
        baseline = newBaseline
        update(current)
    }

    /** Re-checks [current] against the baseline. Call after every change to the screen's values. */
    @Synchronized
    fun update(current: T) {
        if (current == baseline) {
            sessionManager.clearPendingSave(component)
        } else {
            sessionManager.markPendingSave(component)
        }
    }
}
