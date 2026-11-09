package org.avmedia.gshockGoogleSync.session

enum class EntryReason {
    NORMAL,
    CONTENT_SELECTOR,
    RUN_ACTIONS
}

sealed interface SessionPhase {
    data object Disconnected : SessionPhase
    data object Connecting : SessionPhase
    data class Ready(val entryReason: EntryReason = EntryReason.NORMAL) : SessionPhase
    data class Closed(val reason: String = "") : SessionPhase
}

data class WatchSnapshot(
    val name: String = "",
    val address: String = "",
    val model: String = ""
)

data class SessionState(
    val phase: SessionPhase = SessionPhase.Disconnected,
    val trigger: ConnectionTrigger = ConnectionTrigger.NORMAL,
    val watch: WatchSnapshot = WatchSnapshot(),
    val pendingSaves: Set<ComponentId> = emptySet(),
    val errorMessage: String? = null
) {
    val isNormalConnection: Boolean get() = trigger == ConnectionTrigger.NORMAL
    val isActionButton: Boolean get() = trigger == ConnectionTrigger.ACTION_BUTTON
    val isAutoTime: Boolean get() = trigger == ConnectionTrigger.AUTO_TIME
    val isFindPhone: Boolean get() = trigger == ConnectionTrigger.FIND_PHONE
    val isAlwaysConnected: Boolean get() = trigger == ConnectionTrigger.ALWAYS_CONNECTED

    fun isPendingSave(component: ComponentId): Boolean = component in pendingSaves
    fun hasPendingSaves(): Boolean = pendingSaves.isNotEmpty()
}
