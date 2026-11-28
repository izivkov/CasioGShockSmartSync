import org.avmedia.gshockapi.utils.Utils
import java.util.Locale

// ============================================================================
// Pure Functional Core: Cache Key Generation
// ============================================================================

/**
 * Pure functional core for cache operations.
 *
 * All methods are pure: no mutable state, no side effects.
 * Handles key generation and normalization for cache entries.
 */
object CachedIOFunctional {
    /**
     * Pure generator: Creates normalized cache key from command data.
     *
     * Extracts relevant prefix from compact string command:
     * - For 0x1D, 0x1E, 0x1F, 0x30, 0x31: Uses first 4 characters
     * - For others: Uses first 2 characters
     * All keys are uppercase.
     *
     * No side effects - pure string transformation.
     */
    fun createKey(data: String): String =
        Utils.toCompactString(data)
            .let { shortStr ->
                val startOfData = shortStr.substring(0, 2).uppercase(Locale.getDefault())
                val keyLength = if (startOfData in arrayOf("1D", "1E", "1F", "30", "31")) 4 else 2
                shortStr.substring(0, keyLength).uppercase(Locale.getDefault())
            }
}

object CachedIO {
    private data class State(
        val cache: Map<String, Any?> = emptyMap(),
        val cacheOff: Boolean = false,
    )

    @Volatile
    private var state = State()

    /**
     * Every change of the cache goes through here: it is written from several coroutines at once (settings reads, a
     * LifeLog result, a connection that clears it), and a plain read-copy-write of [state] loses entries.
     */
    private fun update(transform: (State) -> State) {
        synchronized(this) { state = transform(state) }
    }

    fun clearCache() {
        update { it.copy(cache = emptyMap()) }
    }

    fun remove(key: String) {
        update { it.copy(cache = it.cache - key.uppercase()) }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> request(
        key: String,
        compute: suspend (String) -> T,
    ): T =
        when {
            state.cacheOff ->
                gatedCompute(key, compute).also { value -> put(key, value) }

            else ->
                state.cache[key.uppercase()]?.let { it as T } ?: gatedCompute(key, compute).also { value -> put(key, value) }
        }

    /** A read from the watch never goes out while a transfer runs (else `0x11` goes out every 4 to 5 s during a workout read). */
    private suspend fun <T : Any> gatedCompute(
        key: String,
        compute: suspend (String) -> T,
    ): T {
        org.avmedia.gshockapi.io.WatchExchange.awaitNoTransfer()
        return org.avmedia.gshockapi.io.WatchExchange.settingsExchange { compute(key) }
    }

    fun set(
        key: String,
        func: () -> Unit,
    ) {
        func()
        remove(key)
    }

    fun put(
        key: String,
        value: Any,
    ) {
        update { it.copy(cache = it.cache + (key.uppercase() to value)) }
    }

    /** The cached value, or null when there is none (a new connection clears the cache). */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(key: String): T? = state.cache[key.uppercase()] as T?

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: String): T =
        state.cache[key.uppercase()]?.let { it as T }
            ?: throw IllegalStateException("Key $key not found in cache")

    fun createKey(data: String): String = CachedIOFunctional.createKey(data)
}
