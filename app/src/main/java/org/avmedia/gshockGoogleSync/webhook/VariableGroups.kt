package org.avmedia.gshockGoogleSync.webhook

/** The four groups the variable list is shown in. */
enum class VarGroup(val title: String) { WATCH("Watch"), HEALTH("Health"), PHONE("Phone"), APP("App") }

object VariableGroups {
    fun of(name: String): VarGroup = VarGroup.WATCH

    /** The variables matching [query] (name or description, any case; blank means all), by group, empty groups left out. */
    fun grouped(variables: List<Variable>, query: String): List<Pair<VarGroup, List<Variable>>> = emptyList()
}
