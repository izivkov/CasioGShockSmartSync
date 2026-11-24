package org.avmedia.gshockGoogleSync.homeassistant

import org.avmedia.gshockGoogleSync.watch.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class ActMemoryStore : KeyValueStore {
    val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun delete(key: String) { map.remove(key) }
}

private class ActTransport(
    val states: HaResponse = HaResponse(200, STATES),
    val service: HaResponse = HaResponse(200, "[]"),
) : HaTransport {
    val posts = ArrayList<Triple<String, String, String?>>()
    val gets = ArrayList<Pair<String, String?>>()
    override fun get(url: String, bearer: String?): HaResponse { gets += url to bearer; return states }
    override fun post(url: String, body: String, bearer: String?): HaResponse {
        posts += Triple(url, body, bearer)
        return when {
            url.endsWith("/registrations") -> HaResponse(200, """{"webhook_id":"hook1"}""")
            url.contains("/api/services/") -> service
            else -> HaResponse(200, """{"success":true}""")
        }
    }
}

private const val STATES = """[
 {"entity_id":"light.kitchen","state":"on","attributes":{"friendly_name":"Kitchen"}},
 {"entity_id":"scene.movie","state":"unknown","attributes":{"friendly_name":"Movie night"}},
 {"entity_id":"script.bedtime","state":"off","attributes":{"friendly_name":"Bedtime"}},
 {"entity_id":"automation.goodnight","state":"on","attributes":{"friendly_name":"Goodnight"}},
 {"entity_id":"automation.noname","state":"on","attributes":{}}
]"""

class HaActionTest {
    private val goodnight = HaAction("automation", "automation.goodnight", "Goodnight")
    private val bedtime = HaAction("script", "script.bedtime", "Bedtime")
    private val movie = HaAction("scene", "scene.movie", "Movie night")

    private fun setup(t: ActTransport, chosen: HaAction?): Pair<HaStore, HaActionRunner> {
        val store = HaStore(ActMemoryStore())
        store.save(HaConfig("https://ha.example", "tok"))
        store.saveAction(chosen)
        return store to HaActionRunner(t, store) { "22:14" }
    }

    @Test
    fun statesParseIntoTheThreeKindsByFriendlyName() {
        val items = HaAction.parseStates(STATES)
        assertEquals(
            listOf("automation.noname", "automation.goodnight", "script.bedtime", "scene.movie"),
            items.map { it.entityId },
        )
        assertEquals("automation.noname", items[0].name)
        assertEquals("Goodnight", items[1].name)
        assertTrue(items.none { it.entityId.startsWith("light.") })
    }

    @Test
    fun badStatesBodyGivesAnEmptyList() {
        assertTrue(HaAction.parseStates("not json").isEmpty())
        assertTrue(HaAction.parseStates("""{"a":1}""").isEmpty())
    }

    @Test
    fun fetchAsksStatesWithTheSavedToken() {
        val t = ActTransport()
        val (_, runner) = setup(t, null)
        val list = runner.fetch()
        assertEquals("https://ha.example/api/states", t.gets.single().first)
        assertEquals("tok", t.gets.single().second)
        assertEquals(4, list.items.size)
        assertNull(list.error)
    }

    @Test
    fun fetchFailureIsPlainWords() {
        val (_, runner) = setup(ActTransport(states = HaResponse(401, "")), null)
        val list = runner.fetch()
        assertTrue(list.items.isEmpty())
        assertTrue(list.error, list.error!!.contains("token"))
    }

    @Test
    fun eachKindCallsItsServiceWithTheEntity() {
        listOf(
            goodnight to "/api/services/automation/trigger",
            bedtime to "/api/services/script/turn_on",
            movie to "/api/services/scene/turn_on",
        ).forEach { (action, path) ->
            val t = ActTransport()
            val (store, runner) = setup(t, action)
            runner.runChosen()
            val call = t.posts.single()
            assertEquals("https://ha.example$path", call.first)
            assertEquals("""{"entity_id":"${action.entityId}"}""", call.second)
            assertEquals("tok", call.third)
            assertEquals("Ran ${action.name} at 22:14", store.lastActionResult())
        }
    }

    @Test
    fun nothingChosenCallsNothing() {
        val t = ActTransport()
        val (store, runner) = setup(t, null)
        runner.runChosen()
        assertTrue(t.posts.isEmpty())
        assertNull(store.lastActionResult())
    }

    @Test
    fun choiceIsKeptAndNothingClearsIt() {
        val (store, _) = setup(ActTransport(), bedtime)
        assertEquals(bedtime, store.loadAction())
        store.saveAction(null)
        assertNull(store.loadAction())
    }

    @Test
    fun failedServiceCallIsShownInWordsAndTheEventStillWent() {
        val t = ActTransport(service = HaResponse(404, ""))
        val (store, _) = setup(t, goodnight)
        val reg = HaPayloads.registration("gshock-1", "CASIO GBX-H5600", "GBX-H5600", "1", "14")
        val job = HaJob(emptyMap(), mapOf("trigger" to "watch_action"), deviceId = "gshock-1", registration = reg)
        val result = HaClient(t, store).send(job)
        assertTrue(result.message, result is HaResult.Ok)
        assertTrue(t.posts.any { it.second.contains("fire_event") })
        val shown = store.lastActionResult().orEmpty()
        assertTrue(shown, shown.contains("Goodnight") && !shown.startsWith("Ran"))
        assertTrue(shown, !shown.contains("404") || shown.contains("not find"))
    }

    @Test
    fun noEventMeansNoServiceCall() {
        val t = ActTransport()
        val (store, _) = setup(t, goodnight)
        val reg = HaPayloads.registration("gshock-1", "CASIO GBX-H5600", "GBX-H5600", "1", "14")
        HaClient(t, store).send(HaJob(mapOf("battery" to 80), deviceId = "gshock-1", registration = reg))
        assertTrue(t.posts.none { it.first.contains("/api/services/") })
    }
}
