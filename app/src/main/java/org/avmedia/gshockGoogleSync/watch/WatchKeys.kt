package org.avmedia.gshockGoogleSync.watch

/**
 * The key every per-watch store uses: the id Home Assistant knows the watch by ("gshock-aabbccddeeff", from its stored
 * address). A re-paired watch gets a new address; its data moves to the new key ([WatchStore.move]).
 */
object WatchKeys {
    /** The key of the watch at [address]; "" for no address. */
    fun of(address: String): String = if (address.isBlank()) "" else "gshock-" + address.filter { it.isLetterOrDigit() }.lowercase()

    /** "gshock-aabbccddeeff" gives "AA:BB:CC:DD:EE:FF"; null for a key that holds no address. */
    fun addressOf(key: String): String? {
        val hex = key.removePrefix("gshock-")
        if (hex.length != 12 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return hex.uppercase().chunked(2).joinToString(":")
    }

    /** The store key of [name] for [watch]: "name@gshock-aabbccddeeff". */
    fun scoped(name: String, watch: String): String = "$name@$watch"

    /** True for a store key that belongs to [watch]. */
    fun belongsTo(storeKey: String, watch: String): Boolean = watch.isNotBlank() && storeKey.endsWith("@$watch")
}
