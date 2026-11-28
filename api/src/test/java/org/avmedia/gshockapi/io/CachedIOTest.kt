package org.avmedia.gshockapi.io

import CachedIO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/** [CachedIO] holds what the library read from the watch for one connection, so it must not lose or mix up entries. */
class CachedIOTest {
    @Before fun empty() = CachedIO.clearCache()

    @After fun cleanUp() = CachedIO.clearCache()

    @Test fun thereIsNothingUntilSomethingIsPut() {
        assertNull(CachedIO.getOrNull<String>("A_KEY"))
    }

    @Test fun aValueIsFoundByItsKeyWhateverTheCase() {
        CachedIO.put("Get_Thing", "value")
        assertEquals("value", CachedIO.getOrNull<String>("GET_THING"))
        assertEquals("value", CachedIO.get<String>("get_thing"))
    }

    @Test fun removeForgetsOneKeyAndClearForgetsAll() {
        CachedIO.put("ONE", 1)
        CachedIO.put("TWO", 2)
        CachedIO.remove("one")
        assertNull(CachedIO.getOrNull<Int>("ONE"))
        assertEquals(2, CachedIO.getOrNull<Int>("TWO"))
        CachedIO.clearCache()
        assertNull(CachedIO.getOrNull<Int>("TWO"))
    }

    /** Settings reads, a LifeLog result and a clear on connect all write at once; no write may drop another. */
    @Test fun concurrentWritesLoseNothing() {
        val threads = 8
        val perThread = 500
        val go = CountDownLatch(1)
        val workers = (0 until threads).map { t ->
            thread { go.await(); repeat(perThread) { i -> CachedIO.put("k$t-$i", i) } }
        }
        go.countDown()
        workers.forEach { it.join() }
        val missing = (0 until threads).sumOf { t -> (0 until perThread).count { i -> CachedIO.getOrNull<Int>("k$t-$i") == null } }
        assertTrue("$missing of ${threads * perThread} entries were lost", missing == 0)
    }
}
