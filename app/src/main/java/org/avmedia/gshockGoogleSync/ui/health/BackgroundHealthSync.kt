package org.avmedia.gshockGoogleSync.ui.health

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.webhook.HaGroup
import org.avmedia.gshockGoogleSync.webhook.ServiceToggles
import org.avmedia.gshockGoogleSync.webhook.WebhookStorage
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.io.H5600HealthIO
import org.avmedia.gshockapi.io.WatchExchange
import org.avmedia.gshockGoogleSync.ui.actions.WatchActions
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes

/**
 * Reads the DW-H5600 LifeLog by itself when the watch connects and then every period of the setting while it stays
 * connected (no Health screen needed).
 * The result goes to [H5600Session], which fires the health webhooks and the Home Assistant push.
 * Failures are only logged.
 */
@Singleton
class BackgroundHealthSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: GShockRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store = object : HealthReadStore {
        override var lastSuccessMs: Long
            get() = LocalDataStorage.get(context, KEY_OK)?.toLongOrNull() ?: 0L
            set(v) = LocalDataStorage.put(context, KEY_OK, v.toString())
        override var lastFailureMs: Long
            get() = LocalDataStorage.get(context, KEY_FAIL)?.toLongOrNull() ?: 0L
            set(v) = LocalDataStorage.put(context, KEY_FAIL, v.toString())
    }

    private val scheduler = HealthReadScheduler(
        store, System::currentTimeMillis, H5600Session.inFlight,
        intervalMs = { intervalMinutes(context) * 60_000L },
        busy = { WatchExchange.mutex.isLocked || H5600HealthIO.isActive || WatchActions.isRunning },
    )

    init {
        // An always-connected watch stays connected, so connections alone are rare: check every minute whether a read is due.
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (api.isConnected()) runIfDue()
            }
        }
    }

    /** Called when the session is Ready and when an always-connected watch asks for its actions. */
    fun onConnection() {
        scope.launch { runIfDue() }
    }

    private suspend fun runIfDue() {
        val outcome = scheduler.onConnection(WatchInfo.model == WatchInfo.WatchModel.DW_H5600, hasConsumer()) { read() }
        if (outcome == HealthReadScheduler.Outcome.NOT_DUE || outcome == HealthReadScheduler.Outcome.OFF) return
        Timber.i("Background health read: $outcome")
        if (outcome == HealthReadScheduler.Outcome.OK || outcome == HealthReadScheduler.Outcome.FAILED) {
            LocalDataStorage.put(context, KEY_LINE, "${LocalTime.now().withNano(0)} $outcome")
        }
    }

    private fun hasConsumer(): Boolean {
        val toggles = WebhookStorage.toggles(context)
        val ha = WebhookStorage.haStore(context)
        return HealthReadScheduler.hasConsumer(
            haConfigured = ha.load() != null,
            haOn = toggles.isOn(ServiceToggles.Service.HOME_ASSISTANT),
            haHealthGroupOn = HaGroup.HEALTH in ha.groupsOn(),
            webhooksOn = toggles.isOn(ServiceToggles.Service.WEBHOOKS),
            healthHookEnabled = WebhookStorage.loadHooks(context).any { it.enabled && it.onHealthData },
        )
    }

    private suspend fun read(): Boolean {
        val data = withTimeoutOrNull(3.minutes) { api.getStepCount() } ?: return false
        if (data.h5600 == null) return false
        H5600Session.store(data)
        return true
    }

    companion object {
        const val KEY_OK = "health_bg_last_ok_ms"
        const val KEY_FAIL = "health_bg_last_fail_ms"
        const val KEY_LINE = "health_bg_last_line"
        const val KEY_INTERVAL_MIN = "health_bg_interval_min"
        private const val TICK_MS = 60_000L

        /** The background read period in minutes (Settings), 0 = off; 15 when never set. */
        fun intervalMinutes(context: Context): Int =
            LocalDataStorage.get(context, KEY_INTERVAL_MIN)?.toIntOrNull()?.takeIf { it in HealthReadScheduler.CHOICES_MIN }
                ?: (HealthReadScheduler.DEFAULT_INTERVAL_MS / 60_000L).toInt()

        fun setIntervalMinutes(context: Context, minutes: Int) = LocalDataStorage.put(context, KEY_INTERVAL_MIN, minutes.toString())
    }
}
