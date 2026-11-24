package org.avmedia.gshockGoogleSync.plugins

import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.avmedia.gshockGoogleSync.watch.KeyValueStore
import org.avmedia.gshockGoogleSync.watch.WatchStore
import org.avmedia.gshockGoogleSync.watchvalues.SettingsCheck
import org.avmedia.gshockGoogleSync.watchvalues.Variable
import org.avmedia.gshockGoogleSync.watchvalues.VariableRegistry
import org.avmedia.gshockGoogleSync.watchvalues.ValueMeta
import org.avmedia.gshockGoogleSync.watchvalues.WatchSettingRegistry
import org.avmedia.gshockGoogleSync.watchvalues.WatchSettingVariables
import org.avmedia.gshockGoogleSync.watchvalues.WatchVariables
import org.avmedia.smartsync.plugin.api.Capability
import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.FailReason
import org.avmedia.smartsync.plugin.api.PluginAccess
import org.avmedia.smartsync.plugin.api.ValueCause
import org.avmedia.smartsync.plugin.api.ValueType
import org.avmedia.smartsync.plugin.api.WatchEvent
import org.avmedia.smartsync.plugin.api.WatchId
import org.avmedia.smartsync.plugin.api.WatchValues
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The host rules of the plugin interface review (2026-11-24): one key vocabulary, a fail-closed catalogue, re-pairing, values. */
class SpiHostRulesTest {
    private val w = WatchId("gshock-aabbccddeeff")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun tearDown() = scope.cancel()

    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        var batches = 0
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
        override fun keys(): Set<String> = m.keys.toSet()
        override fun putAll(values: Map<String, String?>) { batches++; values.forEach { (k, v) -> if (v == null) m.remove(k) else m[k] = v } }
    }

    private fun registry() = WatchVariables.registry(
        WatchVariables.Env(api = mockk(relaxed = true), features = mockk(relaxed = true), raw = mockk(relaxed = true), isH5600 = { true }, phoneBattery = { 50 }, lastSync = { null }),
    )

    // B1: one key everywhere.
    @Test fun everyCatalogueKeyComesBackUnderTheSameKeyInTheSnapshot() {
        val defs = WatchSettingVariables.DEFS.map { it.name }.toSet()
        val values = WatchSettingRegistry.ALL.associate { WatchSettingVariables.valueKey(it.key) to "v-${it.key}" }
        val snap = AppPluginHost.snapshot(WatchValues(w, values + ("watch.settings.auto_light" to true), values.keys.associateWith { 5L }))
        for (s in WatchSettingRegistry.ALL) {
            assertTrue("${s.key} has a value variable", WatchSettingVariables.valueKey(s.key) in defs)
            assertEquals("${s.key} is the snapshot's key", "v-${s.key}", snap.values[s.key])
        }
        assertEquals("only catalogue rows", WatchSettingRegistry.ALL.map { it.key }.toSet(), snap.values.keys)
        assertEquals(5L, snap.readAtMillis)
    }

    // B2: the catalogue and the data kinds come from the host's registry and fail closed.
    @Test fun everyRegistryVariableStatesItsKindAndAnUnknownKeyIsRefused() {
        val r = registry()
        val missing = r.variables.filter { it.meta == null }.map { it.name }
        assertEquals("every variable states its DataUse", emptyList<String>(), missing)
        val host = WatchValuesHost({ r }, WatchStore(Mem()))
        assertEquals(r.variables.size, host.catalog().size)
        val all = PluginAccess(reads = DataUse.entries.toSet())
        assertFalse("a key the registry does not know is never served", AccessGuard.mayRead(all, host::useOf, "tide.station"))
        val settingsOnly = PluginAccess(reads = setOf(DataUse.WATCH_SETTINGS))
        assertFalse("height is body data", AccessGuard.mayRead(settingsOnly, host::useOf, "watch.profile_height_cm"))
        assertFalse(AccessGuard.mayRead(settingsOnly, host::useOf, "watch.setting.profile_weight_kg"))
        assertTrue(AccessGuard.mayRead(settingsOnly, host::useOf, "watch.setting.step_goal"))
        assertEquals(DataUse.BODY_PROFILE, host.useOf("watch.profile_weight_kg"))
        assertEquals(ValueType.WHOLE, host.catalog().first { it.key == "watch.battery" }.type)
        assertEquals("%", host.catalog().first { it.key == "watch.battery" }.unit)
    }

    @Test fun aVariableWithoutAKindIsServedToNoPlugin() {
        val r = VariableRegistry(listOf(Variable("new.key", "no kind") { 1 }, Variable("phone.x", "x", meta = ValueMeta.of(DataUse.PHONE, ValueType.WHOLE)) { 2 }))
        val host = WatchValuesHost({ r }, WatchStore(Mem()))
        assertEquals(listOf("phone.x"), host.catalog().map { it.key })
        assertNull(host.useOf("new.key"))
        val values = AccessGuard.values(PluginAccess(reads = DataUse.entries.toSet()), host::useOf, WatchValues(w, mapOf("new.key" to 1, "phone.x" to 2)))
        assertEquals(mapOf("phone.x" to 2), values.values)
    }

    @Test fun theSettingRowsCarryLabelAndControl() {
        val rows = PluginHub.settingCatalog(emptySet(), "CASIO GBX-H5600")
        assertEquals(org.avmedia.smartsync.plugin.api.Control.WATCH_FACE, rows.first { it.key == "watch_face" }.control)
        assertEquals(org.avmedia.smartsync.plugin.api.Control.WATCH_SETTINGS, rows.first { it.key == "step_goal" }.control)
        assertEquals("Step goal", rows.first { it.key == "step_goal" }.label)
    }

    // H8: only the rows a watch has.
    @Test fun theSettingCatalogFollowsTheWatchsCapabilities() {
        assertEquals("an OCW-S7000 has none of the health settings", emptyList<Any>(), PluginHub.settingCatalog(setOf(Capability.BATTERY, Capability.WATCH_SETTINGS), "CASIO OCW-S7000"))
        assertEquals(WatchSettingRegistry.ALL.size, PluginHub.settingCatalog(setOf(Capability.HEALTH_SETTINGS), "CASIO GBX-H5600").size)
        assertEquals("a watch not seen yet: all", WatchSettingRegistry.ALL.size, PluginHub.settingCatalog(emptySet(), "").size)
    }

    // S5: all or nothing, the refusal names its key.
    @Test fun changeSettingsIsCheckedAllOrNothing() {
        val unknown = WatchSettingRegistry.checkAll(mapOf("step_goal" to 8000, "nope" to 1)) as SettingsCheck.Refused
        assertEquals(FailReason.NOT_SUPPORTED, unknown.reason)
        assertTrue(unknown.message, unknown.message.startsWith("nope:"))
        val bad = WatchSettingRegistry.checkAll(mapOf("auto_light" to true, "step_goal" to "lots")) as SettingsCheck.Refused
        assertEquals(FailReason.INVALID, bad.reason)
        assertTrue(bad.message, bad.message.startsWith("step_goal:"))
        val ok = WatchSettingRegistry.checkAll(mapOf("auto_light" to "on", "step_goal" to 8000)) as SettingsCheck.Ok
        assertEquals(mapOf("auto_light" to true, "step_goal" to 8000), ok.values)
    }

    // B4: the host finds a re-paired watch itself.
    @Test fun aRePairedWatchIsTheOnlyUnpairedOneWithTheSameName() {
        val names = mapOf("gshock-000000000001" to "CASIO GBX-H5600", "gshock-000000000002" to "CASIO OCW-S7000", "gshock-000000000003" to "CASIO GBX-H5600")
        val known = names.keys.toList()
        assertEquals("gshock-000000000001", RePair.find("gshock-0000000000ff", "CASIO GBX-H5600", known, setOf("gshock-000000000003"), names::get))
        assertNull("two fit: nothing moves", RePair.find("gshock-0000000000ff", "CASIO GBX-H5600", known, emptySet(), names::get))
        assertNull("still paired: not re-paired", RePair.find("gshock-0000000000ff", "CASIO OCW-S7000", known, setOf("gshock-000000000002"), names::get))
        assertNull("no name: nothing", RePair.find("gshock-0000000000ff", "", known, emptySet(), names::get))
    }

    // S10: the same number type fresh and after a restart.
    @Test fun aValueHasTheSameTypeFreshAndKept() = runBlocking {
        val r = VariableRegistry(listOf(
            Variable("watch.battery", "", meta = ValueMeta.of(DataUse.BATTERY_AND_TEMPERATURE, ValueType.WHOLE)) { 80 },
            Variable("health.sleep_breathing", "", meta = ValueMeta.of(DataUse.SLEEP, ValueType.NUMBER)) { 14.2f },
            Variable("health.slots", "", meta = ValueMeta.of(DataUse.STEPS, ValueType.LIST)) { listOf(1, null, 3) },
        ))
        val host = WatchValuesHost({ r }, WatchStore(Mem()))
        val fresh = host.read(w, ValueCause.CONNECTION, null, connected = true).values.values
        val kept = host.kept(w).values
        for (k in fresh.keys) assertEquals("$k: ${fresh[k]} vs ${kept[k]}", kept[k], fresh[k])
        assertTrue(fresh["watch.battery"] is Long)
        assertEquals(14.2, fresh["health.sleep_breathing"])
        assertEquals(listOf(1L, null, 3L), fresh["health.slots"])
    }

    // S8: the storage contract.
    @Test fun storageRefusesBadKeysAndOversizeAndBatchesWrites() {
        val mem = Mem()
        val logs = ArrayList<String>()
        val s = NamespacedStorage(mem, "plugin.p.", log = { logs += it })
        s.put("a@b", "x")
        assertNull(mem.m["plugin.p.a@b"])
        s.put("big", "x".repeat(StorageRules.MAX_VALUE_CHARS + 1))
        assertNull(mem.m["plugin.p.big"])
        s.putAll(mapOf("one" to "1", "two" to "2"))
        assertEquals(1, mem.batches)
        s.putAll(mapOf("one" to null))
        assertEquals(setOf("two"), s.keys())
        assertEquals(2, logs.size)
        val broken = object : KeyValueStore {
            override fun get(key: String): String? = error("disk")
            override fun put(key: String, value: String) = error("disk")
            override fun delete(key: String) = error("disk")
        }
        val b = NamespacedStorage(broken, "plugin.p.", log = { logs += it })
        assertNull("a store error never crosses the interface", b.get("x"))
        b.put("x", "y")
        assertTrue(logs.last().startsWith("storage failed"))
    }

    @Test fun homeAssistantsKeptLayoutKeepsItsAtKeys() {
        val mem = Mem()
        NamespacedStorage(mem, PluginNamespaces.prefixOf("home_assistant"), checked = !PluginNamespaces.isKept("home_assistant")).put("sent@gshock-aabbccddeeff", "{}")
        assertEquals("{}", mem.m["ha.sent@gshock-aabbccddeeff"])
    }

    // S9: a plugin's data can be found and deleted, also of a plugin no longer in the app.
    @Test fun aPluginsDataIsFoundByItsKeys() {
        assertTrue(PluginNamespaces.ownsKey("trmnl", "plugin.trmnl.url"))
        assertTrue(PluginNamespaces.ownsKey("trmnl", "plugin.trmnl.sent@gshock-aabbccddeeff"))
        assertTrue(PluginNamespaces.ownsKey("trmnl", "toggle.trmnl"))
        assertTrue(PluginNamespaces.ownsKey("home_assistant", "ha.config"))
        assertFalse(PluginNamespaces.ownsKey("trmnl", "plugin.trmnl2.url"))
        assertFalse(PluginNamespaces.ownsKey("trmnl", "app.lastSync"))
        assertEquals(setOf("old"), PluginNamespaces.leftOver(setOf("plugin.old.token", "plugin.trmnl.url", "ha.config"), setOf("trmnl", "home_assistant")))
    }

    // S6: the screen opening on turn-on is in SpiSecondReviewTest (the switchboard, where both switches meet).

    @Test fun theStatusLineIsThePluginsOkText() {
        assertEquals("Connected", statusText(PluginState.Active, PluginHealth(okText = "Connected")))
        assertEquals("On", statusText(PluginState.Active, null))
        assertEquals("Off", statusText(PluginState.Off, PluginHealth(okText = "Connected")))
        assertEquals("Connected\nLast error: x, 14:05", statusText(PluginState.Active, PluginHealth("x", 1, 1, "Connected")) { "14:05" })
        assertEquals("2 on", onSummary(mapOf("a" to PluginState.Active, "b" to PluginState.Active, "c" to PluginState.Off), 3))
    }

    // H6: a press is never lost when no value could be read.
    @Test fun aButtonPressGoesOutWithoutValues() {
        assertEquals(w, PluginHub.pressTarget(null, w.key))
        val other = WatchId("gshock-112233445566")
        assertEquals(other, PluginHub.pressTarget(WatchEvent.ValuesUpdated(other, WatchValues(other), ValueCause.BUTTON), w.key))
        assertNull(PluginHub.pressTarget(null, ""))
    }

    // H11: one line for the events, in the order they were posted.
    @Test fun eventsLeaveInTheOrderTheyWerePosted() = runBlocking {
        val seen = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val line = SerialEvents(scope)
        val done = CompletableDeferred<Unit>()
        for (i in 0 until 50) line.post { delay((50 - i % 7).toLong() % 5); seen += i; if (i == 49) done.complete(Unit) }
        withTimeout(5_000) { done.await() }
        assertEquals((0 until 50).toList(), seen.toList())
    }

    // Owner item: phone data goes through the host; a plugin's own permissions are declared, asked and shown.
    @Test fun onlyADeclaringPluginGetsThePhonesLocationAndOnlyAfterTheGrant() = runBlocking {
        var granted = false
        val env = WatchVariables.Env(api = mockk(relaxed = true), features = mockk(relaxed = true), raw = mockk(relaxed = true), isH5600 = { false },
            phoneBattery = { 50 }, lastSync = { null }, phoneLocation = { org.avmedia.gshockGoogleSync.watchvalues.PhoneLocation.coarse(granted) { 50.84673 to 4.35247 } })
        val r = WatchVariables.registry(env)
        val host = WatchValuesHost({ r }, WatchStore(Mem()))
        val declared = PluginAccess(reads = setOf(DataUse.PHONE_LOCATION))
        val undeclared = PluginAccess(reads = DataUse.entries.toSet() - DataUse.PHONE_LOCATION)
        val before = host.read(w, ValueCause.CONNECTION, setOf("phone.location"), connected = false)
        assertFalse("nothing is read without the permission", "phone.location" in before.readNow)
        granted = true
        val after = host.read(w, ValueCause.CONNECTION, setOf("phone.location"), connected = false)
        assertEquals(mapOf("latitude" to 50.85, "longitude" to 4.35), after.values["phone.location"])
        assertEquals(mapOf("latitude" to 50.85, "longitude" to 4.35), AccessGuard.values(declared, host::useOf, after.values).values["phone.location"])
        assertFalse("an undeclared plugin gets no location", "phone.location" in AccessGuard.values(undeclared, host::useOf, after.values).values)
        assertNull(AccessGuard.event(undeclared, host::useOf, after)?.let { (it as WatchEvent.ValuesUpdated).values.values["phone.location"] })
        assertEquals(DataUse.PHONE_BATTERY, host.useOf("phone.battery"))
    }

    @Test fun aMissingPermissionIsAskedForAndShownInTheStatus() {
        val own = org.avmedia.smartsync.plugin.api.AndroidPermission("android.permission.health.WRITE_STEPS", "writing steps to Health Connect")
        val access = PluginAccess(reads = setOf(DataUse.PHONE_LOCATION), androidPermissions = listOf(own))
        val required = PluginPermissions.required(access)
        assertEquals(listOf(own.name, PluginPermissions.COARSE_LOCATION), required.map { it.name })
        assertEquals("a plugin without location reads needs no location permission", emptyList<Any>(), PluginPermissions.required(PluginAccess(reads = setOf(DataUse.PHONE))))
        val missing = PluginPermissions.missing(required, granted = { it == PluginPermissions.COARSE_LOCATION })
        assertEquals(listOf(own), missing)
        assertEquals("Connected\nNeeds permission: writing steps to Health Connect", statusText(PluginState.Active, PluginHealth(okText = "Connected"), missing))
        assertEquals("the plugin's own check wins for its own", emptyList<Any>(), PluginPermissions.missing(listOf(own), granted = { false }, ownCheck = emptySet(), own = setOf(own.name)))
        assertEquals("Connected", statusText(PluginState.Active, PluginHealth(okText = "Connected"), emptyList()))
        assertTrue(PluginDetails.sections(FakePluginWith(access), emptyList()).first { it.title == "Permissions" }.more.any { it.contains("writing steps to Health Connect") })
    }
}
