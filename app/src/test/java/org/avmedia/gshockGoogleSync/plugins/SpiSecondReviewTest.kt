package org.avmedia.gshockGoogleSync.plugins

import io.mockk.mockk
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.avmedia.gshockGoogleSync.watch.KeyValueStore
import org.avmedia.gshockGoogleSync.watch.WatchKeys
import org.avmedia.gshockGoogleSync.watch.WatchStore
import org.avmedia.gshockGoogleSync.watchvalues.ValueMeta
import org.avmedia.gshockGoogleSync.watchvalues.WatchSettingRegistry
import org.avmedia.gshockGoogleSync.watchvalues.WatchSettingVariables
import org.avmedia.gshockGoogleSync.watchvalues.WatchVariables
import org.avmedia.smartsync.plugin.api.AndroidPermission
import org.avmedia.smartsync.plugin.api.Control
import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.PluginAccess
import org.avmedia.smartsync.plugin.api.PluginApi
import org.avmedia.smartsync.plugin.api.PluginHost
import org.avmedia.smartsync.plugin.api.PluginNeeds
import org.avmedia.smartsync.plugin.api.PluginUi
import org.avmedia.smartsync.plugin.api.ValueType
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchPlugin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The guards of the second plugin interface review (2026-11-24): M1 to M3, N1 to N3, N6 and N12. */
class SpiSecondReviewTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun tearDown() = scope.cancel()

    private class Fake(
        override val id: String,
        override val access: PluginAccess = PluginAccess(reads = DataUse.ALL, controls = Control.ALL),
        val opens: Boolean = false,
        val handle: suspend (WatchEvent) -> Unit = {},
    ) : WatchPlugin {
        override val name: String get() = id
        override val version = "1"
        override val apiVersion = PluginApi.VERSION
        override suspend fun activate(host: PluginHost) {}
        override suspend fun deactivate() {}
        override suspend fun onEvent(event: WatchEvent) = handle(event)
        override val ui: PluginUi = object : PluginUi {
            @androidx.compose.runtime.Composable override fun SettingsScreen(onBack: () -> Unit) {}
            override val opensOnTurnOn: Boolean get() = opens
        }
    }

    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
        override fun keys(): Set<String> = m.keys.toSet()
        override fun putAll(values: Map<String, String?>) { values.forEach { (k, v) -> if (v == null) m.remove(k) else m[k] = v } }
    }

    private val registry by lazy {
        WatchVariables.registry(
            WatchVariables.Env(api = mockk(relaxed = true), features = mockk(relaxed = true), raw = mockk(relaxed = true), isH5600 = { true }, phoneBattery = { 50 }, lastSync = { null }),
        )
    }
    private val values by lazy { WatchValuesHost({ registry }, WatchStore(Mem())) }
    private val settingKeys = WatchSettingRegistry.ALL.map { WatchSettingVariables.valueKey(it.key) }

    private fun runtime() = PluginRuntime(
        scope, hostFor = { mockk(relaxed = true) }, log = { _, _ -> },
        uses = values::useOf, allKeys = { values.keys() }, settingKeys = { settingKeys },
    )

    private suspend fun until(what: String, ms: Long = 5_000, ok: () -> Boolean) {
        runCatching { withTimeout(ms) { while (!ok()) delay(10) } }.onFailure { throw AssertionError("timed out: $what") }
    }

    // M1: the plugin's own check suspends (a service call); the screen reads the kept answer and never waits for it.
    @Test fun aSlowOwnPermissionCheckNeverBlocksTheScreen() = runBlocking {
        val own = AndroidPermission("android.permission.health.WRITE_STEPS", "writing steps")
        val access = PluginAccess(androidPermissions = listOf(own))
        val gate = CompletableDeferred<Unit>()
        val cache = PluginPermissionCache(scope, access = { access }, granted = { false }, ownCheck = { gate.await(); delay(200); emptySet() })
        cache.refresh("hc")
        val t0 = System.nanoTime()
        assertEquals("before the first answer: nothing claimed", emptyList<AndroidPermission>(), cache.of("hc"))
        assertTrue("the read does not wait", System.nanoTime() - t0 < 50_000_000)
        gate.complete(Unit)
        until("the plugin's answer is kept") { cache.missing.value.containsKey("hc") }
        assertEquals("its own check says granted", emptyList<AndroidPermission>(), cache.of("hc"))
    }

    @Test fun anOwnCheckOverItsLimitKeepsTheAnswerBefore() = runBlocking {
        val own = AndroidPermission("android.permission.health.WRITE_STEPS", "writing steps")
        var slow = false
        val cache = PluginPermissionCache(scope, access = { PluginAccess(androidPermissions = listOf(own)) }, granted = { true },
            ownCheck = { if (slow) delay(10_000); setOf(own.name) }, timeoutMs = 200)
        assertEquals(listOf(own), cache.check("hc"))
        slow = true
        assertEquals("the answer before stays", listOf(own), cache.check("hc"))
    }

    @Test fun anOwnCheckDecidesOnlyForThePluginsOwnPermissions() {
        val own = AndroidPermission("android.permission.health.WRITE_STEPS", "writing steps")
        val access = PluginAccess(reads = setOf(DataUse.PHONE_LOCATION), androidPermissions = listOf(own))
        val missing = PluginPermissions.missing(PluginPermissions.required(access), granted = { false }, ownCheck = emptySet(), own = setOf(own.name))
        assertEquals("the host's location permission is still missing", listOf(PluginPermissions.COARSE_LOCATION), missing.map { it.name })
    }

    // M2: settings are read for a plugin that asks, and only then.
    @Test fun settingsAreReadForAPluginThatAsksAndOnlyThen() = runBlocking {
        val rt = runtime()
        rt.register(listOf(Fake("a"), Fake("b", PluginAccess(reads = setOf(DataUse.STEPS)))))
        rt.activate("a"); rt.activate("b")
        rt.declareNeeds("a", PluginNeeds(values = emptySet(), settings = false))
        assertTrue("no settings without asking", rt.needs().values!!.none { it in settingKeys })
        rt.declareNeeds("a", PluginNeeds(values = emptySet(), settings = true))
        assertTrue("every catalogue setting key", rt.needs().values!!.containsAll(settingKeys.filter { values.useOf(it) != null }))
        assertTrue(rt.needs().settings)
        rt.declareNeeds("a", PluginNeeds())
        rt.declareNeeds("b", PluginNeeds(settings = true))
        assertFalse("a plugin without the settings kinds asks for nothing", rt.needs().settings)
        assertTrue(rt.needs().values!!.isEmpty())
    }

    // M3: a local time is its own type, and the descriptions agree with the types.
    @Test fun localTimesHaveTheirOwnType() {
        val local = listOf("health.recorded_at", "health.hr_max_at", "health.hr_min_at", "health.sleep_fell_asleep", "health.sleep_woke_up", "health.spo2_at", "health.breathing_exercise_at")
        local.forEach { assertEquals(it, ValueType.LOCAL_DATE_TIME, ValueMeta.HEALTH.getValue(it).type) }
        for (v in registry.variables) {
            val type = v.meta?.type ?: continue
            if (type == ValueType.DATE_TIME) assertFalse("${v.name} says without offset", v.description.contains("without offset"))
            if (type == ValueType.LOCAL_DATE_TIME) assertTrue("${v.name} must say without offset", v.description.contains("without offset"))
        }
    }

    // N1: a removed watch is never taken for a re-paired one.
    @Test fun aRemovedWatchIsNeverARePairCandidate() {
        val kv = Mem()
        val store = WatchStore(kv)
        val old = "gshock-aaaaaaaaaaaa"
        store.put(old, PluginHub.KEY_CAPS, "steps")
        store.put(old, WatchValuesHost.KEY, "{}")
        kv.put("plugin.trmnl.last@$old", "x")
        kv.put("app.own@$old", "kept")
        assertEquals("before: a candidate", old, RePair.find("gshock-bbbbbbbbbbbb", "CASIO GBX-H5600", PluginWatchData.known(kv), emptySet()) { "CASIO GBX-H5600" })
        PluginWatchData.remove(kv, old)
        assertNull("after removal: nothing moves", RePair.find("gshock-bbbbbbbbbbbb", "CASIO GBX-H5600", PluginWatchData.known(kv), emptySet()) { "CASIO GBX-H5600" })
        assertNull(kv.get(WatchKeys.scoped(WatchValuesHost.KEY, old)))
        assertNull(kv.get("plugin.trmnl.last@$old"))
        assertEquals("the app's own keys stay", "kept", kv.get("app.own@$old"))
    }

    // N2: the wake-up job waits until the plugin handled its Wake.
    @Test fun theWakeJobWaitsForThePlugin() = runBlocking {
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val rt = runtime()
        rt.register(listOf(Fake("timer", handle = { if (it == WatchEvent.Wake) { delay(300); done.set(true) } })))
        rt.activate("timer")
        rt.declareNeeds("timer", PluginNeeds(wakeEveryMinutes = 15))
        assertEquals(listOf("timer"), rt.wakeAndWait(1_000_000_000L, timeoutMs = 5_000))
        assertTrue("handled before the job ends", done.get())
    }

    // N3: "every key" is every key of the declared kinds: no location for a plugin that did not declare it.
    @Test fun allValuesMeansAllOfTheDeclaredKinds() = runBlocking {
        val rt = runtime()
        rt.register(listOf(Fake("all", PluginAccess(reads = DataUse.ALL - DataUse.PHONE_LOCATION))))
        rt.activate("all")
        rt.declareNeeds("all", PluginNeeds(values = null))
        val wanted = rt.needs().values!!
        assertFalse(wanted.contains("phone.location"))
        assertTrue(wanted.contains("watch.battery"))
    }

    // N6: both switches end in the switchboard; it opens the screen after a turn-on, never for a turn-off or a failed start.
    @Test fun theScreenOpensAfterATurnOnFromAnySwitch() = runBlocking {
        val opened = Collections.synchronizedList(mutableListOf<String>())
        val rt = runtime()
        val bad = object : WatchPlugin by Fake("bad", opens = true) {
            override suspend fun activate(host: PluginHost) = throw IllegalStateException("no")
        }
        rt.register(listOf(Fake("hc", opens = true), Fake("ha"), bad))
        val board = PluginSwitchboard(PluginToggles(Mem()), rt, opensOnTurnOn = { rt.plugin(it)?.ui?.opensOnTurnOn == true }, open = { opened += it })
        board.set("hc", true)
        assertEquals(listOf("hc"), opened)
        board.set("hc", false)
        assertEquals("not on a turn-off", listOf("hc"), opened)
        board.set("hc", true)
        assertEquals("every turn-on", listOf("hc", "hc"), opened)
        board.set("ha", true)
        board.set("bad", true)
        assertEquals("not for a plugin that does not ask or failed", listOf("hc", "hc"), opened)
    }

    // N12: two requests in a row: each answer goes to the plugin that asked.
    @Test fun permissionRequestsAreAnsweredInOrder() {
        val q = PermissionQueue<String>()
        assertEquals("a", q.offer("a"))
        assertNull("the second waits", q.offer("b"))
        assertEquals("a" to "b", q.answered())
        assertEquals("b" to null, q.answered())
        assertEquals(null to null, q.answered())
    }
}
