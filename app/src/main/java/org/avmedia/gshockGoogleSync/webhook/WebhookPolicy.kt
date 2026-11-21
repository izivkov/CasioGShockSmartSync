package org.avmedia.gshockGoogleSync.webhook

/** Pure rules: when a webhook may fire and when a failed send is tried again. */
object WebhookPolicy {
    const val MAX_ATTEMPTS = 3
    const val RESPONSE_PREVIEW_CHARS = 300

    /** True when [hook] reacts to [trigger] and its minimum interval since [lastAttemptMillis] has passed. */
    fun shouldFire(
        hook: Webhook,
        trigger: WebhookTrigger,
        lastAttemptMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        if (!hook.enabled || hook.url.isBlank() || !hook.firesOn(trigger)) return false
        if (trigger == WebhookTrigger.TEST) return true
        val intervalMs = hook.minIntervalMinutes.coerceAtLeast(0) * 60_000L
        return lastAttemptMillis == null || nowMillis - lastAttemptMillis >= intervalMs
    }

    /** No response (offline, timeout, DNS) or a 5xx/429/408 answer is worth another try; other answers are final. */
    fun isRetryable(status: Int?): Boolean =
        status == null || status in 500..599 || status == 429 || status == 408

    fun preview(body: String?): String = (body ?: "").take(RESPONSE_PREVIEW_CHARS)
}
