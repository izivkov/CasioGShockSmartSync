package org.avmedia.gshockGoogleSync.webhook

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Sends the pending Home Assistant job. Retries like [WebhookWorker]: up to [WebhookPolicy.MAX_ATTEMPTS] tries. */
class HaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = WebhookStorage.haStore(applicationContext)
        val job = store.getPending() ?: return Result.failure()
        if (!WebhookStorage.toggles(applicationContext).isOn(ServiceToggles.Service.HOME_ASSISTANT)) {
            store.clearPending() // switched off after this was queued: send nothing
            return Result.success()
        }
        val attempt = runAttemptCount + 1
        val result = withContext(Dispatchers.IO) { HaClient(HaOkHttpTransport, store).send(job) }
        val retry = result is HaResult.Retry && attempt < WebhookPolicy.MAX_ATTEMPTS
        // The message holds no URL and no token.
        Timber.d("Home Assistant attempt $attempt: ${result.message}")
        store.saveResult("${result.message} at ${java.time.OffsetDateTime.now().withNano(0)}" + if (retry) " (retrying)" else "", result is HaResult.Ok)
        if (result is HaResult.Ok) store.saveSent(job.states, System.currentTimeMillis())
        if (retry) return Result.retry()
        store.clearPending()
        return if (result is HaResult.Ok) Result.success() else Result.failure()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<HaWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("home-assistant", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
