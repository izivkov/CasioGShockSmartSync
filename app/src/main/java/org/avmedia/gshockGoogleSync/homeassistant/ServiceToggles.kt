package org.avmedia.gshockGoogleSync.homeassistant

/** The Home Assistant master switch on the Actions screen. On by default; the setup is kept while it is off. */
class ServiceToggles(private val store: KeyValueStore) {
    enum class Service(val key: String) { HOME_ASSISTANT("toggle.home_assistant") }

    fun isOn(service: Service): Boolean = store.get(service.key) != "off"

    fun set(service: Service, on: Boolean) = store.put(service.key, if (on) "on" else "off")
}
