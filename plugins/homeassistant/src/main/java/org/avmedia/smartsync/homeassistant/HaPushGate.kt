package org.avmedia.smartsync.homeassistant

/**
 * A Home Assistant message (a notification or a command) sent to one watch's notify action acts only when that watch is
 * the connected one. Any other is dropped with one App log line (never the message text): another watch's watch face,
 * settings or notifications must not reach the watch that happens to be connected.
 */
class HaPushGate(
    private val store: HaStore,
    private val connected: () -> String,
    private val log: (String) -> Unit,
) {
    fun accept(webhookId: String, push: HaPushEvent.Push): Boolean {
        val id = store.idOfWebhook(webhookId)
        val now = connected()
        if (id != null && id == now) return true
        val name = id?.let { store.nameInHa(it) } ?: "a watch that is not registered here"
        val what = push.command ?: "Notification ${push.message}" // RED CHECK
        log("Home Assistant: $what for $name dropped, " + if (now.isBlank()) "no watch is connected" else "it is not the connected watch")
        return false
    }
}
