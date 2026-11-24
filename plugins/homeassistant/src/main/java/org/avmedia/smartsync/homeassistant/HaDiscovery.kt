package org.avmedia.smartsync.homeassistant

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/** A Home Assistant found on the local network. */
data class HaFound(val name: String, val url: String)

/** Looks for `_home-assistant._tcp` with Android's NsdManager (no permission needed). One search at a time. */
class HaDiscovery(context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var listener: NsdManager.DiscoveryListener? = null
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    /** False when this phone has no NSD. [onFound] and [onFailed] come on a system thread. */
    fun start(onFound: (HaFound) -> Unit, onFailed: () -> Unit): Boolean {
        val manager = nsd ?: return false
        stop()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = onFailed()
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                synchronized(queue) { queue.addLast(service) }
                resolveNext(manager, onFound)
            }
        }
        listener = l
        return runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }.isSuccess
    }

    fun stop() {
        val l = listener ?: return
        listener = null
        synchronized(queue) { queue.clear() }
        runCatching { nsd?.stopServiceDiscovery(l) }
    }

    // The older resolve call takes one request at a time, so they wait in a queue.
    @Suppress("DEPRECATION")
    private fun resolveNext(manager: NsdManager, onFound: (HaFound) -> Unit) {
        val next = synchronized(queue) {
            if (resolving) return
            queue.removeFirstOrNull()?.also { resolving = true } ?: return
        }
        fun done() { synchronized(queue) { resolving = false }; resolveNext(manager, onFound) }
        val ok = runCatching {
            manager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = done()
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress
                    if (host != null) {
                        val base = serviceInfo.attributes["base_url"]?.let { String(it) }
                        onFound(HaFound(serviceInfo.serviceName, urlOf(host, serviceInfo.port, base)))
                    }
                    done()
                }
            })
        }.isSuccess
        if (!ok) done()
    }

    companion object {
        const val SERVICE_TYPE = "_home-assistant._tcp."

        /** The address HA advertises itself (base_url), else http://host:port. */
        fun urlOf(host: String, port: Int, baseUrl: String?): String =
            baseUrl?.trim()?.trimEnd('/')?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: "http://${if (host.contains(':')) "[$host]" else host}:$port"
    }
}
