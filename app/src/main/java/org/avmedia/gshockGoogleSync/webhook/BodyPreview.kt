package org.avmedia.gshockGoogleSync.webhook

/** The body as it would be sent, for the editor. Never an error: a value that is not known yet shows as <name>. */
object BodyPreview {
    fun render(template: String, json: Boolean, known: Set<String>, values: Map<String, Any?>): String {
        val used = TemplateRenderer.namesIn(template).filter { it != TemplateRenderer.ALL }
        val shown = LinkedHashMap(values)
        used.filter { shown[it] == null }.forEach { shown[it] = "<$it>" }
        return TemplateRenderer.render(template, shown, known + used, json).text
    }
}
