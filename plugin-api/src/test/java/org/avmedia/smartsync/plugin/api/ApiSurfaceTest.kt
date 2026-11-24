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
        assertEquals(sortedSetOf("buttonPress", "healthReads", "values", "wakeEveryMinutes"), fields)
    }
}
