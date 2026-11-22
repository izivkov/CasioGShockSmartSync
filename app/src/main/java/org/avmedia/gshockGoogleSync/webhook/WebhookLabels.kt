package org.avmedia.gshockGoogleSync.webhook

/** Short plain texts of the list cards. */
object WebhookLabels {
    /** Only the host: the URL may carry a token in its path or query. */
    fun hostOf(url: String): String =
        url.substringAfter("://", url).substringBefore('/').substringBefore('?').ifBlank { "(no address)" }

    fun triggers(hook: Webhook): List<String> = buildList {
        if (hook.onWatchAction) add("Watch button")
        if (hook.onSync) add("After sync")
        if (hook.onHealthData) add("Health data")
    }

    /** [time] is already formatted; null result means never sent. */
    fun resultText(result: WebhookResult?, time: String?): String = when {
        result == null -> "Not sent yet"
        result.ok -> "Worked, $time"
        result.retrying -> "Failed, trying again, $time"
        else -> "Failed, $time"
    }
}
