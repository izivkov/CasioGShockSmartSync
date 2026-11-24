package org.avmedia.smartsync.homeassistant

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Writes a dashboard config as YAML for "Raw configuration editor" in Home Assistant. Only what a dashboard uses. */
object HaYaml {
    fun dump(config: JsonObject): String = StringBuilder().also { write(it, config, 0) }.toString()

    private fun write(out: StringBuilder, e: JsonElement, indent: Int) {
        val pad = "  ".repeat(indent)
        when (e) {
            is JsonObject -> e.entrySet().forEach { (k, v) ->
                when {
                    v is JsonObject && v.size() > 0 -> { out.append(pad).append(k).append(":\n"); write(out, v, indent + 1) }
                    v is JsonArray && v.size() > 0 -> { out.append(pad).append(k).append(":\n"); write(out, v, indent) }
                    else -> out.append(pad).append(k).append(": ").append(scalar(v)).append('\n')
                }
            }
            is JsonArray -> e.forEach { item ->
                if (item is JsonObject && item.size() > 0) {
                    val inner = StringBuilder().also { write(it, item, indent + 1) }.toString().lines().filter { it.isNotEmpty() }
                    inner.forEachIndexed { i, line -> out.append(if (i == 0) pad + "- " + line.removePrefix("  ".repeat(indent + 1)) else line).append('\n') }
                } else out.append(pad).append("- ").append(scalar(item)).append('\n')
            }
            else -> out.append(pad).append(scalar(e)).append('\n')
        }
    }

    private fun scalar(e: JsonElement): String = when {
        e is JsonObject -> "{}"
        e is JsonArray -> "[]"
        e.isJsonNull -> "null"
        e.asJsonPrimitive.isNumber || e.asJsonPrimitive.isBoolean -> e.asString
        else -> quote(e.asString)
    }

    private fun quote(s: String): String = if (Regex("[A-Za-z0-9_./-][A-Za-z0-9_./ -]*").matches(s) && s.lowercase() !in RESERVED && s.toDoubleOrNull() == null) s else "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private val RESERVED = setOf("true", "false", "null", "yes", "no", "on", "off", "~")
}
