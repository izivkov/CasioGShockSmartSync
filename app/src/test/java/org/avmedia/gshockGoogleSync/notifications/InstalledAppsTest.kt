package org.avmedia.gshockGoogleSync.notifications

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class InstalledAppsTest {
    private val apps = listOf(
        AppEntry("com.whatsapp", "WhatsApp"),
        AppEntry("com.google.android.gm", "Gmail"),
        AppEntry("com.spotify.music", "Spotify"),
    )

    @Test
    fun blankQueryKeepsAll() {
        assertEquals(apps, InstalledApps.filter(apps, "  "))
    }

    @Test
    fun searchMatchesNameOrPackageIgnoringCase() {
        assertEquals(listOf("Gmail"), InstalledApps.filter(apps, "GMA").map { it.label })
        assertEquals(listOf("Spotify"), InstalledApps.filter(apps, "spotify.music").map { it.label })
        assertEquals(emptyList<AppEntry>(), InstalledApps.filter(apps, "zzz"))
    }

    /** Reading every app's label froze the screen when it ran on the caller's (main) thread. */
    @Test
    fun theListLoadsOnTheIoDispatcherOnce() {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "io-test") }
        val loadThreads = mutableListOf<Thread>()
        val cache = SessionListCache(executor.asCoroutineDispatcher()) {
            loadThreads += Thread.currentThread()
            apps
        }
        val caller = Thread.currentThread()
        try {
            runBlocking {
                assertEquals(apps, cache.get())
                assertEquals(apps, cache.get())
            }
        } finally {
            executor.shutdown()
        }
        // Coroutine debug mode adds " @coroutine#n" to the thread name while a coroutine runs.
        assertEquals(1, loadThreads.size)
        assertNotEquals(caller, loadThreads.single())
        assertTrue(loadThreads.single().name.startsWith("io-test"))
    }
}
