package org.avmedia.gshockGoogleSync.homeassistant

/**
 * The generic HTTP calls feature was removed (Home Assistant can call anything). Its stored list,
 * editor draft, results, queued bodies, backups and switches are dropped quietly once; nothing else is touched.
 */
object RemovedFeatureCleanup {
    private const val PREFIX = "webhooks."
    private val EXACT = setOf("toggle.webhooks", "WebhookAction.enabled")

    fun isRemoved(key: String): Boolean = key.startsWith(PREFIX) || key in EXACT
}
