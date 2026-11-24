package org.avmedia.smartsync.homeassistant

/** What a Home Assistant `command_watch_setting` (or the settings helper) holds: `{setting, value}` or `{settings: {key: value}}` (both may be present). */
object HaSettingRequest {
    fun requested(data: Map<String, Any?>): Map<String, Any?> {
        val all = LinkedHashMap<String, Any?>()
        (data["settings"] as? Map<*, *>)?.forEach { (k, v) -> if (k is String) all[k] = v }
        (data["setting"] as? String)?.let { all[it] = data["value"] }
        return all
    }
}
