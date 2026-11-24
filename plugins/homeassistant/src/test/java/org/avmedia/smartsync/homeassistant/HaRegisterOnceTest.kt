package org.avmedia.smartsync.homeassistant

import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class SafeStore : KeyValueStore {
    val map = ConcurrentHashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun delete(key: String) { map.remove(key) }
}

/** A slow HA: every registration takes [delayMs], as on a real network, and is counted. */
private class SlowHa(private val delayMs: Long) : HaTransport {
    val registrations = AtomicInteger()
    override fun get(url: String, bearer: String?) = HaResponse(200, """{"message":"API running."}""")
    override fun post(url: String, body: String, bearer: String?): HaResponse {
        if (url.endsWith("/registrations")) {
            val n = registrations.incrementAndGet()
            Thread.sleep(delayMs)
            return HaResponse(200, """{"webhook_id":"hook$n"}""")
        }
        return if (body.contains("update_sensor_states")) HaResponse(200, """{"battery":{"success":true}}""")
        else HaResponse(200, """{"success":true}""")
    }
}

/** Build 15 registered the same watch 3 times within a minute and HA dropped all sensors. */
class HaRegisterOnceTest {
    private val reg = HaPayloads.registration("gshock-1", "CASIO GBX-H5600", "GBX-H5600", "1", "14")
    private val job = HaJob(mapOf("battery" to 80), deviceId = "gshock-1", registration = reg)

    private fun setup(delayMs: Long): Pair<SlowHa, HaStore> {
        val t = SlowHa(delayMs)
        val store = HaStore(SafeStore())
        assertTrue(HaClient(t, store).saveSettings("https://ha.example", "tok") is HaResult.Ok)
        return t to store
    }

    @Test
    fun threeJobsAtOnceRegisterTheWatchOnce() {
        val (t, store) = setup(150)
        val pool = Executors.newFixedThreadPool(3)
        val start = CountDownLatch(1)
        val results = (1..3).map {
            pool.submit<HaResult> { start.await(); HaClient(t, store).send(job) } // a new client per worker, as HaWorker does
        }
        start.countDown()
        results.forEach { assertTrue(it.get(10, TimeUnit.SECONDS) is HaResult.Ok) }
        pool.shutdown()
        assertEquals(1, t.registrations.get())
    }

    @Test
    fun threeJobsBackToBackRegisterTheWatchOnce() {
        val (t, store) = setup(0)
        repeat(3) { assertTrue(HaClient(t, store).send(job) is HaResult.Ok) }
        assertEquals(1, t.registrations.get())
    }

    @Test
    fun ensureRegisteredAndSendTogetherRegisterOnce() {
        val (t, store) = setup(150)
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val a = pool.submit<HaResult> { start.await(); HaClient(t, store).ensureRegistered("gshock-1", reg) }
        val b = pool.submit<HaResult> { start.await(); HaClient(t, store).send(job) }
        start.countDown()
        a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS)
        pool.shutdown()
        assertEquals(1, t.registrations.get())
    }

    @Test
    fun differentWatchesStillRegisterEach() {
        val (t, store) = setup(0)
        val other = job.copy(deviceId = "gshock-2", registration = HaPayloads.registration("gshock-2", "CASIO", "GBX", "1", "14"))
        assertTrue(HaClient(t, store).send(job) is HaResult.Ok)
        assertTrue(HaClient(t, store).send(other) is HaResult.Ok)
        assertEquals(2, t.registrations.get())
    }
}
