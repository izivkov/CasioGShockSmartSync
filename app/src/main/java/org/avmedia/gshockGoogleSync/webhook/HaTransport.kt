package org.avmedia.gshockGoogleSync.webhook

import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** [status] is null when no response arrived (offline, timeout). */
data class HaResponse(val status: Int?, val body: String)

/** One JSON POST. A fake in the unit tests; [HaOkHttpTransport] on the phone. */
fun interface HaTransport {
    fun post(url: String, body: String, bearer: String?): HaResponse

    /** One GET (the URL and token check). */
    fun get(url: String, bearer: String?): HaResponse = HaResponse(null, "GET is not supported")
}

/** Never logs the URL, the body or the token. */
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
