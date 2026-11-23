package org.avmedia.gshockGoogleSync.webhook

import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.avmedia.gshockapi.log.LogSource

/**
 * Makes the HTTP call. Never logs the URL query or the headers: they may hold tokens.
 */
object WebhookSender {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** What happened: [status] is null when no response arrived. */
    data class Outcome(val status: Int?, val message: String) {
        val ok: Boolean get() = status != null && status in 200..299
    }

    /**
     * Sends [body] with [hook]'s settings, tried again on a network error, timeout, 408, 429 or 5xx ([CallRetry]).
     * Blocking: call from a background thread. [retries] false is for the Test button, which shows the first answer.
     */
    fun send(hook: Webhook, body: String, retries: Boolean = true): Outcome =
        CallRetry.run(LogSource.Webhook, "Webhook '${hook.name}' (${hostOf(hook.url)})", retries) {
            val (o, error) = sendOnce(hook, body)
            CallRetry.Attempt(o, o.status, error?.let { CallRetry.reasonOf(null, it) })
        }

    /** The host only: the query, user info and path may hold secrets. */
    fun hostOf(url: String): String = url.trim().toHttpUrlOrNull()?.host ?: "invalid URL"

    private fun sendOnce(hook: Webhook, body: String): Pair<Outcome, Throwable?> = try {
        client.newCall(buildRequest(hook, body)).execute().use { response ->
            Outcome(response.code, WebhookPolicy.preview(response.body?.string())) to null
        }
    } catch (e: Throwable) {
        // Throwable on purpose: a class missing on the device is an Error and must be shown, not crash.
        WebhookDiagnostics.logError("Send failed: ${WebhookDiagnostics.describe(e)}")
        Outcome(null, WebhookDiagnostics.oneLine(e)) to e
    }

    internal fun buildRequest(hook: Webhook, body: String): Request {
        val builder = Request.Builder().url(hook.url.trim())
        val method = hook.method.uppercase()
        val mediaType = hook.contentType.toMediaTypeOrNull()
        when (method) {
            "GET" -> builder.get()
            "PUT" -> builder.put(body.toRequestBody(mediaType))
            else -> builder.post(body.toRequestBody(mediaType))
        }
        hook.headers
            .filter { it.key.isNotBlank() }
            .forEach { builder.header(it.key.trim(), it.value.trim()) }
        return builder.build()
    }
}
