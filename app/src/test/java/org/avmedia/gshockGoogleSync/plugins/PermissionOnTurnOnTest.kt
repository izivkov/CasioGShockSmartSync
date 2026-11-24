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
import org.avmedia.smartsync.plugin.api.AndroidPermission
import org.avmedia.smartsync.plugin.api.PluginAccess
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Turning a plugin on that misses a permission asks for it at once, once, with no other tap (Settings > Plugins and
 * the Actions tab both end in [PluginSwitchboard.set]); the answer is the person's.
 */
class PermissionOnTurnOnTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @After fun tearDown() = scope.cancel()

    private val hc = AndroidPermission("android.permission.health.WRITE_STEPS", "writing to Health Connect")
    private val asked: MutableList<Pair<String, List<String>>> = Collections.synchronizedList(mutableListOf())

    private fun board(granted: Boolean): PluginSwitchboard {
        val plugin = FakePluginWith(PluginAccess(androidPermissions = listOf(hc)))
        val rt = PluginRuntime(scope, hostFor = { mockk(relaxed = true) }, log = { _, _ -> })
        rt.register(listOf(plugin))
        val cache = PluginPermissionCache(scope, access = { rt.plugin(it)?.access }, granted = { granted }, ownCheck = { null })
        val asker = PermissionAsker(scope, check = { cache.check(it) }, request = { id, m -> id to m.map { it.name } }, emit = { asked += it })
        val kv = object : org.avmedia.gshockGoogleSync.watch.KeyValueStore {
            val m = HashMap<String, String>()
            override fun get(key: String) = m[key]
            override fun put(key: String, value: String) { m[key] = value }
            override fun delete(key: String) { m.remove(key) }
        }
        return PluginSwitchboard(PluginToggles(kv), rt, afterTurnOn = { asker.ask(it) })
    }

    @Test fun switchingOnWithAMissingPermissionAsksOnceWithoutAnotherTap() = runBlocking {
        board(granted = false).set("with", true)
        withTimeout(2_000) { while (asked.isEmpty()) delay(5) }
        delay(100)
        assertEquals(listOf("with" to listOf(hc.name)), asked.toList())
    }

    @Test fun nothingMissingAsksNothing() = runBlocking {
        board(granted = true).set("with", true)
        delay(200)
        assertEquals(emptyList<Any>(), asked.toList())
    }
}
