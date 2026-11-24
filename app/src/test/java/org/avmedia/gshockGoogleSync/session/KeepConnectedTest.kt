package org.avmedia.gshockGoogleSync.session

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.services.InactivityTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** "Keep connected in background": one watch at most; on keeps the link, off is upstream's connect on request. */
@RunWith(RobolectricTestRunner::class)
class KeepConnectedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val a = "AA:BB:CC:DD:EE:01"
    private val b = "AA:BB:CC:DD:EE:02"
    private val h5600 = "CASIO GBX-H5600"

    @After fun reset() = runBlocking {
        KeepConnected.set(context, a, false)
        KeepConnected.set(context, b, false)
        KeepConnected.resetForTest()
    }

    @Test
    fun turningItOnForBTurnsItOffForA() = runBlocking {
        assertNull(KeepConnected.set(context, a, true))
        assertTrue(KeepConnected.holds(a))
        assertEquals(a, KeepConnected.set(context, b, true))
        assertFalse(KeepConnected.holds(a))
        assertTrue(KeepConnected.holds(b.lowercase()))
    }

    @Test
    fun afterBTookItTheReconnectOfAIsIgnoredWithoutATap() = runBlocking {
        KeepConnected.set(context, a, true)
        KeepConnected.set(context, b, true)
        assertFalse(KeepConnected.admits(h5600, a, background = false, waitingFor = null))
        assertTrue(KeepConnected.admits(h5600, a, background = false, waitingFor = a))
        assertTrue(KeepConnected.admits(h5600, a, background = true, waitingFor = null))
        assertTrue(KeepConnected.admits(h5600, b, background = false, waitingFor = null))
        // A watch that connects on its button press is admitted as upstream does.
        assertTrue(KeepConnected.admits("CASIO GW-B5600", a, background = false, waitingFor = null))
    }

    @Test
    fun onIsStillOnAfterAnAppRestartAndItsReconnectIsAdmitted() = runBlocking {
        KeepConnected.set(context, a, true)
        KeepConnected.resetForTest() // the app process ends
        KeepConnected.load(context)
        assertTrue(KeepConnected.holds(a))
        assertTrue(KeepConnected.admits(h5600, a, background = false, waitingFor = null))
    }

    @Test
    fun migrationGivesItToTheMostRecentlyConnectedAlwaysConnectedWatch() {
        val names = mapOf(a to "CASIO GBX-H5600", b to "CASIO DW-H5600", "CC" to "CASIO GW-B5600")
        val known = listOf(a, b, "CC")
        assertEquals(b, KeepConnected.migrationChoice(b, known) { names[it].orEmpty() })
        assertEquals(a, KeepConnected.migrationChoice("CC", known) { names[it].orEmpty() })
        assertNull(KeepConnected.migrationChoice("CC", listOf("CC")) { names[it].orEmpty() })
    }

    @Test
    fun onMeansNoInactivityDisconnectOffMeansDisconnect() = runBlocking {
        val on = SessionState(watch = WatchSnapshot(name = h5600, address = a), button = ConnectionButton.ALWAYS_CONNECTED)
        assertFalse("off: the H5600 is let go as upstream does", on.keepsLink)
        assertTrue("disconnect" in inactivity(on.keepsLink))
        KeepConnected.set(context, a, true)
        assertTrue(on.keepsLink)
        assertFalse("disconnect" in inactivity(on.keepsLink))
    }

    @Test
    fun offTheBackgroundReadIsFollowedByARelease() = runBlocking {
        val calls = mutableListOf<String>()
        KeepConnected.afterBackgroundConnect(a, read = { calls += "read" }, disconnect = { calls += "disconnect" })
        assertEquals(listOf("read", "disconnect"), calls)
        calls.clear()
        KeepConnected.set(context, a, true)
        KeepConnected.afterBackgroundConnect(a, read = { calls += "read" }, disconnect = { calls += "disconnect" })
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun offALinkDropEndsTheVisitOnTheTheLinkWaits() = runBlocking {
        val device = mockk<BluetoothDevice> { every { address } returns a }
        fun dropped(): Boolean {
            val m = SessionManager(mockk<GShockRepository>(relaxed = true))
            val s = m.onConnecting(device)
            m.onDeviceName(h5600)
            s.setReady(button = ConnectionButton.ALWAYS_CONNECTED, cause = ConnectionCause.USER)
            m.onDisconnected(device, "Watch Disconnected")
            return s.isActive
        }
        assertFalse(dropped())
        KeepConnected.set(context, a, true)
        assertTrue(dropped())
    }

    @Test
    fun forgetTurnsItOff() = runBlocking {
        KeepConnected.set(context, a, true)
        KeepConnected.forget(context, a)
        assertFalse(KeepConnected.holds(a))
    }

    private fun inactivity(keepsLink: Boolean): List<String> {
        val calls = mutableListOf<String>()
        InactivityTimeout.run(keepsLink, goHome = { calls += "home" }, disconnect = { calls += "disconnect" }, tellDisconnected = { calls += "tell" })
        return calls
    }
}
