package org.avmedia.gshockGoogleSync.homeassistant

import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap

/**
 * A re-paired watch gets a new Bluetooth address, and the registrations are kept per address. This finds the
 * registration of the same watch under its old address and moves it, so HA keeps one device (its notify action and
 * entity ids) instead of the sync stopping. Android-free: the paired list and the other stores come in as lambdas.
 */
class HaCarryOver(
    /** True while [address] (upper case, with colons) is still a paired watch of the app. */
    private val paired: (address: String) -> Boolean = { false },
    /** The watch name last seen at [address] (the home screen snapshot); null when not known. */
    private val watchNameAt: (address: String) -> String? = { null },
    /** Moves the other per-address stores (the home screen snapshot) from one address to the other. */
    private val moveOthers: (from: String, to: String) -> Unit = { _, _ -> },
    private val log: (String) -> Unit = {},
) {
    private val told = ConcurrentHashMap.newKeySet<String>()

    /**
     * Moves the registration of the same watch (same model, same watch name when known, old address no longer
     * paired, the only one that fits) to [address]. True when one was moved.
     */
    fun moveTo(store: HaStore, address: String, watchName: String, cfg: HaConfig): Boolean {
        val id = HaPayloads.deviceId(address)
        if (store.loadRegistration(id) != null) return false
        val model = HaPayloads.resolveNames(watchName, cfg.deviceName, cfg.model).second
        val candidates = store.registeredIds().filter { it != id }.filter { old ->
            val reg = store.loadRegistration(old) ?: return@filter false
            val oldAddress = addressOf(old) ?: return@filter false
            field(reg.registration, "model").equals(model, ignoreCase = true) &&
                !paired(oldAddress) &&
                watchNameAt(oldAddress).let { it.isNullOrBlank() || it == watchName }
        }
        val old = candidates.singleOrNull() ?: return false
        val name = field(store.loadRegistration(old)?.registration.orEmpty(), "device_name").ifBlank { watchName }
        store.moveRegistration(old, id)
        addressOf(old)?.let { runCatching { moveOthers(it, address) } }
        told.remove(id)
        log("$name re-paired, registration moved")
        return true
    }

    /** The reason a sync was skipped, logged once per watch (it was silent before). */
    fun skipped(deviceId: String, text: String) {
        if (told.add(deviceId)) log(text)
    }

    companion object {
        /** "gshock-aabbccddeeff" gives "AA:BB:CC:DD:EE:FF"; null for an id that holds no address. */
        fun addressOf(deviceId: String): String? {
            val hex = deviceId.removePrefix("gshock-")
            if (hex.length != 12 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            return hex.uppercase().chunked(2).joinToString(":")
        }

        private fun field(body: String, name: String): String = runCatching {
            JsonParser.parseString(body).asJsonObject.get(name)?.takeIf { it.isJsonPrimitive }?.asString
        }.getOrNull().orEmpty()
    }
}
