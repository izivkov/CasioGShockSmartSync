package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** (De)serialisation of what is stored. Bad or missing data gives empty values, never a crash. */
object WebhookJson {
    private val gson = Gson()
    private val hooksType = object : TypeToken<List<Webhook>>() {}.type
    private val resultsType = object : TypeToken<Map<String, WebhookResult>>() {}.type

    fun hooksToJson(hooks: List<Webhook>): String = gson.toJson(hooks)

    fun hooksFromJson(json: String?): List<Webhook> = parseHooks(json) ?: emptyList()

    /** The stored list, or null when [json] cannot be read (callers must not treat that as "no webhooks"). */
    fun parseHooks(json: String?): List<Webhook>? =
        parse<List<Webhook?>>(json, hooksType)?.filterNotNull()

    fun resultsToJson(results: Map<String, WebhookResult>): String = gson.toJson(results)

    fun resultsFromJson(json: String?): Map<String, WebhookResult> = parse(json, resultsType) ?: emptyMap()

    private fun <T> parse(json: String?, type: java.lang.reflect.Type): T? {
        if (json.isNullOrBlank()) return null
        return try {
            gson.fromJson<T>(json, type)
        } catch (e: Exception) {
            null
        }
    }
}
