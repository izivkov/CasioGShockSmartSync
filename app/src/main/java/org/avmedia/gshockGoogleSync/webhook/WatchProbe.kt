package org.avmedia.gshockGoogleSync.webhook

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.session.SessionPhase
import org.avmedia.gshockGoogleSync.session.WatchModelOverride
import org.avmedia.gshockapi.WatchInfo
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.ble.IDataReceived
import org.avmedia.gshockapi.casio.MessageDispatcher
import org.avmedia.gshockapi.io.IO

/**
 * Debug probe: sends Casio one-byte READ request codes to the request characteristic (26eb002c, the
 * library's GetSetMode.GET) and records every notification that comes back. Never writes settings.
 *
 * While a request is outstanding the answers are recorded here and not handed to the library (its
 * own handlers would treat some of them, such as 0x10, as real data). Everything else is passed on
 * as usual. When the watch drops (about every 45 s on an H5600) the probe waits for the next
 * connection and carries on with the same code.
 */
@Singleton
class WatchProbe @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: GShockRepository,
    private val sessionManager: SessionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _entries = MutableStateFlow<List<ProbeEntry>>(emptyList())
    val entries: StateFlow<List<ProbeEntry>> = _entries

    private val _status = MutableStateFlow("Not started")
    val status: StateFlow<String> = _status

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    @Volatile private var waiting = false
    @Volatile private var lastRxMillis = 0L
    private val buffer = ArrayList<String>()

    private val collector = IDataReceived { data ->
        if (data == null) return@IDataReceived
        if (waiting) {
            synchronized(buffer) { buffer.add(data) }
            lastRxMillis = System.currentTimeMillis()
        } else {
            forwardToLibrary(data)
        }
    }

    private fun forwardToLibrary(data: String) {
        try {
            MessageDispatcher.onReceived(data, WatchInfo.protocol)
        } catch (t: Throwable) {
            WebhookDiagnostics.logError("Probe: library handler failed: ${WebhookDiagnostics.oneLine(t)}")
        }
    }

    fun result(): ProbeResult = ProbeResult(
        watchName = WatchModelOverride.displayName(WatchInfo.name),
        phoneTime = OffsetDateTime.now().withNano(0).toString(),
        entries = _entries.value,
    )

    fun clear() {
        stop()
        _entries.value = emptyList()
        _status.value = "Not started"
    }

    /** Starts, or continues where it stopped (unanswered codes only). */
    fun start(all: Boolean) {
        if (job?.isActive == true) return
        val plan = ProbePlan.codes(all)
        val old = _entries.value
        // Keep finished entries; extend the plan (for example after switching on "all codes").
        _entries.value = plan.mapIndexed { i, code -> old.getOrNull(i)?.takeIf { it.code == code } ?: ProbeEntry(code) }
        _running.value = true
        job = scope.launch {
            try {
                run()
            } finally {
                restoreLibraryCallback()
                _running.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        waiting = false
        _running.value = false
    }

    private fun isReady(): Boolean {
        val state = sessionManager.sessionState.value
        return runCatching { api.isConnected() }.getOrDefault(false) &&
            state.phase is SessionPhase.Ready && state.isSetupComplete
    }

    private suspend fun run() {
        var wasReady = false
        var index = 0
        while (index < _entries.value.size) {
            val entry = _entries.value[index]
            if (entry.done) {
                index++
                continue
            }
            if (!isReady()) {
                wasReady = false
                _status.value = "Waiting for the watch to reconnect (${_entries.value.count { it.done }}/${_entries.value.size} done)"
                delay(1000)
                continue
            }
            if (!wasReady) {
                wasReady = true
                _status.value = "Connected, letting the watch settle"
                delay(3000)
                if (!isReady()) continue
            }
            installCollector()
            _status.value = "Request ${ProbePlan.hex(entry.code)} (${index + 1}/${_entries.value.size})"
            val answers = requestOnce(entry.code)
            if (!isReady()) {
                // The watch dropped during the request: the answer (or its absence) means nothing. Retry.
                continue
            }
            _entries.value = _entries.value.toMutableList().also { it[index] = entry.copy(answers = answers) }
            index++
        }
        _status.value = "Done: ${_entries.value.count { it.answers.orEmpty().isNotEmpty() }} of ${_entries.value.size} codes answered"
    }

    private fun installCollector() {
        try {
            Connection.setDataCallback(collector)
        } catch (t: Throwable) {
            WebhookDiagnostics.logError("Probe: cannot hook notifications: ${WebhookDiagnostics.oneLine(t)}")
        }
    }

    private fun restoreLibraryCallback() {
        try {
            Connection.setDataCallback(IDataReceived { data -> data?.let { forwardToLibrary(it) } })
        } catch (ignored: Throwable) {
        }
    }

    private suspend fun requestOnce(code: Int): List<String> {
        synchronized(buffer) { buffer.clear() }
        lastRxMillis = 0L
        waiting = true
        try {
            IO.writeCmd(GetSetMode.GET, byteArrayOf(code.toByte()))
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 2000) {
                delay(100)
                val rx = lastRxMillis
                if (rx != 0L && System.currentTimeMillis() - rx > 400) break
            }
        } finally {
            waiting = false
        }
        return synchronized(buffer) { buffer.toList() }
    }
}
