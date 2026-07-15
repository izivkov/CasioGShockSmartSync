package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import timber.log.Timber

object PhoneFinder {
    fun ring(context: Context): Result<Unit> = runCatching {
        Timber.d("PhoneFinder: ring() called, starting service")
        PhoneFinderService.start(context)
    }.onFailure { e ->
        Timber.e(e, "Failed to start PhoneFinderService")
    }

    fun stopRing(context: Context, reason: String = "Unknown") {
        Timber.d("PhoneFinder: stopRing() called. Reason: $reason")
        PhoneFinderService.stop(context)
    }
}
