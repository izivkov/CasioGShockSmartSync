package org.avmedia.gshockGoogleSync.webhook

/** The two master switches on the Actions screen. On by default; each service keeps its own settings when off. */
class ServiceToggles(private val store: KeyValueStore) {
    enum class Service(val key: String) { WEBHOOKS("toggle.webhooks"), HOME_ASSISTANT("toggle.home_assistant") }

    fun isOn(service: Service): Boolean = store.get(service.key) != "off"

    fun set(service: Service, on: Boolean) = store.put(service.key, if (on) "on" else "off")
}
