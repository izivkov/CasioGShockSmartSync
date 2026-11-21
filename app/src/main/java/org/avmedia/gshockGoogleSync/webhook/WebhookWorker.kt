package org.avmedia.gshockGoogleSync.webhook

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Sends the pending body of one webhook. Runs once the network is there; a failed send that is
 * worth repeating (offline, 5xx) is retried by WorkManager, up to [WebhookPolicy.MAX_ATTEMPTS] tries.
 */
class WebhookWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val hookId = inputData.getString(KEY_HOOK_ID) ?: return Result.failure()
        val hook = WebhookStorage.loadHooks(applicationContext).firstOrNull { it.id == hookId }
            ?: return Result.failure()
        val body = WebhookStorage.getPending(applicationContext, hookId) ?: return Result.failure()

        val attempt = runAttemptCount + 1
        val outcome = withContext(Dispatchers.IO) { WebhookSender.send(hook, body) }
        // Host only: the URL query and headers may hold secrets.
        Timber.d("Webhook '${hook.name}' attempt $attempt: status=${outcome.status}")

        val retry = !outcome.ok && WebhookPolicy.isRetryable(outcome.status) &&
            attempt < WebhookPolicy.MAX_ATTEMPTS
        WebhookStorage.saveResult(
            applicationContext,
            hookId,
            WebhookResult(
                timeMillis = System.currentTimeMillis(),
                status = outcome.status,
                ok = outcome.ok,
                message = outcome.message,
                attempts = attempt,
                retrying = retry,
            ),
        )
        if (retry) return Result.retry()
        WebhookStorage.clearPending(applicationContext, hookId)
        return if (outcome.ok) Result.success() else Result.failure()
    }

    companion object {
        private const val KEY_HOOK_ID = "hookId"

        fun enqueue(context: Context, hookId: String) {
            val request = OneTimeWorkRequestBuilder<WebhookWorker>()
                .setInputData(Data.Builder().putString(KEY_HOOK_ID, hookId).build())
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // A newer payload for the same webhook replaces a waiting older one.
            WorkManager.getInstance(context)
                .enqueueUniqueWork("webhook-$hookId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
