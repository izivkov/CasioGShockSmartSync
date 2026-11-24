package org.avmedia.gshockGoogleSync.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** RED CHECK stub: "Keep connected in background"; the real one in the next commit. */
object KeepConnected {
    const val KEY = "KeepConnectedAddress"
    private val _holder = MutableStateFlow<String?>(null)
    val holder: StateFlow<String?> = _holder

    fun holds(address: String?): Boolean = false

    fun load(context: Context) {}

    fun migrationChoice(lastAddress: String, known: List<String>, nameOf: (String) -> String): String? = null

    suspend fun set(context: Context, address: String, on: Boolean): String? = null

    suspend fun forget(context: Context, address: String) {}

    fun admits(name: String, address: String, background: Boolean, waitingFor: String?): Boolean = true

    suspend fun afterBackgroundConnect(address: String, read: suspend () -> Unit, disconnect: () -> Unit) {}

    internal fun resetForTest(holder: String? = null) { _holder.value = holder }
}
