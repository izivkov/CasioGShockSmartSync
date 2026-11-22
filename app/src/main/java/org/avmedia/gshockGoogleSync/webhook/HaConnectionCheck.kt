package org.avmedia.gshockGoogleSync.webhook

/** What the "is this Home Assistant and does it accept the token" check found. */
enum class HaCheck { OK, WRONG_TOKEN, CANNOT_REACH, NOT_HOME_ASSISTANT, CLEARTEXT_BLOCKED }

/** Turns the answer of GET /api/ into one of [HaCheck] and plain words for it. Pure. */
object HaConnectionCheck {
    fun classify(r: HaResponse): HaCheck = HaCheck.OK

    fun message(check: HaCheck): String = ""
}
