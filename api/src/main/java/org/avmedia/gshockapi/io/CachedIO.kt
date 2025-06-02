import org.avmedia.gshockapi.utils.Utils
import java.util.Locale

object CachedIO {
    private data class State(
        val cache: Map<String, Any?> = emptyMap(),
        val cacheOff: Boolean = false
    )

    private var state = State()

    fun clearCache() {
        state = state.copy(cache = emptyMap())
    }

    fun remove(key: String) {
        state = state.copy(cache = state.cache - key.uppercase())
    }

    suspend fun <T : Any> request(
        key: String,
        compute: suspend (String) -> T
    ): T = when {
        state.cacheOff -> compute(key).also { value ->
            state = state.copy(cache = state.cache + (key.uppercase() to value))
        }

        else -> state.cache[key.uppercase()]?.let { it as T } ?: compute(key).also { value ->
            state = state.copy(cache = state.cache + (key.uppercase() to value))
        }
    }

    fun set(key: String, func: () -> Unit) {
        func()
        remove(key)
    }

    fun put(key: String, value: Any) {
        state = state.copy(cache = state.cache + (key.uppercase() to value))
    }

    fun <T : Any> get(key: String): T = state.cache[key.uppercase()]?.let { it as T }
        ?: throw IllegalStateException("Key $key not found in cache")

    fun createKey(data: String): String = Utils.toCompactString(data)
        .let { shortStr ->
            val startOfData = shortStr.substring(0, 2).uppercase(Locale.getDefault())
            val keyLength = if (startOfData in arrayOf("1D", "1E", "1F", "30", "31")) 4 else 2
            shortStr.substring(0, keyLength).uppercase(Locale.getDefault())
        }
}
