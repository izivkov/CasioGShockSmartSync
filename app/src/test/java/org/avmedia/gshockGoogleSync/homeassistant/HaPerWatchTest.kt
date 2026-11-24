package org.avmedia.gshockGoogleSync.homeassistant

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.avmedia.gshockapi.WatchInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * With the OCW-S7000 connected, Home Assistant still showed health data, even calories. One HA device
 * per watch, each with only the sensors its model has; the GBX registration stays exactly as it was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HaPerWatchTest {
    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
        override fun keys(): Set<String> = m.keys.toSet()
    }

    private class Call(val url: String, val body: String) {
        val type: String? get() = runCatching { JsonParser.parseString(body).asJsonObject.get("type").asString }.getOrNull()
        fun ids(): List<String> = runCatching {
            val o = JsonParser.parseString(body).asJsonObject
            val d = o.get("data")
            if (d.isJsonArray) d.asJsonArray.map { it.asJsonObject.get("unique_id").asString }
            else listOfNotNull(d.asJsonObject.get("unique_id")?.asString)
        }.getOrDefault(emptyList())
    }

    private class T : HaTransport {
        val calls = ArrayList<Call>()
        override fun get(url: String, bearer: String?) = HaResponse(200, """{"message":"API running."}""")
        override fun post(url: String, body: String, bearer: String?): HaResponse {
            val c = Call(url, body)
            calls += c
            return when {
                url.endsWith("/registrations") -> HaResponse(200, """{"webhook_id":"hookO"}""")
                c.type == "update_sensor_states" -> HaResponse(200, """{"battery":{"success":true}}""")
                else -> HaResponse(200, """{"success":true}""")
            }
        }
    }

    private val gbxAddress = "AA:AA:AA:AA:AA:01"
    private val ocwAddress = "BB:BB:BB:BB:BB:02"
    private val gbx = HaPayloads.deviceId(gbxAddress)
    private val ocw = HaPayloads.deviceId(ocwAddress)
    private val gbxName = "GBX-H5600KI-5ER"
    private val gbxBody = HaPayloads.registration(gbx, gbxName, "GBX-H5600", "1", "14")
    private val kv = Mem()

    /** The store as an older version left it: one GBX registration and the values of the one watch, not keyed. */
    private fun olderVersionStore(): HaStore {
        val s = HaStore(kv)
        // The owner had typed the GBX's full model in the setup form.
        s.save(HaConfig("https://ha.example", "tok", model = gbxName))
        s.confirmName(gbx, gbxName)
        s.saveRegistration(gbx, HaRegistration("hookG", gbxBody, HaSensors.fingerprint(HaSensors.ALL)))
        kv.m["ha.sent"] = """{"states":{"battery":80,"kcal_today":1714},"at":5}"""
        kv.m["ha.device"] = "dev-gbx"
        return s
    }

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun reset() {
        WatchInfo.reset()
        Dispatchers.resetMain()
    }

    @Test fun anOcwS7000HasNoHealthWorkoutOrSettingsSensors() {
        WatchInfo.setNameAndModel("CASIO OCW-S7000")
        val caps = WatchCaps.connected()
        assertFalse(WatchCap.H5600 in caps)
        val ids = HaSensors.forCaps(HaSensors.ALL, caps).map { it.variable }
        assertTrue(ids.toString(), ids.none { it.startsWith("health.") || it.startsWith("workout.") || it.startsWith("watch.setting") || it.startsWith("tide") })
        assertTrue("watch.battery" in ids)
        assertTrue("watch.temperature" in ids)
        assertTrue("watch.connected" in ids)
        assertTrue("app.last_sync" in ids)
    }

    @Test fun theGbxKeepsEverySensor() {
        WatchInfo.setNameAndModel("CASIO GBX-H5600")
        assertEquals(WatchCaps.ALL, WatchCaps.connected())
        assertEquals("the same sensors, so the same fingerprint: nothing registered again", HaSensors.fingerprint(HaSensors.ALL), HaSensors.fingerprint(HaSensors.forCaps(HaSensors.ALL, WatchCaps.connected())))
    }

    @Test fun anOcwSyncSendsNoHealthValueAndNothingToTheGbx() {
        val store = olderVersionStore()
        WatchInfo.setNameAndModel("CASIO OCW-S7000")
        val caps = WatchCaps.connected()
        store.confirmName(ocw, "OCW-S7000")
        val reg = HaJobs.registration(store, ocwAddress, "CASIO OCW-S7000", store.load()!!, "1", "14")!!
        // Even when a value of the GBX reached the registry, it must not go out for the OCW.
        val job = HaJobs.job(
            HaTrigger.SYNC, java.time.OffsetDateTime.parse("2026-11-24T10:00:00Z"), "CASIO OCW-S7000",
            mapOf("battery" to 83, "temperature" to 27, "kcal_today" to 1714, "steps_today" to 9000, "sleep_score" to 80),
            emptyMap(), ocw, reg, false, caps,
        )!!
        val t = T()
        assertTrue(HaClient(t, store, mayRegister = { store.confirmedName(it) != null }).send(job) is HaResult.Ok)
        assertTrue("nothing to the GBX: ${t.calls.map { it.url }}", t.calls.none { it.url.contains("hookG") })
        val sent = t.calls.filter { it.type == "register_sensor" || it.type == "update_sensor_states" }.flatMap { it.ids() }
        assertTrue(sent.toString(), "battery" in sent)
        listOf("kcal_today", "steps_today", "sleep_score", "workout_sport", "spo2").forEach { assertFalse("$it was sent for the OCW", it in sent) }
        // Its own device: its own name and its own model, never the GBX's typed model.
        val body = JsonParser.parseString(reg).asJsonObject
        assertEquals("OCW-S7000", body.get("device_name").asString)
        assertEquals("OCW-S7000", body.get("model").asString)
        assertEquals("hookO", store.loadRegistration(ocw)?.webhookId)
        assertEquals("hookG", store.loadRegistration(gbx)?.webhookId)
    }

    @Test fun theGbxRegistrationIsUnchangedAfterTheMigration() {
        val store = olderVersionStore()
        WatchInfo.setNameAndModel("CASIO GBX-H5600")
        val reg = HaJobs.registration(store, gbxAddress, "CASIO GBX-H5600", store.load()!!, "1", "14")!!
        assertEquals("the same body: no update_registration", gbxBody, reg)
        val t = T()
        val job = HaJobs.job(HaTrigger.SYNC, java.time.OffsetDateTime.parse("2026-11-24T10:00:00Z"), "CASIO GBX-H5600", mapOf("battery" to 81), emptyMap(), gbx, reg, true, WatchCaps.connected())!!
        assertTrue(HaClient(t, store).send(job) is HaResult.Ok)
        assertTrue("same webhook: ${t.calls.map { it.url }}", t.calls.all { it.url == "https://ha.example/api/webhook/hookG" })
        assertTrue("no new registration", t.calls.none { it.url.endsWith("/registrations") || it.type == "update_registration" })
        assertTrue("same sensors, same entity ids: nothing registered again", t.calls.none { it.type == "register_sensor" })
        assertEquals("hookG", store.loadRegistration(gbx)?.webhookId)
        // The values of the older version are the GBX's, and only the GBX's.
        assertEquals(1714L, store.loadSent(gbx).states["kcal_today"])
        assertEquals("dev-gbx", store.loadDeviceId(gbx))
        assertTrue(store.loadSent(ocw).states.isEmpty())
        assertNull(store.loadDeviceId(ocw))
    }

    @Test fun aSecondWatchRegistersOnlyAfterItsNameIsConfirmed() {
        val store = olderVersionStore()
        WatchInfo.setNameAndModel("CASIO OCW-S7000")
        assertNull(HaJobs.registration(store, ocwAddress, "CASIO OCW-S7000", store.load()!!, "1", "14"))
        val t = T()
        val job = HaJob(mapOf("battery" to 83), deviceId = ocw, registration = HaPayloads.registration(ocw, "OCW-S7000", "OCW-S7000", "1", "14"))
        assertTrue(HaClient(t, store, mayRegister = { store.confirmedName(it) != null }).send(job) is HaResult.Failed)
        assertTrue(t.calls.isEmpty())
        store.confirmName(ocw, "OCW-S7000")
        assertNotNull(HaJobs.registration(store, ocwAddress, "CASIO OCW-S7000", store.load()!!, "1", "14"))
        assertTrue(HaClient(t, store, mayRegister = { store.confirmedName(it) != null }).send(job) is HaResult.Ok)
        assertEquals(1, t.calls.count { it.url.endsWith("/registrations") })
        assertEquals(listOf(gbx, ocw), store.registeredIds())
    }

    @Test fun aJobWaitsPerWatchAndTheLastResultIsPerWatch() {
        val store = olderVersionStore()
        store.putPending(HaJob(mapOf("battery" to 80), deviceId = gbx, registration = gbxBody))
        store.putPending(HaJob(mapOf("battery" to 83), deviceId = ocw, registration = "{}"))
        assertEquals(80L, store.getPending(gbx)?.states?.get("battery"))
        assertEquals(83L, store.getPending(ocw)?.states?.get("battery"))
        store.saveResult(ocw, "Failed", ok = false)
        assertEquals(false, store.lastResultOk(ocw))
        assertNull(store.lastResultOk(gbx))
        store.clearPending(ocw)
        assertEquals(listOf(gbx), store.pendingIds())
    }

    @Test fun aMessageForAWatchThatIsNotConnectedIsLoggedAndDropped() {
        val store = olderVersionStore()
        store.saveRegistration(ocw, HaRegistration("hookO", HaPayloads.registration(ocw, "OCW-S7000", "OCW-S7000", "1", "14")))
        var connected = ocw
        val logs = ArrayList<String>()
        val gate = HaPushGate(store, { connected }, { logs += it })
        val face = HaPushEvent.Push(null, "command_watch_setting", null, HaPush.COMMAND_WATCH_SETTING)
        assertFalse(gate.accept("hookG", face))
        assertEquals(1, logs.size)
        assertTrue(logs[0], logs[0].contains(gbxName) && logs[0].contains("dropped"))
        assertTrue(gate.accept("hookO", face))
        connected = ""
        assertFalse(gate.accept("hookO", HaPushEvent.Push("Door", "secret text", null)))
        assertFalse("never the message text", logs.last().contains("secret"))
        connected = gbx
        assertTrue(gate.accept("hookG", face))
    }

    @Test fun theSocketListensForEveryWatchAndConfirmsOnTheRightWebhook() {
        val sent = ArrayList<String>()
        val routed = ArrayList<String>()
        val other = ArrayList<Pair<String, String>>()
        val s = HaPushSession(
            "tok", "hookO", send = { sent += it; true }, up = {}, end = {}, route = { routed += it.message }, log = {},
            others = listOf("hookG"), routeOther = { p, hook -> other += p.message to hook },
        )
        s.onText("""{"type":"auth_required"}""")
        s.onText("""{"type":"auth_ok"}""")
        assertEquals(2, sent.count { it.contains("push_notification_channel") })
        assertTrue(sent.any { it.contains("\"webhook_id\":\"hookG\"") })
        s.onText("""{"id":2,"type":"event","event":{"message":"for the gbx","hass_confirm_id":"c1"}}""")
        assertEquals(listOf("for the gbx" to "hookG"), other)
        assertTrue(routed.isEmpty())
        assertTrue(sent.last(), sent.last().contains("push_notification_confirm") && sent.last().contains("hookG"))
    }

    @Test fun watchSettingsAskedForOneWatchAreNeverWrittenToAnother() = kotlinx.coroutines.runBlocking {
        var connected = gbx
        val written = ArrayList<String>()
        val logs = ArrayList<String>()
        val c = WatchSettingCommands(
            write = { s, _ -> written += s.key; org.avmedia.gshockapi.io.SettingWriteResult.Done },
            connected = { true }, log = { logs += it }, onQueued = {}, watch = { connected },
        )
        c.submit(mapOf("setting" to "watch_face", "value" to "steps"))
        connected = ocw
        c.onSetupDone()
        c.flush()
        assertTrue(written.toString(), written.isEmpty())
        assertTrue(logs.toString(), logs.any { it.contains("dropped") })
    }

    @Test fun rawSettingsAreTheConnectedWatchsOwn() = kotlinx.coroutines.runBlocking {
        var connected = gbx
        val reader = RawSettingsReader(clock = { 0L }, watch = { connected })
        reader.refresh(RawRequester { code, _ -> listOf("0x%02X 01 02".format(code)) }, listOf(0x51))
        assertEquals(1, reader.values().size)
        connected = ocw
        assertTrue("another watch sees none of them", reader.values().isEmpty())
        assertNull(reader.lastReadMillis())
    }
}
