package org.avmedia.gshockGoogleSync.homeassistant

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LocalDataStorage.put/delete launch the write and return at once, so two quick writes can land in
 * either order and a read right after a write sees the old value (before: Connect saved the HA
 * link twice, the webhook id was lost, the screen said "Not connected" and no sensor was sent).
 * The app store must wait for each write.
 */
class StorageOrderTest {
    @Test
    fun appStoreWaitsForEachWrite() {
        val src = File("src/main/java/org/avmedia/gshockGoogleSync/homeassistant/AppStores.kt").readText()
        val store = src.substringAfter("class LocalKeyValueStore").substringBefore("\n    }\n")
        assertTrue("LocalKeyValueStore must not use the fire-and-forget put/delete:\n$store",
            "LocalDataStorage.put(" !in store && "LocalDataStorage.delete(" !in store)
    }
}
