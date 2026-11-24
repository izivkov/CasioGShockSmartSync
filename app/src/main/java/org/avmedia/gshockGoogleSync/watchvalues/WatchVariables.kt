package org.avmedia.gshockGoogleSync.watchvalues

import org.avmedia.gshockGoogleSync.homeassistant.HaPushState
import org.avmedia.gshockGoogleSync.watch.AppStores
import android.content.Context
import android.os.BatteryManager
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone
import org.avmedia.gshockGoogleSync.session.WatchModelOverride
import org.avmedia.gshockGoogleSync.BuildConfig
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockapi.IGShockAPI
import org.avmedia.gshockapi.model.StepCounterData
import org.avmedia.gshockGoogleSync.history.WorkoutRow
import org.avmedia.gshockGoogleSync.history.WorkoutStore
import org.avmedia.gshockGoogleSync.ui.health.H5600Session
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.io.IO

/**
 * THE list of variables the Home Assistant sensors read ([HaSensors]). To add one, add one [Variable] here.
 * Names are dotted (`watch.*`, `health.*`, `app.*`).
 *
 * A value the connected watch does not support returns null and is simply left out.
 */
object WatchVariables {

    /** What the variables read from. [androidEnv] is the real one; unit tests pass fakes. */
    class Env(
        val api: IGShockAPI,
        val features: IWatchFeatureManager,
        val raw: RawRequester,
        val isH5600: () -> Boolean,
        val phoneBattery: () -> Int?,
        val lastSync: () -> String?,
        /** The last DW-H5600 LifeLog transfer, or null when none was read this session. */
        val healthData: () -> StepCounterData? = { null },
        /** The newest stored DW-H5600 workout (any app session), or null. */
        val lastWorkout: suspend () -> WorkoutRow? = { null },
        /** The "Read raw watch settings" switch; off reads only the decoded codes. */
        val rawOn: () -> Boolean = { false },
    )

    fun androidEnv(appContext: Context, api: GShockRepository, features: IWatchFeatureManager, raw: RawRequester) = Env(
        api = api,
        features = features,
        raw = raw,
        isH5600 = { WatchModelOverride.isH5600Family(WatchInfo.name) },
        phoneBattery = {
            val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { p -> p in 0..100 }
        },
        // The connected watch's own last connection, never another watch's.
        lastSync = { AppStores.watchStore(appContext).get(org.avmedia.gshockGoogleSync.watch.CurrentWatch.key(), AppStores.KEY_LAST_SYNC, AppStores.KEY_LAST_SYNC)?.value },
        healthData = { H5600Session.data },
        lastWorkout = { WorkoutStore.newest(appContext) },
        rawOn = { AppStores.rawSwitch(appContext).isOn() },
    )

    fun registry(env: Env): VariableRegistry {
        val api = env.api
        val features = env.features
        val raw = env.raw

        fun supported(feature: String) = features.isFeatureSupported(feature)

        suspend fun settings(ctx: VariableContext) = ctx.cached("settings") { api.getSettings() }
        suspend fun alarms(ctx: VariableContext) = ctx.cached("alarms") {
            api.getAlarms().take(features.getAlarmCount())
        }

        fun h5600() = env.isH5600()

        /** Watch condition of the H5600 family, read once per call (the library's profile has no battery for it). */
        suspend fun h5600Condition(ctx: VariableContext) = ctx.cached("h5600.condition") {
            WatchDecoders.h5600Condition(raw.request(0x28, 2000).firstOrNull())
        }

        suspend fun versionAnswer(ctx: VariableContext) = ctx.cached("h5600.version") {
            raw.request(0x20, 2000).firstOrNull()
        }

        /** Raw setting codes (see [RawSettings]), read once per call, at most every 30 minutes each, H5600 only. */
        suspend fun rawSettings(ctx: VariableContext): Map<String, String> =
            if (!h5600()) emptyMap()
            else ctx.cached("raw.settings") {
                val codes = RawSettings.codesFor(env.rawOn())
                RawSettings.shared.refresh(raw, codes)
                val allowed = codes.map { c -> RawSettings.name(c) }.toSet()
                RawSettings.shared.values().filterKeys { k -> k in allowed }
            }

        val list = mutableListOf(
            // Event
            Variable("event", "What triggered this call: watch_action, sync, health_data or test") { it.event },
            Variable("timestamp", "Time of the call, ISO 8601 with offset") { it.now.withNano(0).toString() },

            // Phone and app
            Variable("phone.battery", "Phone battery level, percent") {
                env.phoneBattery()
            },
            Variable("phone.timezone", "Phone time zone id, for example Europe/Brussels") {
                ZoneId.systemDefault().id
            },
            Variable("phone.dst", "True while the phone is on daylight saving time") {
                TimeZone.getDefault().inDaylightTime(Date())
            },
            Variable("app.version", "Smart Sync app version") { BuildConfig.VERSION_NAME },
            Variable("app.last_sync", "When the app last connected to the watch, ISO 8601") {
                env.lastSync()
            },

            Variable("app.ha_push_connected", "True while the Home Assistant push socket is up") { HaPushState.connected },
            Variable("app.ha_push_heartbeat", "Now, ISO 8601: Home Assistant can treat a stale value as 'the app is gone', since a dead app cannot send 'off'") {
                java.time.OffsetDateTime.now().withNano(0).toString()
            },
            Variable("app.ha_push_changed_at", "When the Home Assistant push socket last went up or down, ISO 8601") { HaPushState.changedAt.takeIf { it.isNotEmpty() } },

            // Watch
            Variable("watch.connected", "True when the watch is connected right now") { it.watchConnected },
            Variable("watch.name", "Watch model name, for example GW-B5600") {
                WatchInfo.name.takeIf { n -> n.isNotBlank() }
            },
            Variable("watch.model", "Watch model id used by the app", needsWatch = true) {
                WatchInfo.model.name
            },
            Variable("watch.battery", "Watch battery level, percent (H5600: 7-step scale, 0 = charging/empty, from watch.battery_level)", needsWatch = true) {
                if (h5600()) h5600Condition(it)?.batteryPercent
                else if (supported("time.battery")) api.getBatteryLevel() else null
            },
            Variable("watch.battery_level", "H5600 only: battery step 0..6 (H5600: 7-step scale, 0 = charging/empty)", needsWatch = true) {
                if (h5600()) h5600Condition(it)?.batteryLevel else null
            },
            Variable("watch.battery_label", "H5600 only: battery indicator CHG, LOW, 1, 2, 3, 4 or HIGH (H5600: 7-step scale, 0 = charging/empty)", needsWatch = true) {
                if (h5600()) h5600Condition(it)?.batteryLabel else null
            },
            Variable("watch.temperature", "Temperature from the watch sensor, degrees Celsius", needsWatch = true) {
                if (h5600()) h5600Condition(it)?.temperature
                else if (supported("time.temperature")) api.getWatchTemperature() else null
            },
            Variable("watch.home_city", "Home time city set on the watch", needsWatch = true) {
                if (supported("time.home_time")) api.getHomeTime().takeIf { c -> c.isNotBlank() && c != "N/A" } else null
            },
            Variable("watch.dst.home", "True while daylight saving is on for the home time", needsWatch = true) {
                if (supported("time.home_time")) WatchDecoders.dstHomeOn(api.getDSTWatchState(IO.DstState.ZERO)) else null
            },
            Variable("watch.firmware", "Firmware: unknown until the version bytes are decoded (see watch.firmware_raw)", needsWatch = true) {
                if (h5600()) WatchDecoders.firmwareState(versionAnswer(it)) else null
            },
            Variable("watch.firmware_raw", "H5600 only: the version answer (request 0x20) as hex bytes, not decoded", needsWatch = true) {
                if (h5600()) WatchDecoders.versionRaw(versionAnswer(it)) else null
            },
            Variable("watch.face_raw", "H5600 only: request 0x51 (watch face) as hex bytes, not decoded", needsWatch = true) {
                rawSettings(it)[RawSettings.name(0x51)]
            },
            Variable("watch.face_items_raw", "H5600 only: request 0x52 (items of the custom face) as hex bytes, not decoded", needsWatch = true) {
                rawSettings(it)[RawSettings.name(0x52)]
            },
            Variable("watch.raw_settings", "H5600 only: raw setting requests as an object, code to hex bytes, not decoded", needsWatch = true) {
                rawSettings(it).takeIf { m -> m.isNotEmpty() }
            },
            Variable("watch.raw_settings_read_at", "H5600 only: when a raw setting was last read, ISO 8601 with offset", needsWatch = true) {
                rawSettings(it)
                RawSettings.shared.lastReadMillis()?.let { ms ->
                    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().withNano(0).toString()
                }
            },
            Variable("watch.notifications_supported", "True when the watch accepts app notifications (read while connected)", needsWatch = true) {
                api.supportsAppNotifications()
            },
            Variable("watch.timer_seconds", "Countdown timer set on the watch, seconds", needsWatch = true) {
                api.getTimer()
            },
            Variable("health.steps_today", HealthVariables.DESCRIPTIONS.getValue("health.steps_today"), needsWatch = false) {
                // H5600: the LifeLog read takes 30 to 100 s, so only the last transfer is used, never a new read here.
                if (h5600()) HealthVariables.values(env.healthData())["health.steps_today"]
                else if (it.watchConnected && supported("time.step_counter")) api.getStepCount().currentDaySteps else null
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

        // DW-H5600 LifeLog values, from the last transfer (health.steps_today is defined above).
        for (name in HealthVariables.NAMES.filter { it != "health.steps_today" }) {
            list += Variable(name, HealthVariables.DESCRIPTIONS.getValue(name)) { HealthVariables.values(env.healthData())[name] }
        }

        // DW-H5600 workouts: the newest stored one (read once per call), never a new read here.
        for (name in WorkoutVariables.NAMES) {
            list += Variable(name, WorkoutVariables.DESCRIPTIONS.getValue(name)) { ctx ->
                WorkoutVariables.values(ctx.cached("workout.newest") { env.lastWorkout() })[name]
            }
        }

        // One variable per raw code, from the table: adding a code is one entry in RawSettings.CODES.
        for (code in RawSettings.CODES) {
            list += Variable(RawSettings.variable(code), "H5600 only: request ${RawSettings.name(code)} as hex bytes, not decoded", needsWatch = true) {
                rawSettings(it)[RawSettings.name(code)]
            }
        }

        // Decoded health settings (face, heart-rate measurement, bedtime, night window, unit, profile, smart alarm).
        for (def in WatchSettingVariables.DEFS) {
            list += Variable(def.name, def.description, needsWatch = true) {
                if (h5600()) def.value(WatchSettingVariables.frames(rawSettings(it))) else null
            }
        }

        // World cities 1 to 5 (name only).
        for (i in 1..5) {
            list += Variable("watch.world_city.$i", "Name of world city $i on the watch", needsWatch = true) {
                if (supported("time.world_cities")) WatchDecoders.worldCityName(api.getWorldCities(i), i) else null
            }
        }
        return VariableRegistry(list)
    }
}
