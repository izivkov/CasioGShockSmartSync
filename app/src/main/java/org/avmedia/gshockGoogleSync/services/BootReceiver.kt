package org.avmedia.gshockGoogleSync.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        handleIntent(context, intent)
            .onSuccess { /* Success case handled silently */ }
            .onFailure { error ->
                Timber.e("Failed to handle boot: ${error.message}")
            }
    }

    private fun handleIntent(context: Context, intent: Intent): Result<Unit> = runCatching {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> handleBootCompleted(context)
            else -> Unit
        }
    }

    private fun handleBootCompleted(context: Context) {
    }
}
