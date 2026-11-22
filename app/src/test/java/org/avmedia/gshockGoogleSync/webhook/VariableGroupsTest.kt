package org.avmedia.gshockGoogleSync.webhook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VariableGroupsTest {
    private fun v(name: String, description: String = "") = Variable(name, description) { null }

    @Test fun namesFallIntoTheFourGroups() {
        assertEquals(VarGroup.WATCH, VariableGroups.of("watch.battery"))
        assertEquals(VarGroup.WATCH, VariableGroups.of("alarms.list"))
        assertEquals(VarGroup.HEALTH, VariableGroups.of("health.steps_today"))
        assertEquals(VarGroup.PHONE, VariableGroups.of("phone.battery"))
        assertEquals(VarGroup.APP, VariableGroups.of("app.version"))
        assertEquals(VarGroup.APP, VariableGroups.of("event"))
        assertEquals(VarGroup.APP, VariableGroups.of("timestamp"))
    }

    @Test fun settingsAndDstStayWithTheWatch() {
        assertEquals(VarGroup.WATCH, VariableGroups.of("watch.settings.font"))
        assertEquals(VarGroup.WATCH, VariableGroups.of("watch.dst.home"))
        assertEquals(VarGroup.PHONE, VariableGroups.of("phone.dst"))
    }

    @Test fun blankQueryKeepsAllInGroupOrder() {
        val all = listOf(v("app.version"), v("watch.name"), v("phone.battery"), v("health.steps_today"))
        val grouped = VariableGroups.grouped(all, "  ")
        assertEquals(listOf(VarGroup.WATCH, VarGroup.HEALTH, VarGroup.PHONE, VarGroup.APP), grouped.map { it.first })
        assertEquals(4, grouped.sumOf { it.second.size })
    }

    @Test fun queryMatchesNameOrDescriptionAnyCase() {
        val all = listOf(v("watch.battery", "Charge level"), v("phone.battery", "Phone charge"), v("watch.name", "Name of the watch"))
        assertEquals(listOf("watch.battery", "phone.battery"), VariableGroups.grouped(all, "BATTERY").flatMap { it.second }.map { it.name })
        assertEquals(listOf("watch.battery", "phone.battery"), VariableGroups.grouped(all, "charge").flatMap { it.second }.map { it.name }.sorted().reversed())
        assertEquals(listOf(VarGroup.WATCH), VariableGroups.grouped(all, "name of").map { it.first })
    }

    @Test fun noMatchGivesNoGroups() {
        assertTrue(VariableGroups.grouped(listOf(v("watch.name")), "zzz").isEmpty())
    }
}
