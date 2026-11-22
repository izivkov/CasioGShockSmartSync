package org.avmedia.gshockGoogleSync.webhook

/** The four groups the variable list is shown in. */
enum class VarGroup(val title: String) { WATCH("Watch"), HEALTH("Health"), PHONE("Phone"), APP("App") }

object VariableGroups {
    fun of(name: String): VarGroup = when {
        name.startsWith("health.") -> VarGroup.HEALTH
        name.startsWith("phone.") -> VarGroup.PHONE
        name.startsWith("watch.") || name.startsWith("alarms.") -> VarGroup.WATCH
        else -> VarGroup.APP // app.*, event, timestamp
    }

    /** The variables matching [query] (name or description, any case; blank means all), by group, empty groups left out. */
    fun grouped(variables: List<Variable>, query: String): List<Pair<VarGroup, List<Variable>>> {
        val q = query.trim().lowercase()
        val hits = variables.filter { q.isEmpty() || q in it.name.lowercase() || q in it.description.lowercase() }
        return VarGroup.entries.map { g -> g to hits.filter { of(it.name) == g } }.filter { it.second.isNotEmpty() }
    }
}
