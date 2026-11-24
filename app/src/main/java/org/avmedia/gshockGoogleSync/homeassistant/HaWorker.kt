package org.avmedia.gshockGoogleSync.homeassistant

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

/** Sends the pending Home Assistant job. A retryable failure runs again, up to [CallRetry.MAX_RUNS] runs. */
class HaWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = AppStores.haStore(applicationContext)
        val ids = store.pendingIds()
        if (ids.isEmpty()) return Result.failure()
        if (!AppStores.toggles(applicationContext).isOn(ServiceToggles.Service.HOME_ASSISTANT)) {
            ids.forEach { store.clearPending(it) } // switched off after this was queued: send nothing
            return Result.success()
        }
        val attempt = runAttemptCount + 1
        // Each watch's job goes to that watch's registration only; one failing watch does not hold back the others.
        var retry = false
        var ok = true
        for (id in ids) {
            val job = store.getPending(id)
            if (job == null) { store.clearPending(id); continue }
            val result = withContext(Dispatchers.IO) { HaClient(RetryingHaTransport(HaOkHttpTransport), store, mayRegister = { store.confirmedName(it) != null }).send(job) }
            val again = result is HaResult.Retry && attempt < CallRetry.MAX_RUNS
            // The message holds no URL and no token.
            Timber.d("Home Assistant attempt $attempt: ${result.message}")
            store.saveResult(id, "${result.message} at ${java.time.OffsetDateTime.now().withNano(0)}" + if (again) " (retrying)" else "", result is HaResult.Ok)
            if (result is HaResult.Ok) store.saveSent(job, System.currentTimeMillis())
            if (again) retry = true else store.clearPending(id)
            if (result !is HaResult.Ok) ok = false
        }
        if (retry) return Result.retry()
        return if (ok) Result.success() else Result.failure()
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
