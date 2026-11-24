package org.avmedia.gshockGoogleSync.watchvalues

import java.time.OffsetDateTime
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What a provider can see when the values are read. [cached] lets several variables share one
 * (slow) read from the watch, for example all `watch.settings.*` values.
 */
class VariableContext(
    val event: String,
    val now: OffsetDateTime,
    val watchConnected: Boolean,
) {
    private val cache = HashMap<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> cached(key: String, load: suspend () -> T): T {
        if (cache.containsKey(key)) return cache[key] as T
        val value = load()
        cache[key] = value
        return value
    }
}

/**
 * One variable: a dotted name, a description for the screen, and the code that reads it when
 * the values are read. Return null (or throw) when the value is not available.
 */
class Variable(
    val name: String,
    val description: String,
    /** True when the value comes from the watch: skipped (null) while the watch is not connected. */
    val needsWatch: Boolean = false,
    val provider: suspend (VariableContext) -> Any?,
)

/** The single list of variables. Adding a variable is adding one [Variable] to it. */
class VariableRegistry(val variables: List<Variable>) {
    private val byName = variables.associateBy { it.name }

    init {
        require(byName.size == variables.size) { "Duplicate variable name" }
    }

    val names: Set<String> get() = byName.keys

    /**
     * Reads the variables in [wanted] (all when null), one after the other, because they share one
     * Bluetooth link. A provider that throws or takes longer than [timeoutMs] gives null.
     */
    suspend fun resolve(
        context: VariableContext,
        wanted: Set<String>? = null,
        timeoutMs: Long = 5_000,
    ): Map<String, Any?> = resolveWithFailures(context, wanted, timeoutMs).values

    class Resolution(val values: Map<String, Any?>, val failures: Map<String, String>)

    /** Like [resolve], and also says which providers failed and why ("name" to "Class: message"). */
    suspend fun resolveWithFailures(
        context: VariableContext,
        wanted: Set<String>? = null,
        timeoutMs: Long = 5_000,
    ): Resolution {
        val result = LinkedHashMap<String, Any?>()
        val failures = LinkedHashMap<String, String>()
        for (variable in variables) {
            if (wanted != null && variable.name !in wanted) continue
            result[variable.name] =
                if (variable.needsWatch && !context.watchConnected) null
                else try {
                    withTimeoutOrNull(timeoutMs) { variable.provider(context) }.let { value ->
                        if (value is String && WatchDecoders.looksLikeRawHex(value)) {
                            failures[variable.name] = "raw hex dropped (needs a decoder)"
                            null
                        } else value
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Also Errors such as NoClassDefFoundError: one variable never breaks the send.
                    failures[variable.name] = Diagnostics.oneLine(e)
                    null
                }
        }
        return Resolution(result, failures)
    }
}
