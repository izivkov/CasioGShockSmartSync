package org.avmedia.gshockGoogleSync.watch

import org.avmedia.gshockGoogleSync.homeassistant.HaPayloads

/**
 * The key every per-watch store uses: the id Home Assistant knows the watch by ("gshock-aabbccddeeff", from its stored
 * address). A re-paired watch gets a new address; its data moves to the new key ([WatchStore.move]).
 */
object WatchKeys {
    /** The key of the watch at [address]; "" for no address. */
    fun of(address: String): String = if (address.isBlank()) "" else HaPayloads.deviceId(address)

    /** The store key of [name] for [watch]: "name@gshock-aabbccddeeff". */
    fun scoped(name: String, watch: String): String = name

    /** True for a store key that belongs to [watch]. */
    fun belongsTo(storeKey: String, watch: String): Boolean = watch.isNotBlank() && storeKey.endsWith("@$watch")
}
