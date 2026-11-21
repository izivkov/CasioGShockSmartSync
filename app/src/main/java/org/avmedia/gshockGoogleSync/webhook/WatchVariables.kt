package org.avmedia.gshockGoogleSync.webhook

import android.content.Context
import android.os.BatteryManager
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone
import org.avmedia.gshockGoogleSync.BuildConfig
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.io.IO

/**
 * THE list of webhook variables. To add one, add one [Variable] here: the Webhooks screen, the
 * `{{all}}` object and the templates pick it up. Names are dotted; the dots become nesting in
 * `{{all}}`, so a name must never also be the parent of another name.
 *
 * A value the connected watch does not support returns null and is simply left out.
 */
object WatchVariables {

    fun registry(
        appContext: Context,
        api: GShockRepository,
        features: IWatchFeatureManager,
    ): VariableRegistry {
        fun supported(feature: String) = features.isFeatureSupported(feature)

        suspend fun settings(ctx: VariableContext) = ctx.cached("settings") { api.getSettings() }
        suspend fun alarms(ctx: VariableContext) = ctx.cached("alarms") {
            api.getAlarms().take(features.getAlarmCount())
        }

        val list = mutableListOf(
            // Event
            Variable("event", "What triggered this call: watch_action, sync, health_data or test") { it.event },
            Variable("timestamp", "Time of the call, ISO 8601 with offset") { it.now.withNano(0).toString() },

            // Phone and app
            Variable("phone.battery", "Phone battery level, percent") {
                val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { p -> p in 0..100 }
            },
            Variable("phone.timezone", "Phone time zone id, for example Europe/Brussels") {
                ZoneId.systemDefault().id
            },
            Variable("phone.dst", "True while the phone is on daylight saving time") {
                TimeZone.getDefault().inDaylightTime(Date())
            },
            Variable("app.version", "Smart Sync app version") { BuildConfig.VERSION_NAME },
            Variable("app.last_sync", "When the app last connected to the watch, ISO 8601") {
                LocalDataStorage.get(appContext, WebhookStorage.KEY_LAST_SYNC)
            },

            // Watch
            Variable("watch.connected", "True when the watch is connected right now") { it.watchConnected },
            Variable("watch.name", "Watch model name, for example GW-B5600") {
                WatchInfo.name.takeIf { n -> n.isNotBlank() }
            },
            Variable("watch.model", "Watch model id used by the app", needsWatch = true) {
                WatchInfo.model.name
            },
            Variable("watch.battery", "Watch battery level, percent", needsWatch = true) {
                if (supported("time.battery")) api.getBatteryLevel() else null
            },
            Variable("watch.temperature", "Temperature from the watch sensor, degrees Celsius", needsWatch = true) {
                if (supported("time.temperature")) api.getWatchTemperature() else null
            },
            Variable("watch.home_city", "Home time city set on the watch", needsWatch = true) {
                if (supported("time.home_time")) api.getHomeTime() else null
            },
            Variable("watch.dst.home", "Daylight saving state of the home time", needsWatch = true) {
                if (supported("time.home_time")) api.getDSTWatchState(IO.DstState.ZERO) else null
            },
            Variable("watch.timer_seconds", "Countdown timer set on the watch, seconds", needsWatch = true) {
                api.getTimer()
            },
            Variable("health.steps_today", "Steps counted today (watches with a step counter)", needsWatch = true) {
                if (supported("time.step_counter")) api.getStepCount().currentDaySteps else null
            },

            // Watch settings
            Variable("watch.settings.time_format", "12h or 24h", needsWatch = true) {
                if (supported("locale.time_format")) settings(it).timeFormat.ifBlank { null } else null
            },
            Variable("watch.settings.date_format", "DD:MM or MM:DD", needsWatch = true) {
                if (supported("locale.date_format")) settings(it).dateFormat.ifBlank { null } else null
            },
            Variable("watch.settings.language", "Day-of-week language of the watch", needsWatch = true) {
                if (supported("locale.week_language")) settings(it).language.ifBlank { null } else null
            },
            Variable("watch.settings.auto_light", "Auto light on or off", needsWatch = true) {
                if (supported("light.auto_light")) settings(it).autoLight else null
            },
            Variable("watch.settings.light_duration", "Light duration, for example 2s or 4s", needsWatch = true) {
                settings(it).lightDuration.ifBlank { null }
            },
            Variable("watch.settings.power_saving", "Power saving mode on or off", needsWatch = true) {
                if (supported("settings.power_saving")) settings(it).powerSavingMode else null
            },
            Variable("watch.settings.button_tone", "Button tone on or off", needsWatch = true) {
                settings(it).buttonTone
            },
            Variable("watch.settings.key_vibration", "Key vibration on or off", needsWatch = true) {
                if (supported("operation_tone.vibrate")) settings(it).keyVibration else null
            },
            Variable("watch.settings.hourly_chime", "Hourly chime on or off", needsWatch = true) {
                if (supported("alarms.chime")) settings(it).hourlyChime else null
            },
            Variable("watch.settings.do_not_disturb", "Do not disturb (always-connected watches)", needsWatch = true) {
                if (supported("time_adjustment.always_connected")) settings(it).DnD else null
            },
            Variable("watch.settings.font", "Display font, Standard or Classic", needsWatch = true) {
                if (supported("settings.multiple_fonts")) settings(it).font else null
            },
            Variable("watch.settings.time_adjustment", "Automatic time adjustment on or off", needsWatch = true) {
                if (supported("time_adjustment.supported")) settings(it).timeAdjustment else null
            },
            Variable("watch.settings.adjustment_minutes", "Minutes between automatic time adjustments", needsWatch = true) {
                if (supported("time_adjustment.supported")) settings(it).adjustmentTimeMinutes else null
            },
            Variable("watch.settings.fine_adjustment", "Fine time adjustment", needsWatch = true) {
                if (supported("time_adjustment.supported")) settings(it).fineAdjustment else null
            },

            // Alarms
            Variable("alarms.enabled_count", "Number of alarms that are switched on", needsWatch = true) {
                alarms(it).count { a -> a.enabled }
            },
            Variable("alarms.list", "All alarms: time, enabled, hourly_chime, one_time", needsWatch = true) {
                alarms(it).map { a ->
                    mapOf(
                        "time" to "%02d:%02d".format(a.hour, a.minute),
                        "enabled" to a.enabled,
                        "hourly_chime" to a.hasHourlyChime,
                        "one_time" to a.oneTime,
                    )
                }
            },
        )

        // World cities 1 to 5 (name only).
        for (i in 1..5) {
            list += Variable("watch.world_city.$i", "Name of world city $i on the watch", needsWatch = true) {
                if (supported("time.world_cities")) api.getWorldCities(i).ifBlank { null } else null
            }
        }
        return VariableRegistry(list)
    }
}
