package org.avmedia.gshockGoogleSync.webhook

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** One answer of the Home Assistant websocket API. [code] is HA's error code ("unauthorized", "not_found") when [ok] is false. */
data class WsReply(val ok: Boolean, val result: JsonElement? = null, val code: String? = null, val message: String? = null)

/** Raised when the socket cannot be opened or the token is refused ([authRefused]). */
class HaWsException(message: String, val authRefused: Boolean = false) : Exception(message)

/** One open, authenticated websocket session. A fake in the unit tests. */
interface HaWsSession : AutoCloseable {
    fun call(type: String, payload: JsonObject = JsonObject()): WsReply
}

fun interface HaWsConnector {
    /** @throws HaWsException when the connection or the login fails. */
    fun connect(baseUrl: String, token: String): HaWsSession
}

/** Never logs the URL, the messages or the token. */
object HaOkHttpWs : HaWsConnector {
    private const val STEP_SECONDS = 20L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    }

    /** "https://ha.example/" gives "wss://ha.example/api/websocket". */
    fun socketUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/').replaceFirst("http", "ws") + "/api/websocket"

    override fun connect(baseUrl: String, token: String): HaWsSession {
        val inbox = LinkedBlockingQueue<Any>()
        val socket = client.newWebSocket(Request.Builder().url(socketUrl(baseUrl)).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { inbox.put(text) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { inbox.put(t) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { inbox.put(IllegalStateException("closed")) }
        })
        fun next(): JsonObject {
            val m = inbox.poll(STEP_SECONDS, TimeUnit.SECONDS) ?: throw HaWsException("Home Assistant did not answer in time")
            if (m is Throwable) throw HaWsException(WebhookDiagnostics.oneLine(m))
            return JsonParser.parseString(m as String).asJsonObject
        }
        try {
            next() // auth_required
            socket.send(JsonObject().apply { addProperty("type", "auth"); addProperty("access_token", token) }.toString())
            val reply = next()
            if (reply.get("type")?.asString != "auth_ok") throw HaWsException("Home Assistant refused the token", authRefused = true)
        } catch (e: Throwable) {
            socket.cancel()
            throw e
        }
        return object : HaWsSession {
            private var id = 0
            override fun call(type: String, payload: JsonObject): WsReply {
                val own = ++id
                val msg = payload.deepCopy().apply { addProperty("id", own); addProperty("type", type) }
                if (!socket.send(msg.toString())) throw HaWsException("The connection to Home Assistant closed")
                while (true) {
                    val r = next()
                    if (r.get("id")?.asInt != own || r.get("type")?.asString != "result") continue
                    val err = r.getAsJsonObject("error")
                    return WsReply(
                        ok = r.get("success")?.asBoolean == true,
                        result = r.get("result"),
                        code = err?.get("code")?.asString,
                        message = err?.get("message")?.asString,
                    )
                }
            }

            override fun close() { socket.close(1000, null) }
        }
    }
}
