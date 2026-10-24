package org.avmedia.gshockGoogleSync.remote

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.avmedia.gshockGoogleSync.BuildConfig
import org.avmedia.gshockapi.ProgressEvents
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BleProxy — WebSocket outbound client connection to the Python bridge server.
 *
 * Transports GATT writes and notifications between the local watch BLE link and the Python server.
 *
 * Protocol (WebSocket JSON):
 *   PC → App:  {"type": "write", "handle": "0x0E", "payload": "hexdata"}
 *   PC → App:  {"type": "event", "name": "AlarmsUpdated", "payload": ...}
 *   PC → App:  {"type": "prompt", "text": "...", "reconnect": true|false}
 *   PC → App:  {"type": "disconnect"}
 *   App → PC:  {"type": "notification", "payload": "hexdata"}
 *   App → PC:  {"type": "connected", "address": "..."}
 *   App → PC:  {"type": "log", "ts": "...", "msg": "..."}
 */
@Singleton
class BleProxy @Inject constructor() {

    // Args may hold Kotlin data classes (Alarm, Event, Settings). JSONObject(Map) can only wrap
    // JDK types and turns anything else into null, so the args are serialized with Gson instead.
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var running = true

    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val reconnectDelayMs = 5_000L

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<Any?>>()

    @Volatile
    private var watchReady = false

    @Volatile
    private var watchAddress = ""

    @Volatile
    private var watchName = ""

    var onWriteReceived: ((handle: Int, data: ByteArray) -> Unit)? = null
    var onDisconnectRequested: (() -> Unit)? = null

    fun connect(host: String = BuildConfig.BRIDGE_HOST, port: Int = BuildConfig.BRIDGE_PORT) {
        if (!running) return
        val resolvedHost = if ((host == "127.0.0.1" || host.isEmpty()) && isEmulator()) "10.0.2.2" else if (host.isEmpty()) "127.0.0.1" else host
        val url = BuildConfig.BRIDGE_URL.ifEmpty { "ws://$resolvedHost:$port" }
        Timber.i("Connecting to bridge WebSocket $url …")

        val request = Request.Builder().url(url).apply {
            // Cloudflare Access service token, when configured. The secret is never logged.
            if (BuildConfig.CF_ACCESS_CLIENT_ID.isNotEmpty() && BuildConfig.CF_ACCESS_CLIENT_SECRET.isNotEmpty()) {
                addHeader("CF-Access-Client-Id", BuildConfig.CF_ACCESS_CLIENT_ID)
                addHeader("CF-Access-Client-Secret", BuildConfig.CF_ACCESS_CLIENT_SECRET)
            }
        }.build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.i("WebSocket bridge established")
                ProgressEvents.onNext("BridgeConnected")
                if (watchReady) sendConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Timber.i("WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.i("WebSocket closed: $code $reason")
                ProgressEvents.onNext("BridgeDisconnected")
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e("WebSocket failure: ${t.message} — retrying in ${reconnectDelayMs}ms")
                ProgressEvents.onNext("BridgeDisconnected")
                scheduleReconnect()
            }
        })
    }

    private fun isEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.FINGERPRINT.startsWith("unknown") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK built for x86") ||
                android.os.Build.HARDWARE.contains("goldfish") ||
                android.os.Build.HARDWARE.contains("ranchu") ||
                android.os.Build.PRODUCT.contains("sdk") ||
                android.os.Build.PRODUCT.contains("sdk_gphone") ||
                android.os.Build.HARDWARE.contains("vbox86") ||
                android.os.Build.MODEL.contains("vbox86p")
    }

    private fun scheduleReconnect() {
        if (!running) return
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectHandler.postDelayed({
            if (running) {
                connect()
            }
        }, reconnectDelayMs)
    }

    suspend fun sendCommand(command: String, args: Map<String, Any> = emptyMap()): Any? {
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Any?>()
        pendingRequests[id] = deferred

        val json = JSONObject().apply {
            put("type", "command")
            put("id", id)
            put("command", command)
            put("args", JSONObject(gson.toJson(args)))
        }

        if (!sendJson(json)) {
            pendingRequests.remove(id)
            Timber.w("Command $command not sent: bridge not connected")
            return null
        }

        return try {
            withTimeout(15_000L) {
                deferred.await()
            }
        } catch (e: Exception) {
            pendingRequests.remove(id)
            Timber.e(e, "Command $command timed out or failed")
            null
        }
    }

    fun onWatchConnected(address: String = "", name: String = "") {
        watchReady = true
        watchAddress = address
        watchName = name
        sendConnected()
        Timber.i("Sent 'connected' signal for watch: ${address.ifEmpty { name }}")
    }

    fun onWatchDisconnected() {
        watchReady = false
        sendJson(JSONObject().apply { put("type", "disconnected") })
        Timber.i("Sent 'disconnected' signal to bridge")
    }

    private fun sendConnected() {
        sendJson(JSONObject().apply {
            put("type", "connected")
            put("address", watchAddress)
            if (watchName.isNotEmpty()) put("name", watchName)
        })
    }

    fun onNotification(data: ByteArray, characteristicUuid: String? = null) {
        sendJson(JSONObject().apply {
            put("type", "notification")
            put("payload", data.toHex())
            if (characteristicUuid != null) put("uuid", characteristicUuid)
        })
    }

    fun log(message: String) {
        sendJson(JSONObject().apply {
            put("type", "log")
            put("ts", Instant.now().toString())
            put("msg", message)
        })
    }

    fun disconnect() {
        running = false
        reconnectHandler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "App closing")
        webSocket = null
        Timber.i("Disconnected from bridge")
        ProgressEvents.onNext("BridgeDisconnected")
    }

    private fun handleMessage(raw: String) {
        val msg = try {
            JSONObject(raw)
        } catch (e: Exception) {
            Timber.w("Malformed JSON: $raw")
            return
        }

        when (val type = msg.optString("type")) {
            "response" -> {
                val id = msg.optString("id")
                val deferred = pendingRequests.remove(id)
                if (deferred != null) {
                    val ok = msg.optBoolean("ok", false)
                    if (ok) {
                        val result = msg.opt("result")
                        deferred.complete(if (result == JSONObject.NULL) null else result)
                    } else {
                        val error = msg.optString("error", "Unknown error")
                        deferred.completeExceptionally(Exception(error))
                    }
                }
            }

            "write" -> {
                val handleStr = msg.getString("handle")
                val handle = if (handleStr.startsWith("0x") || handleStr.startsWith("0X"))
                    handleStr.substring(2).toInt(16)
                else
                    handleStr.toInt()
                val data = msg.getString("payload").hexToByteArray()
                Timber.d("write handle=0x${handle.toString(16).uppercase()}")
                onWriteReceived?.invoke(handle, data)
            }

            "event" -> {
                val eventName = msg.optString("name")
                if (eventName.isNotEmpty()) {
                    val payload = msg.opt("payload")
                    Timber.d("Received event from Python: $eventName")
                    ProgressEvents.onNext(eventName, payload)
                }
            }

            "prompt" -> {
                val text = msg.optString("text", "")
                Timber.i("Prompt received from Python: $text")
                ProgressEvents.onNext("PromptReceived", text)
            }

            "disconnect" -> {
                Timber.i("Disconnect requested by Python")
                onDisconnectRequested?.invoke()
                disconnect()
            }

            else -> Timber.w("Unknown message type: $type")
        }
    }

    private fun sendJson(obj: JSONObject): Boolean {
        val ws = webSocket ?: run {
            Timber.w("sendJson: WebSocket not connected")
            return false
        }
        return ws.send(obj.toString())
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexToByteArray() =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
