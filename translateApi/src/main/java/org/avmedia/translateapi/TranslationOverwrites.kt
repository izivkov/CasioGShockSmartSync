package org.avmedia.translateapi

class TranslationOverwrites {
    private val resourceLocaleMap = mutableMapOf<ResourceLocaleKey, () -> String>()

    fun addAll(entries: Array<Pair<ResourceLocaleKey, () -> String>>) {
        entries.forEach { (key, value) ->
            resourceLocaleMap[key] = value
        }
    }

    fun add(key: ResourceLocaleKey, value: () -> String) {
        resourceLocaleMap[key] = value
    }

    operator fun get(key: ResourceLocaleKey): (() -> String)? {
        return resourceLocaleMap[key]
    }

    fun clear() {
        resourceLocaleMap.clear()
    }

    // For testing or accessing the map
    fun getMap(): Map<ResourceLocaleKey, () -> String> = resourceLocaleMap
}