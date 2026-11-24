package org.avmedia.gshockGoogleSync.plugins

import org.avmedia.smartsync.plugin.api.Capability

/** What a watch model has, from the library's model table, as the plugin interface names it. Android-free. */
object Capabilities {
    /** The flags of the model table the mapping reads. */
    data class Model(
        val healthFamily: Boolean,
        val alwaysConnected: Boolean,
        val notifications: Boolean,
        val battery: Boolean,
        val temperature: Boolean,
        val stepCounter: Boolean,
        val homeTime: Boolean,
        val worldCities: Boolean,
        val timer: Boolean,
        val alarms: Boolean,
        val watchSettings: Boolean,
    )

    fun of(m: Model): Set<Capability> = buildSet {
        if (m.healthFamily) addAll(HEALTH_FAMILY)
        if (m.alwaysConnected) add(Capability.ALWAYS_CONNECTED)
        if (m.notifications) add(Capability.NOTIFICATIONS)
        if (m.battery) add(Capability.BATTERY)
        if (m.temperature) add(Capability.TEMPERATURE)
        if (m.stepCounter) add(Capability.STEPS)
        if (m.homeTime) add(Capability.HOME_TIME)
        if (m.worldCities) add(Capability.WORLD_CITIES)
        if (m.timer) add(Capability.TIMER)
        if (m.alarms) add(Capability.ALARMS)
        if (m.watchSettings) add(Capability.WATCH_SETTINGS)
    }

    /** The connected watch's capabilities from the library's model table; [notifications] as the link says. */
    fun connected(notifications: Boolean): Set<Capability> {
        val w = org.avmedia.gshockapi.WatchInfo
        return of(
            Model(
                healthFamily = org.avmedia.gshockGoogleSync.session.WatchModelOverride.isH5600Family(w.name) || w.model == org.avmedia.gshockapi.WatchInfo.WatchModel.DW_H5600,
                alwaysConnected = runCatching { w.alwaysConnected }.getOrDefault(false),
                notifications = notifications,
                battery = w.hasBatteryLevel, temperature = w.hasTemperature, stepCounter = w.hasStepCounter,
                homeTime = w.hasHomeTime, worldCities = w.hasWorldCities, timer = w.hasTimer,
                alarms = true, watchSettings = w.hasWatchSettings,
            ),
        )
    }

    /** The DW-H5600 family: the health log and what it carries. */
    val HEALTH_FAMILY = setOf(
        Capability.HEALTH_LOG, Capability.HEART_RATE, Capability.SLEEP, Capability.BLOOD_OXYGEN, Capability.WORKOUTS,
        Capability.HEALTH_SETTINGS, Capability.FIRMWARE_INFO,
    )

    fun names(caps: Set<Capability>): String = caps.map { it.name }.sorted().joinToString(",")

    fun parse(text: String?): Set<Capability> =
        text.orEmpty().split(',').mapNotNull { n -> Capability.entries.firstOrNull { it.name == n.trim() } }.toSet()
}

/** What a watch could do at its last connection, kept per watch by the plugin hub. */
object WatchCapabilities {
    /** Whether [watch] showed app notifications at its last connection; null before its first connection. */
    fun notificationsSupported(context: android.content.Context, watch: String): Boolean? {
        val store = org.avmedia.gshockGoogleSync.watch.AppStores.watchStore(context)
        store.get(watch, PluginHub.KEY_CAPS)?.value?.let { return org.avmedia.smartsync.plugin.api.Capability.NOTIFICATIONS in Capabilities.parse(it) }
        // Before its first connection with this version: what versions before the plugin interface kept.
        return store.get(watch, "ha.push.ok")?.value?.let { it == "yes" }
    }
}
