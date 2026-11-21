package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.GsonBuilder

/**
 * Fills `{{variable}}` placeholders of a webhook body.
 *
 * - JSON content type: `{{name}}` becomes a JSON value (strings quoted and escaped, missing values `null`).
 * - Any other content type: the plain text of the value (missing values become empty).
 * - `{{all}}` becomes every available variable as one JSON object, nested by the dots in the names.
 *
 * Names that are not in [known] are rendered as missing and listed in [Rendered.unknown], so the
 * Test button can report them. Known names without a value are listed in [Rendered.missing].
 */
object TemplateRenderer {
    const val ALL = "all"

    private val placeholder = Regex("""\{\{\s*([A-Za-z0-9_.\-]+)\s*}}""")
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    data class Rendered(
        val text: String,
        /** Names in the template that no variable has. */
        val unknown: List<String>,
        /** Known names that had no value this time (for example a watch that does not support it). */
        val missing: List<String>,
        /** Variables whose provider threw, with the reason. Filled in by the registry. */
        val failed: List<String> = emptyList(),
    )

    fun isJsonContentType(contentType: String): Boolean {
        val type = contentType.substringBefore(';').trim().lowercase()
        return type == "application/json" || type.endsWith("+json") || type.endsWith("/json")
    }

    /** The variable names a template uses, in order, without duplicates. */
    fun namesIn(template: String): List<String> =
        placeholder.findAll(template).map { it.groupValues[1] }.distinct().toList()

    fun render(
        template: String,
        values: Map<String, Any?>,
        known: Set<String>,
        json: Boolean,
    ): Rendered {
        val unknown = LinkedHashSet<String>()
        val missing = LinkedHashSet<String>()
        val text = placeholder.replace(template) { match ->
            val name = match.groupValues[1]
            when {
                name == ALL -> allAsJson(values)
                name !in known -> {
                    unknown += name
                    if (json) "null" else ""
                }
                else -> {
                    val value = values[name]
                    if (value == null) missing += name
                    if (json) jsonValue(value) else plainText(value)
                }
            }
        }
        return Rendered(text, unknown.toList(), missing.toList())
    }

    /** All non-null values as one JSON object, nested by the dots in the names. */
    fun allAsJson(values: Map<String, Any?>): String = gson.toJson(nest(values))

    internal fun nest(values: Map<String, Any?>): Map<String, Any?> {
        val root = LinkedHashMap<String, Any?>()
        for ((name, value) in values.toSortedMap()) {
            if (value == null) continue
            val parts = name.split('.')
            var node = root
            var ok = true
            for (part in parts.dropLast(1)) {
                val next = node.getOrPut(part) { LinkedHashMap<String, Any?>() }
                @Suppress("UNCHECKED_CAST")
                if (next is LinkedHashMap<*, *>) node = next as LinkedHashMap<String, Any?>
                else {
                    ok = false // a name is both a value and a parent: keep the shorter one
                    break
                }
            }
            if (ok && node[parts.last()] == null) node[parts.last()] = value
        }
        return root
    }

    private fun jsonValue(value: Any?): String = gson.toJson(value)

    private fun plainText(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> gson.toJson(value)
    }
}
