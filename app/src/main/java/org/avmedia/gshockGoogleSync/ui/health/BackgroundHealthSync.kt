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
import org.avmedia.gshockGoogleSync.homeassistant.HaGroup
import org.avmedia.gshockGoogleSync.homeassistant.ServiceToggles
import org.avmedia.gshockGoogleSync.homeassistant.AppStores
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.io.H5600HealthIO
import org.avmedia.gshockapi.io.WatchExchange
import org.avmedia.gshockGoogleSync.ui.actions.WatchActions
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes
import org.avmedia.gshockGoogleSync.watch.CurrentWatch

/**
 * Reads the DW-H5600 LifeLog by itself when the watch connects and then every period of the setting while it stays
 * connected (no Health screen needed).
 * The result goes to [H5600Session], which starts the Home Assistant push.
 * Failures are only logged.
 */
@Singleton
class BackgroundHealthSync @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: GShockRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The last read and the failure back-off are the connected watch's own: another watch's failure never delays it.
    private val watches by lazy { AppStores.watchStore(context) }
    private val store = object : HealthReadStore {
        override var lastSuccessMs: Long
            get() = watches.get(CurrentWatch.key(), KEY_OK, KEY_OK)?.value?.toLongOrNull() ?: 0L
            set(v) = watches.put(CurrentWatch.key(), KEY_OK, v.toString())
        override var lastFailureMs: Long
            get() = watches.get(CurrentWatch.key(), KEY_FAIL, KEY_FAIL)?.value?.toLongOrNull() ?: 0L
            set(v) = watches.put(CurrentWatch.key(), KEY_FAIL, v.toString())
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
            watches.put(CurrentWatch.key(), KEY_LINE, "${LocalTime.now().withNano(0)} $outcome")
        }
    }

    private fun hasConsumer(): Boolean {
        val toggles = AppStores.toggles(context)
        val ha = AppStores.haStore(context)
        return HealthReadScheduler.hasConsumer(
            haConfigured = ha.load() != null,
            haOn = toggles.isOn(ServiceToggles.Service.HOME_ASSISTANT),
            haHealthGroupOn = HaGroup.HEALTH in ha.groupsOn(),
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

        /** The last background read of the connected (else the last) watch, for the Health screen. */
        fun lastLine(context: Context): String? =
            AppStores.watchStore(context).get(CurrentWatch.keyOrLast(context), KEY_LINE, KEY_LINE)?.value

        /** The background read period in minutes (Settings), 0 = off; 15 when never set. */
        fun intervalMinutes(context: Context): Int =
            LocalDataStorage.get(context, KEY_INTERVAL_MIN)?.toIntOrNull()?.takeIf { it in HealthReadScheduler.CHOICES_MIN }
                ?: (HealthReadScheduler.DEFAULT_INTERVAL_MS / 60_000L).toInt()

        fun setIntervalMinutes(context: Context, minutes: Int) = LocalDataStorage.put(context, KEY_INTERVAL_MIN, minutes.toString())
    }
}
