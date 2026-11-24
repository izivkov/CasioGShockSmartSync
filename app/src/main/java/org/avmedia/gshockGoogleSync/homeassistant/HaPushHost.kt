package org.avmedia.gshockGoogleSync.homeassistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.avmedia.gshockapi.AppNotification

/**
 * Owns the push socket for the whole process, not an Activity or a ViewModel. It runs while at least one owner holds
 * it: the companion foreground service (`"service"`) and a ready watch session (`"session"`). It has its own scope,
 * so a screen going away never ends it. [networkChanged] reconnects at once; [watchConnected] cuts the wait.
 */
class HaPushHost(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val startChannel: (CoroutineScope) -> HostedChannel,
) {
    /** What the host needs from the socket: [run] until cancelled, [wake] to cut the wait. */
    class HostedChannel(val run: suspend () -> Unit, val wake: (drop: Boolean) -> Unit)

    private val owners = HashSet<String>()
    private var job: Job? = null
    private var channel: HostedChannel? = null

    @get:Synchronized val running: Boolean get() = job?.isActive == true

    @Synchronized fun acquire(owner: String) {
        owners += owner
        if (job?.isActive == true) return
        val ch = startChannel(scope)
        channel = ch
        job = scope.launch { ch.run() }
    }

    @Synchronized fun release(owner: String) {
        owners -= owner
        if (owners.isNotEmpty()) return
        job?.cancel()
        job = null
        channel = null
    }

    @Synchronized fun networkChanged() { ensureRunning(); channel?.wake(true) }

    @Synchronized fun watchConnected() { ensureRunning(); channel?.wake(false) }

    /** An owner holds the host but the socket's job ended (a crash): start it again. */
    private fun ensureRunning() {
        if (owners.isEmpty() || job?.isActive == true) return
        val ch = startChannel(scope)
        channel = ch
        job = scope.launch { ch.run() }
    }

    companion object {
        const val SERVICE = "service"
        const val SESSION = "session"

        /** Set by MainEventHandler: sends a Home Assistant message to the watch. */
        @Volatile var deliver: (AppNotification) -> Unit = {}

        @Volatile private var shared: HaPushHost? = null

        fun get(context: Context): HaPushHost = shared ?: synchronized(this) {
            shared ?: create(context.applicationContext).also { shared = it }
        }

        private fun create(app: Context): HaPushHost {
            val host = HaPushHost { _ ->
                val store = AppStores.haStore(app)
                val toggles = AppStores.toggles(app)
                val catchUp = HaCatchUp(RetryingHaTransport(HaOkHttpTransport), store, log = { org.avmedia.gshockGoogleSync.log.AppLog.log(org.avmedia.gshockGoogleSync.log.LogSource.App, it) })
                val ch = HaPushChannel(
                    store,
                    deliver = { n -> deliver(n) },
                    enabled = { toggles.isOn(ServiceToggles.Service.HOME_ASSISTANT) },
                    catchUp = { id, since, at -> catchUp.run(id, since, at) },
                )
                HostedChannel(
                    run = {
                        ch.run {
                            val address = runCatching { org.avmedia.gshockapi.WatchInfo.getAddress() }.getOrDefault("")
                            if (address.isNotBlank()) HaPayloads.deviceId(address) else store.registeredIds().lastOrNull().orEmpty()
                        }
                    },
                    wake = ch::wake,
                )
            }
            watchNetwork(app) { host.networkChanged() }
            return host
        }

        /** A new default network (wifi to mobile, back online) is a reason to reconnect at once. The first call at registration is skipped. */
        private fun watchNetwork(app: Context, onChange: () -> Unit) {
            val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
            var first = true
            runCatching {
                cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (first) { first = false; return }
                        onChange()
                    }
                })
            }
        }
    }
}
