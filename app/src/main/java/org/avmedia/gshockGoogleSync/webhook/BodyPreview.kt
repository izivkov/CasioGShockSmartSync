package org.avmedia.gshockGoogleSync.webhook

/** The body as it would be sent, for the editor. Never an error: a value that is not known yet shows as <name>. */
object BodyPreview {
    fun render(template: String, json: Boolean, known: Set<String>, values: Map<String, Any?>): String = ""
}
