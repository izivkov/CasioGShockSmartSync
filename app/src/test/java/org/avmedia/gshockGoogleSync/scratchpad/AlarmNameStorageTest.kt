package org.avmedia.gshockGoogleSync.scratchpad

import org.avmedia.gshockapi.IGShockAPI
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class AlarmNameStorageTest {

    private lateinit var storage: AlarmNameStorage
    private lateinit var manager: ScratchpadManager
    private lateinit var api: IGShockAPI

    @Before
    fun setUp() {
        // Create a dynamic proxy for IGShockAPI
        api = Proxy.newProxyInstance(
            IGShockAPI::class.java.classLoader,
            arrayOf(IGShockAPI::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getScratchpadData" -> ByteArray(args[1] as Int) // Return empty buffer of requested size
                "setScratchpadData" -> null // Do nothing
                "toString" -> "FakeIGShockAPI"
                "hashCode" -> 0
                "equals" -> false
                else -> {
                    // Return appropriate defaults for primitives if needed, otherwise null
                    if (method.returnType == Boolean::class.java) false
                    else if (method.returnType == Int::class.java) 0
                    else null
                }
            }
        } as IGShockAPI

        manager = ScratchpadManager(api)
        storage = AlarmNameStorage(manager)
    }

    @Test
    fun testPutAndGet() {
        // Initial state: Buffer is all 0s.
        // Code 0 maps to "Daily".
        for (i in 0 until 6) {
            assertEquals("Daily", storage.get(i))
        }

        // Set names
        storage.put("Fajr", 0) // Code 1
        assertEquals("Fajr", storage.get(0))

        storage.put("Dhuhr", 1) // Code 2
        assertEquals("Dhuhr", storage.get(1))

        // Verify interference (Index 0 and 1 share a byte)
        assertEquals("Fajr", storage.get(0))
        assertEquals("Dhuhr", storage.get(1))

        // Set remaining
        storage.put("Asr", 2)
        storage.put("Maghrib", 3)
        storage.put("Isha", 4)
        storage.put("Daily", 5)

        assertEquals("Asr", storage.get(2))
        assertEquals("Maghrib", storage.get(3))
        assertEquals("Isha", storage.get(4))
        assertEquals("Daily", storage.get(5))

        // Test clear()
        storage.clear()
        for (i in 0 until 6) {
            assertEquals("", storage.get(i))
        }
        
        // Test putting after clear
        storage.put("Fajr", 0)
        assertEquals("Fajr", storage.get(0))
        assertEquals("", storage.get(1)) // Should still be empty
    }
}
