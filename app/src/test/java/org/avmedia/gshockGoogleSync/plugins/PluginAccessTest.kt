package org.avmedia.gshockGoogleSync.plugins

import io.mockk.mockk
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.avmedia.smartsync.plugin.api.Capability
import org.avmedia.smartsync.plugin.api.Control
import org.avmedia.smartsync.plugin.api.DataKind
import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.DayRecord
import org.avmedia.smartsync.plugin.api.HistoryRange
import org.avmedia.smartsync.plugin.api.PluginAccess
import org.avmedia.smartsync.plugin.api.PluginApi
import org.avmedia.smartsync.plugin.api.PluginHost
import org.avmedia.smartsync.plugin.api.PluginIcon
import org.avmedia.smartsync.plugin.api.ValueCause
import org.avmedia.smartsync.plugin.api.WatchCommand
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchId
import org.avmedia.smartsync.plugin.api.WatchPlugin
import org.avmedia.smartsync.plugin.api.WatchValues
import org.avmedia.smartsync.plugin.api.WorkoutRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A plugin gets only what it declared, only for the watches it works with, and its switch is kept. */
class PluginAccessTest {
    private val w = WatchId("gshock-aaaaaaaaaa01")
    private val ocw = WatchId("gshock-bbbbbbbbbb02")
    private val stepsOnly = PluginAccess(reads = setOf(DataUse.STEPS), controls = setOf(Control.WATCH_SETTINGS))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** The kinds the registry states for these keys; any other key has none. */
    private val uses: ValueUses = mapOf(
        "health.steps_today" to DataUse.STEPS, "health.hr_rest" to DataUse.HEART_RATE, "watch.battery" to DataUse.BATTERY_AND_TEMPERATURE,
    )::get

    @After fun tearDown() = scope.cancel()

    @Test fun anUndeclaredReadOrCommandIsRefused() {
        assertTrue(AccessGuard.mayRead(stepsOnly, DataKind.Hours))
        assertFalse(AccessGuard.mayRead(stepsOnly, DataKind.HeartRate))
        assertFalse(AccessGuard.mayRead(stepsOnly, DataKind.Workouts))
        assertTrue(AccessGuard.mayRun(stepsOnly, WatchCommand.ChangeSettings(mapOf("step_goal" to 9000))))
        assertFalse("the face is a control of its own", AccessGuard.mayRun(stepsOnly, WatchCommand.ChangeSettings(mapOf("watch_face" to "steps"))))
        val values = WatchValues(w, mapOf("health.steps_today" to 5000, "health.hr_rest" to 55, "watch.battery" to 80))
        assertEquals(mapOf("health.steps_today" to 5000), AccessGuard.values(stepsOnly, uses, values).values)
        val day = AccessGuard.record(stepsOnly, DayRecord(java.time.LocalDate.of(2026, 11, 24), steps = 5000, hrRest = 55, spo2 = 97))
        assertEquals(5000, day.steps)
        assertNull("heart rate inside a day is not declared", day.hrRest)
        assertNull(day.spo2)
    }

    @Test fun undeclaredEventsAreNotDelivered() {
        val workout = WorkoutRecord("1-2", java.time.Instant.EPOCH, sport = 2, sportName = "Run")
        assertNull(AccessGuard.event(stepsOnly, uses, WatchEvent.WorkoutStored(w, workout)))
        assertNull(AccessGuard.event(stepsOnly, uses, WatchEvent.ButtonPressed(w, java.time.Instant.EPOCH)))
        assertNull("a body profile setting is not a watch setting", AccessGuard.event(PluginAccess(reads = setOf(DataUse.WATCH_SETTINGS)), { k -> org.avmedia.gshockGoogleSync.watchvalues.ValueMeta.ofSetting("profile_weight_kg", org.avmedia.gshockGoogleSync.watchvalues.SettingType.Whole(20..300)).use.takeIf { k == "watch.setting.profile_weight_kg" } }, WatchEvent.SettingChanged(w, "profile_weight_kg", 80)))
        val values = WatchValues(w, mapOf("health.steps_today" to 5000, "watch.battery" to 80))
        val seen = AccessGuard.event(stepsOnly, uses, WatchEvent.ValuesUpdated(w, values, ValueCause.CONNECTION)) as WatchEvent.ValuesUpdated
        assertEquals(setOf("health.steps_today"), seen.readNow)
        val health = WatchEvent.HealthRead(w, HistoryRange.days(java.time.LocalDate.now(), java.time.LocalDate.now()), setOf(DataKind.Hours, DataKind.Nights))
        assertEquals(setOf<DataKind<*>>(DataKind.Hours), (AccessGuard.event(stepsOnly, uses, health) as WatchEvent.HealthRead).kinds)
        assertEquals(WatchEvent.Disconnected(w), AccessGuard.event(stepsOnly, uses, WatchEvent.Disconnected(w)))
    }

    @Test fun theRuntimeDeliversOnlyDeclaredEventsAndOnlyForWatchesThePluginWorksWith() = runBlocking {
        val seen: MutableList<WatchEvent> = Collections.synchronizedList(mutableListOf())
        val p = plugin("health", PluginAccess(reads = setOf(DataUse.STEPS), requires = setOf(Capability.HEALTH_LOG))) { seen += it }
        val caps = mapOf(w to setOf(Capability.HEALTH_LOG, Capability.STEPS), ocw to setOf(Capability.BATTERY))
        val rt = PluginRuntime(scope, hostFor = { mockk(relaxed = true) }, log = { _, _ -> }, capsOf = { caps[it].orEmpty() })
        rt.register(listOf(p))
        rt.activate("health")
        rt.dispatch(WatchEvent.WorkoutStored(w, WorkoutRecord("1-2", java.time.Instant.EPOCH, sport = 2, sportName = "Run")))
        rt.dispatch(WatchEvent.Disconnected(ocw))
        rt.dispatch(WatchEvent.Disconnected(w))
        withTimeout(2_000) { while (seen.isEmpty()) delay(10) }
        delay(100)
        assertEquals("not the undeclared workout, nothing for the watch without a health log", listOf<WatchEvent>(WatchEvent.Disconnected(w)), seen.toList())
    }

    @Test fun requiresAndUsesAreResolvedPerWatch() {
        val ha = PluginAccess(uses = setOf(Capability.NOTIFICATIONS, Capability.HEALTH_LOG, Capability.BATTERY))
        val hc = PluginAccess(requires = setOf(Capability.HEALTH_LOG), uses = setOf(Capability.HEALTH_LOG, Capability.BLOOD_OXYGEN))
        val gbx = setOf(Capability.NOTIFICATIONS, Capability.HEALTH_LOG, Capability.BATTERY, Capability.BLOOD_OXYGEN)
        val ocwCaps = setOf(Capability.BATTERY)
        assertEquals(WatchSupport.Full, WatchSupport.of(ha, gbx))
        assertEquals(WatchSupport.Partial(setOf(Capability.NOTIFICATIONS, Capability.HEALTH_LOG)), WatchSupport.of(ha, ocwCaps))
        assertEquals(WatchSupport.NotSupported(setOf(Capability.HEALTH_LOG)), WatchSupport.of(hc, ocwCaps))
        assertEquals("a watch never connected is not known yet", WatchSupport.Full, WatchSupport.of(hc, emptySet()))
        assertEquals(
            listOf("GBX-H5600 (all features)", "OCW-S7000 (not supported: needs health log)"),
            AccessGuard.worksWith(listOf("GBX-H5600" to WatchSupport.of(hc, gbx), "OCW-S7000" to WatchSupport.of(hc, ocwCaps))),
        )
    }

    @Test fun theSwitchSurvivesARestartAndActivatesAndDeactivates() = runBlocking {
        val kv = Mem()
        val p = plugin("p", PluginAccess())
        fun board(): Pair<PluginSwitchboard, PluginRuntime> {
            val rt = PluginRuntime(scope, hostFor = { mockk(relaxed = true) }, log = { _, _ -> })
            rt.register(listOf(p))
            return PluginSwitchboard(PluginToggles(kv), rt) to rt
        }
        val (b1, rt1) = board()
        b1.startAll()
        assertEquals("a new plugin is off", PluginState.Off, rt1.state("p"))
        b1.set("p", true)
        assertEquals(PluginState.Active, rt1.state("p"))
        assertEquals(1, p.activations)
        val (b2, rt2) = board()
        b2.startAll()
        assertEquals("on again after a restart", PluginState.Active, rt2.state("p"))
        b2.set("p", false)
        assertEquals(PluginState.Off, rt2.state("p"))
        assertEquals(1, p.deactivations)
        val (b3, rt3) = board()
        b3.startAll()
        assertEquals("off after a restart", PluginState.Off, rt3.state("p"))
    }

    @Test fun homeAssistantSetUpBeforeThePluginsStaysOn() = runBlocking {
        val kv = Mem()
        kv.m["ha.config"] = """{"baseUrl":"https://ha.example","token":"t"}"""
        val legacy = { id: String -> PluginHub.legacyOn(id, kv) }
        val rt = PluginRuntime(scope, hostFor = { mockk(relaxed = true) }, log = { _, _ -> })
        rt.register(listOf(plugin("home_assistant", PluginAccess()), plugin("other", PluginAccess())))
        PluginSwitchboard(PluginToggles(kv), rt, legacyOn = legacy).startAll()
        assertEquals(PluginState.Active, rt.state("home_assistant"))
        assertEquals(PluginState.Off, rt.state("other"))
        // Its switch is the key Home Assistant always had.
        kv.m["toggle.home_assistant"] = "off"
        assertFalse(PluginSwitchboard(PluginToggles(kv), rt, legacyOn = legacy).isOn("home_assistant"))
    }

    @Test fun aMissingIconUsesTheFallback() {
        assertEquals(PluginIcon.Vector(FALLBACK_ICON), iconOf(null))
        assertEquals(PluginIcon.Drawable(42), iconOf(PluginIcon.Drawable(42)))
    }

    @Test fun theDetailsListTheDeclaration() {
        val s = PluginDetails.sections(FakePlugin(), listOf("GBX-H5600 (all features)")).associateBy { it.title }
        assertEquals("steps, distance and energy, heart rate and 1 more", s.getValue("Reads").line)
        assertEquals(listOf("steps, distance and energy", "heart rate", "sleep"), s.getValue("Reads").more)
        assertEquals("show notifications on the watch, change watch settings", s.getValue("Controls").line)
        assertEquals("your Home Assistant server", s.getValue("Sends to").line)
        assertEquals("Home Assistant address and token", s.getValue("Stores").line)
        assertEquals("GBX-H5600 (all features)", s.getValue("Works with").line)
    }

    // A plugin that maps everything shows one line, not every kind.
    @Test fun everythingIsOneLine() {
        val ha = org.avmedia.smartsync.homeassistant.HomeAssistantPlugin()
        val s = PluginDetails.sections(ha, emptyList()).associateBy { it.title }
        assertTrue(s.getValue("Reads").line, s.getValue("Reads").line.startsWith(PluginDetails.EVERYTHING))
        assertEquals(PluginDetails.ALL_CONTROLS, s.getValue("Controls").line)
        assertEquals("the person can still see each kind", (DataUse.ALL - DataUse.PHONE_LOCATION).size, s.getValue("Reads").more.size)
        val few = PluginDetails.reads(PluginAccess(reads = setOf(DataUse.STEPS)))
        assertEquals("a plugin that reads little keeps its list", "steps, distance and energy", few.line)
    }

    private class Mem : org.avmedia.gshockGoogleSync.watch.KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
        override fun keys(): Set<String> = m.keys.toSet()
    }

    private class P(override val id: String, override val access: PluginAccess, val handle: (WatchEvent) -> Unit) : WatchPlugin {
        override val name = id
        override val version = "1"
        override val apiVersion = PluginApi.VERSION
        var activations = 0
        var deactivations = 0
        override suspend fun activate(host: PluginHost) { activations++ }
        override suspend fun deactivate() { deactivations++ }
        override suspend fun onEvent(event: WatchEvent) = handle(event)
    }

    private fun plugin(id: String, access: PluginAccess, handle: (WatchEvent) -> Unit = {}) = P(id, access, handle)
}
