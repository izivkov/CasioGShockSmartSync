package org.avmedia.smartsync.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The members of the interface, by name: a member that is added or removed shows here, so the CHANGELOG and the
 * compatibility rule (README) are looked at with it. The review (2026-11-24) removed moveWatchData, valueKeys and the
 * status line composable.
 */
class ApiSurfaceTest {
    private fun names(c: Class<*>) = c.declaredMethods.map { it.name }.filter { !it.contains('$') }.toSortedSet()

    @Test fun theHostHasExactlyTheseCalls() {
        assertEquals(
            sortedSetOf(
                "command", "connected", "current", "declareNeeds", "getApiVersion", "getAppVersion", "getContext", "history", "lastValues",
                "missingPermissions", "notify", "requestPermissions", "settingCatalog", "settings", "status", "storage", "valueCatalog", "watchName", "watches",
            ),
            names(PluginHost::class.java),
        )
    }

    @Test fun theUiHasNoStatusLine() {
        assertEquals(sortedSetOf("ActionsRow", "SettingsScreen", "getHasActionsRow", "getOpensOnTurnOn"), names(PluginUi::class.java))
    }

    @Test fun theNeedsHaveNoFlagThatDoesNothing() {
        val fields = PluginNeeds::class.java.declaredFields.map { it.name }.filter { !it.contains('$') }.toSortedSet()
        assertEquals(sortedSetOf("buttonPress", "healthReads", "settings", "values", "wakeEveryMinutes"), fields)
    }

    // M1: the plugin's own permission check may wait on a service (Health Connect), so it is a suspend call.
    @Test fun theOwnPermissionCheckSuspends() {
        val m = WatchPlugin::class.java.declaredMethods.single { it.name == "missingPermissions" }
        assertEquals("kotlin.coroutines.Continuation", m.parameterTypes.last().name)
    }

    // M3: a local time without offset has its own type.
    @Test fun aLocalTimeHasItsOwnType() {
        org.junit.Assert.assertTrue(ValueType.entries.map { it.name }.containsAll(listOf("DATE_TIME", "LOCAL_DATE_TIME")))
    }

    @Test fun thePluginTellsAboutItself() {
        val about = sortedSetOf("getAuthor", "getDescription", "getInfoUrl", "getIssuesUrl", "getLicense", "getSetupNeeds", "getSourceUrl", "getSummary")
        org.junit.Assert.assertTrue(names(WatchPlugin::class.java).containsAll(about))
    }
}
