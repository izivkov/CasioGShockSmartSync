package org.avmedia.smartsync.homeassistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    /** Ends the socket whoever holds it (the plugin was switched off). */
    @Synchronized fun stop() {
        owners.clear()
        job?.cancel()
        job = null
        channel = null
        networkCallback?.let { cb -> runCatching { connectivity?.unregisterNetworkCallback(cb) } }
        networkCallback = null
    }

    private var connectivity: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        const val SERVICE = "service"
        const val SESSION = "session"

        fun create(rt: HaRuntime): HaPushHost {
            val store = rt.store
            val host = HaPushHost(rt.scope) { _ ->
                val catchUp = HaCatchUp(RetryingHaTransport(HaOkHttpTransport), store, applySettings = { rt.changeSettings(mapOf("settings" to it)) },
                    log = { AppLog.log(LogSource.App, it) })
                val ch = HaPushChannel(
                    store,
                    deliver = rt::deliver,
                    enabled = { HaRuntime.active === rt },
                    catchUp = { id, since, at -> catchUp.run(id, since, at) },
                    connected = { rt.host.current()?.key.orEmpty() },
                    updateSensors = rt::pushSensorsNow,
                    watchSetting = rt::changeSettings,
                )
                HostedChannel(
                    run = {
                        // The current watch first, else the last registered one; every other registered watch is listened
                        // to as well, so its messages are dropped with a line instead of reaching the wrong watch.
                        ch.run(others = { store.registeredIds() }) {
                            rt.host.current()?.key?.takeIf { store.loadRegistration(it) != null }
                                ?: store.registeredIds().lastOrNull().orEmpty()
                        }
                    },
                    wake = ch::wake,
                )
            }
            host.watchNetwork(rt.host.context) { host.networkChanged() }
            return host
        }
    }

    /** A new default network (wifi to mobile, back online) is a reason to reconnect at once. The first call at registration is skipped. */
    private fun watchNetwork(app: Context, onChange: () -> Unit) {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        var first = true
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (first) { first = false; return }
                onChange()
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb); connectivity = cm; networkCallback = cb }
    }
}
