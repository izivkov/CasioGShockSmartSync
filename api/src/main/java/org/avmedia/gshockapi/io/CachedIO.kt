import org.avmedia.gshockapi.utils.Utils
import java.util.Locale

object CachedIO {

    private var cacheOff = false
    private val cache = mutableMapOf<String, Any?>()

    // Clear the entire cache
    fun clearCache() {
        cache.clear()
    }

    // Remove a specific key
    fun remove(key: String) {
        cache.remove(key.uppercase())
    }

    // Fetch from cache or compute if not present
    suspend fun <T : Any> request(
        key: String,
        compute: suspend (String) -> T
    ): T {
        if (cacheOff) {
            return compute(key).also { cache[key.uppercase()] = it }
        }

        @Suppress("UNCHECKED_CAST")
        return (cache[key.uppercase()] as? T) ?: compute(key).also {
            cache[key.uppercase()] = it
        }
    }

    fun set(key: String, func: () -> Unit) {
        func() // Execute the lambda
        remove(key) // Remove the cached value for the given key
    }

    fun put(key: String, value: Any) {
        cache[key.uppercase()] = value
    }

    // Get a cached value without recomputing
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: String): T {
        if (!cache.containsKey(key.uppercase())) {
            throw IllegalStateException("Key $key not found in cache")
        }

        return cache[key.uppercase()] as T
    }

    // Generate a compact key
    fun createKey(data: String): String {
        val shortStr = Utils.toCompactString(data)
        val startOfData = shortStr.substring(0, 2).uppercase(Locale.getDefault())
        val keyLength = if (startOfData in arrayOf("1D", "1E", "1F", "30", "31")) 4 else 2
        return shortStr.substring(0, keyLength).uppercase(Locale.getDefault())
    }
}