package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.gshockGoogleSync.ui.time.HomeSnapshot
import org.avmedia.gshockGoogleSync.ui.time.HomeSnapshotStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A re-paired watch (new Bluetooth address) keeps its HA registration; 2026-11-24 the sync stopped quietly. */
class HaRePairTest {
    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
    }

    private class T : HaTransport {
        val posts = ArrayList<String>()
        override fun get(url: String, bearer: String?) = HaResponse(200, "{}")
        override fun post(url: String, body: String, bearer: String?): HaResponse {
            posts += url
            return if (url.endsWith("/registrations")) HaResponse(200, """{"webhook_id":"new"}""")
            else if (body.contains("update_sensor_states")) HaResponse(200, """{"battery":{"success":true}}""")
            else HaResponse(200, """{"success":true}""")
        }
    }

    private val a = "AA:AA:AA:AA:AA:01"
    private val b = "BB:BB:BB:BB:BB:02"
    private val ble = "CASIO GBX-H5600"
    private val name = "GBX-H5600KI-5ER"

    private val kv = Mem()
    private val store = HaStore(kv).apply { save(HaConfig("https://ha.example", "tok")) }
    private val snapshots = HomeSnapshotStore(kv)
    private val logs = ArrayList<String>()
    private val paired = HashSet<String>()

    private fun carryOver() = HaCarryOver(
        paired = { it in paired },
        watchNameAt = { snapshots.load(it)?.watchName },
        moveOthers = { from, to -> snapshots.move(from, to) },
        log = { logs += it },
    )

    private fun registerAtA() {
        val idA = HaPayloads.deviceId(a)
        store.confirmName(idA, name)
        val body = HaPayloads.registration(idA, name, "GBX-H5600", "1", "14")
        store.saveRegistration(idA, HaRegistration("hookA", body, "hash"))
        snapshots.save(a, HomeSnapshot(batteryLevel = 80, watchName = ble))
    }

    @Test fun theSameWatchUnderANewAddressUsesTheOldRegistration() {
        registerAtA()
        val c = carryOver()
        val reg = HaJobs.registration(store, b, ble, store.load()!!, "1", "14", c)
        assertNotNull("the re-paired watch must sync", reg)
        val idB = HaPayloads.deviceId(b)
        assertEquals("hookA", store.loadRegistration(idB)?.webhookId)
        assertEquals(name, store.confirmedName(idB))
        assertNull(store.loadRegistration(HaPayloads.deviceId(a)))
        assertEquals(listOf(idB), store.registeredIds())
        assertEquals(80, snapshots.load(b)?.batteryLevel)
        assertEquals(listOf("$name re-paired, registration moved"), logs)

        val t = T()
        val job = HaJob(mapOf("battery" to 80), deviceId = idB, registration = reg!!)
        assertTrue(HaClient(t, store, mayRegister = { store.confirmedName(it) != null }).send(job) is HaResult.Ok)
        assertTrue("no second registration: ${t.posts}", t.posts.none { it.endsWith("/registrations") })
        assertTrue(t.posts.all { it.endsWith("/api/webhook/hookA") })
        assertEquals("hookA", store.loadRegistration(idB)?.webhookId)
        assertEquals(1, store.registeredIds().size)
    }

    @Test fun aDifferentModelMovesNothingAndSaysWhyOnce() {
        registerAtA()
        val c = carryOver()
        val other = "CASIO GW-B5600"
        assertNull(HaJobs.registration(store, b, other, store.load()!!, "1", "14", c))
        assertNull(HaJobs.registration(store, b, other, store.load()!!, "1", "14", c))
        assertEquals("hookA", store.loadRegistration(HaPayloads.deviceId(a))?.webhookId)
        assertNull(store.loadRegistration(HaPayloads.deviceId(b)))
        assertNull(snapshots.load(b))
        assertEquals("one line for two skipped syncs: $logs", 1, logs.size)
        assertTrue(logs[0], logs[0].contains("not synced"))
    }

    @Test fun anOldAddressThatIsStillPairedKeepsItsRegistration() {
        registerAtA()
        paired += a
        assertNull(HaJobs.registration(store, b, ble, store.load()!!, "1", "14", carryOver()))
        assertEquals("hookA", store.loadRegistration(HaPayloads.deviceId(a))?.webhookId)
        assertEquals(1, logs.size)
    }

    @Test fun addressOfGivesTheAddressBack() {
        assertEquals(a, HaCarryOver.addressOf(HaPayloads.deviceId(a)))
        assertNull(HaCarryOver.addressOf("gshock-1"))
    }
}
