package org.avmedia.gshockGoogleSync.plugins

/**
 * Finds the watch a new watch id re-pairs: a re-paired watch gets a new Bluetooth address, so its data stays under the
 * old id. The old id is the only known one that is no longer paired and was last seen with the same watch name; with
 * none or several, nothing moves. Android-free.
 */
object RePair {
    /**
     * The old id of [newKey] (named [name] now), or null. [known]: the ids the app saw at a connection; [paired]: the
     * ids paired now; [nameOf]: the name last seen for an id.
     */
    fun find(newKey: String, name: String, known: List<String>, paired: Set<String>, nameOf: (String) -> String?): String? {
        if (name.isBlank() || newKey.isBlank()) return null
        val fits = known.distinct().filter { old -> old != newKey && nameOf(old) == name }
        return fits.singleOrNull()
    }
}
