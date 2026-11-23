package org.avmedia.gshockGoogleSync.webhook

import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.avmedia.gshockapi.log.LogSource

/** [status] is null when no response arrived (offline, timeout). */
data class HaResponse(val status: Int?, val body: String)

/** One JSON POST. A fake in the unit tests; [HaOkHttpTransport] on the phone. */
fun interface HaTransport {
    fun post(url: String, body: String, bearer: String?): HaResponse

    /** One GET (the URL and token check). */
    fun get(url: String, bearer: String?): HaResponse = HaResponse(null, "GET is not supported")
}

/** Never logs the URL, the body or the token. One try per call. */
object HaOkHttpTransport : HaTransport {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    override fun get(url: String, bearer: String?): HaResponse = try {
        val builder = Request.Builder().url(url).get()
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        client.newCall(builder.build()).execute().use { HaResponse(it.code, it.body?.string().orEmpty()) }
    } catch (e: Throwable) {
        HaResponse(null, WebhookDiagnostics.oneLine(e))
    }

    override fun post(url: String, body: String, bearer: String?): HaResponse = try {
        val builder = Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType()))
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        client.newCall(builder.build()).execute().use { HaResponse(it.code, it.body?.string().orEmpty()) }
    } catch (e: Throwable) {
        HaResponse(null, WebhookDiagnostics.oneLine(e))
    }
}

/**
 * Wraps [HaOkHttpTransport] for the background worker: retries a post like [CallRetry] says and logs to the app log as "Home Assistant" with the kind of call only. A 410
 * is not retried and not logged: [HaClient] sends it to the re-register check. A GET (the URL and token check of the
 * setup screen) is one try, so the user sees the first answer.
 */
class RetryingHaTransport(private val inner: HaTransport) : HaTransport {
    override fun get(url: String, bearer: String?): HaResponse = inner.get(url, bearer)

    override fun post(url: String, body: String, bearer: String?): HaResponse =
        CallRetry.run(LogSource.HomeAssistant, "Home Assistant ${kindOf(url, body)}", quiet = setOf(410)) {
            val r = inner.post(url, body, bearer)
            CallRetry.Attempt(r, r.status, if (r.status == null) noAnswerReason(r.body) else null)
        }

    companion object {
        private val TYPE = Regex(""""type"\s*:\s*"(\w+)"""")

        /** What the call is for, never the URL: register, update sensors, fire event, check registration, action. */
        fun kindOf(url: String, body: String): String {
            if (url.endsWith("/api/mobile_app/registrations")) return "register"
            return when (TYPE.find(body)?.groupValues?.get(1)) {
                "register_sensor", "update_registration" -> "register"
                "update_sensor_states" -> "update sensors"
                "fire_event" -> "fire event"
                "get_config" -> "check registration"
                else -> "action"
            }
        }

        /** The transport's message starts with the exception class name (see [WebhookDiagnostics.oneLine]). */
        fun noAnswerReason(message: String): String = when {
            Regex("Unknown|Connect|NoRoute").containsMatchIn(message.substringBefore(':')) -> "host not reachable"
            Regex("Timeout|Interrupted").containsMatchIn(message.substringBefore(':')) -> "timeout"
            message.substringBefore(':').contains("SSL") -> "TLS error"
            else -> "no answer"
        }
    }
}

