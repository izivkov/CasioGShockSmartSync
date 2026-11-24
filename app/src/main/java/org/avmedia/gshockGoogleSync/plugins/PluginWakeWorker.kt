package org.avmedia.gshockGoogleSync.plugins

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The host's one periodic job for [org.avmedia.smartsync.plugin.api.PluginNeeds.wakeEveryMinutes]: it runs also when
 * the app was stopped (the Application starts the plugin hub first), waits until the plugins switched on have started
 * and sends [org.avmedia.smartsync.plugin.api.WatchEvent.Wake] to those whose interval passed, then waits until they
 * handled it (60 s at most). Set while a plugin asks,
 * cancelled when none does.
 */
class PluginWakeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    fun interface HubEntryPoint {
        fun pluginHub(): PluginHub
    }

    override suspend fun doWork(): Result {
        val hub = EntryPointAccessors.fromApplication(applicationContext, HubEntryPoint::class.java).pluginHub()
        hub.start()
        withTimeoutOrNull(60_000) { hub.ready.await() }
        // Waits until the woken plugins handled the event: the job's hold on the process and the network ends here.
        val woken = hub.wakeAndWait()
        if (woken.isNotEmpty()) hub.log(CommandScheduler.HOST, "wake-up sent to ${woken.joinToString()}")
        return Result.success()
    }

    companion object {
        private const val NAME = "plugin-wake"

        /** Sets the job for [minutes] (null: cancels it). Android runs it every 15 minutes at most often. */
        fun schedule(context: Context, minutes: Int?) {
            val wm = WorkManager.getInstance(context)
            if (minutes == null) { wm.cancelUniqueWork(NAME); return }
            val every = minutes.coerceAtLeast(PluginRuntime.MIN_WAKE_MINUTES).toLong()
            val request = PeriodicWorkRequestBuilder<PluginWakeWorker>(every, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
