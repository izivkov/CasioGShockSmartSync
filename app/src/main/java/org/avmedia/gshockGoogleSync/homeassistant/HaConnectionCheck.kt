package org.avmedia.gshockGoogleSync.homeassistant

import com.google.gson.JsonParser

/** What the "is this Home Assistant and does it accept the token" check found. */
enum class HaCheck { OK, WRONG_TOKEN, CANNOT_REACH, NOT_HOME_ASSISTANT, CLEARTEXT_BLOCKED }

/** Turns the answer of GET /api/ into one of [HaCheck] and plain words for it. Pure. */
object HaConnectionCheck {
    fun classify(r: HaResponse): HaCheck {
        val s = r.status
        return when {
            s == null -> if (r.body.contains("cleartext", ignoreCase = true)) HaCheck.CLEARTEXT_BLOCKED else HaCheck.CANNOT_REACH
            s == 401 || s == 403 -> HaCheck.WRONG_TOKEN
            s >= 500 -> HaCheck.CANNOT_REACH
            s in 200..299 && saysApiRunning(r.body) -> HaCheck.OK
            else -> HaCheck.NOT_HOME_ASSISTANT
        }
    }

    fun message(check: HaCheck): String = when (check) {
        HaCheck.OK -> "Home Assistant answered. The address and the token work."
        HaCheck.WRONG_TOKEN -> "Home Assistant did not accept the token. Create a new token and paste it again."
        HaCheck.CANNOT_REACH -> "Cannot reach Home Assistant. Check the address, and that this phone can connect to it."
        HaCheck.NOT_HOME_ASSISTANT -> "Something answered at this address, but it is not Home Assistant. Check the address."
        HaCheck.CLEARTEXT_BLOCKED -> "Android blocks plain http:// addresses in this app. Use an https:// address."
    }

    /** The technical side for a "Details" expander: the status code or the error text. */
    fun detail(r: HaResponse): String = r.status?.let { "HTTP $it" } ?: r.body.take(160)

    private fun saysApiRunning(body: String): Boolean = runCatching {
        JsonParser.parseString(body).asJsonObject.get("message").isJsonPrimitive
    }.getOrDefault(false)
}
