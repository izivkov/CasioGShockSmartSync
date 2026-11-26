package org.avmedia.gshockGoogleSync.plugins

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.avmedia.smartsync.plugin.api.AndroidPermission
import org.avmedia.smartsync.plugin.api.DataUse
import org.avmedia.smartsync.plugin.api.PluginAccess

/**
 * The Android permissions a plugin needs: its own ([PluginAccess.androidPermissions]) and the location permission when
 * it reads the phone's location through the host ([DataUse.PHONE_LOCATION]). Android-free.
 */
object PluginPermissions {
    const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"

    fun required(access: PluginAccess): List<AndroidPermission> =
        (access.androidPermissions + listOfNotNull(
            AndroidPermission(COARSE_LOCATION, "the phone's coarse location").takeIf { DataUse.PHONE_LOCATION in access.reads },
        )).distinctBy { it.name }

    /**
     * Those of [required] that are missing. [ownCheck] (the plugin's own answer) decides only for the plugin's own
     * permissions ([own] names); every other one (the host's location permission) is asked of [granted].
     */
    fun missing(
        required: List<AndroidPermission>, granted: (String) -> Boolean, ownCheck: Set<String>? = null, own: Set<String> = emptySet(),
    ): List<AndroidPermission> =
        required.filter { p -> if (ownCheck != null && p.name in own) p.name in ownCheck else !granted(p.name) }

    /** "Needs permission: the phone's coarse location"; null when nothing is missing. */
    fun line(missing: List<AndroidPermission>): String? =
        missing.takeIf { it.isNotEmpty() }?.let { m -> "Needs permission: " + m.map { it.reason }.distinct().joinToString() }
}

/**
 * What each plugin misses, as state: the screens and [org.avmedia.smartsync.plugin.api.PluginHost.missingPermissions]
 * read [missing] and never wait. [refresh] asks again off the caller's thread: the system for the host's part, the
 * plugin's own suspend check ([ownCheck], [timeoutMs] at most; over it the answer before stays). Android-free.
 */
class PluginPermissionCache(
    private val scope: CoroutineScope,
    private val access: (String) -> PluginAccess?,
    private val granted: (String) -> Boolean,
    private val ownCheck: suspend (String) -> Set<String>?,
    private val timeoutMs: Long = 5_000,
) {
    private val _missing = MutableStateFlow<Map<String, List<AndroidPermission>>>(emptyMap())
    val missing: StateFlow<Map<String, List<AndroidPermission>>> = _missing

    /** The last answer for [id]; empty before the first check. */
    fun of(id: String): List<AndroidPermission> = _missing.value[id].orEmpty()

    fun refresh(id: String): Job = scope.launch { check(id) }

    /** Checks [id] now and keeps the answer. */
    suspend fun check(id: String): List<AndroidPermission> {
        val a = access(id) ?: return emptyList()
        val required = PluginPermissions.required(a)
        if (required.isEmpty()) return emptyList<AndroidPermission>().also { keep(id, it) }
        val own = a.androidPermissions.map { it.name }.toSet()
        val answer: Result<Set<String>?>? = if (own.isEmpty()) Result.success(null)
        else withTimeoutOrNull(timeoutMs) {
            try { Result.success(ownCheck(id)) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { Result.failure(e) }
        }
        if (answer == null) return of(id) // over the limit: the answer before stays
        val result = PluginPermissions.missing(required, granted, answer.getOrNull(), own)
        keep(id, result)
        return result
    }

    private fun keep(id: String, value: List<AndroidPermission>) = _missing.update { it + (id to value) }
}

/**
 * Asks for what a plugin misses, at once (its switch turned on, or a tap on its "Needs permission" line): [check] gives
 * the missing ones, [request] builds the request (the plugin's own screen or the runtime permissions), [emit] hands it
 * to the screen that asks. Nothing missing: no request. Android-free.
 */
class PermissionAsker<R>(
    private val scope: CoroutineScope,
    private val check: suspend (String) -> List<AndroidPermission>,
    private val request: (String, List<AndroidPermission>) -> R,
    private val emit: (R) -> Unit,
) {
    fun ask(id: String): Job = scope.launch {
        val missing = check(id)
        if (missing.isNotEmpty()) emit(request(id, missing))
    }
}

/**
 * The permission requests of the plugins, one at a time: the answer of the open one goes to its own plugin, the next
 * is asked after it. Android-free.
 */
class PermissionQueue<T> {
    private val waiting = ArrayDeque<T>()

    /** Queues [r]; returns it when nothing is open (ask it now), else null. */
    @Synchronized fun offer(r: T): T? {
        waiting.addLast(r)
        return r.takeIf { waiting.size == 1 }
    }

    /** The open request was answered: it, and the next one to ask now (null for none). */
    @Synchronized fun answered(): Pair<T?, T?> {
        val done = waiting.removeFirstOrNull()
        return done to waiting.firstOrNull()
    }
}
