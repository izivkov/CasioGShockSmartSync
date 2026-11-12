package org.avmedia.gshockGoogleSync.session

import android.bluetooth.BluetoothDevice
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.avmedia.gshockapi.EventAction
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Which button the fake watch reports as having started the connection. */
enum class FakeButton { NONE, ACTION, AUTO_TIME, FIND_PHONE, ALWAYS_CONNECTED }

class FakeConnectionGateway : ConnectionGateway {
    var pressed: FakeButton = FakeButton.NONE
    val tornDown = mutableListOf<BluetoothDevice>()
    var disconnectCount = 0
        private set

    override fun isActionButtonPressed() = pressed == FakeButton.ACTION
    override fun isAutoTimeStarted() = pressed == FakeButton.AUTO_TIME
    override fun isFindPhoneButtonPressed() = pressed == FakeButton.FIND_PHONE
    override fun isAlwaysConnectedConnectionPressed() = pressed == FakeButton.ALWAYS_CONNECTED

    override fun teardownConnection(device: BluetoothDevice) {
        tornDown += device
    }

    override fun disconnect() {
        disconnectCount++
    }
}

/** An [EventSource] the test drives by hand: [emit] sets the payload and runs the matching action. */
class FakeEventSource : EventSource {
    private val payloads = mutableMapOf<String, Any?>()
    private var actions: Map<String, EventAction> = emptyMap()
    var subscribeCount = 0
        private set
    var isSubscribed = false
        private set

    override fun subscribe(actions: Array<EventAction>): AutoCloseable {
        subscribeCount++
        isSubscribed = true
        this.actions = actions.associateBy { it.label }
        return AutoCloseable { isSubscribed = false }
    }

    override fun payload(eventName: String): Any? = payloads[eventName]

    fun emit(eventName: String, payload: Any? = null) {
        payloads[eventName] = payload
        if (isSubscribed) actions[eventName]?.action?.invoke()
    }
}

/** A device mock with a fixed address; [BluetoothDevice] cannot be constructed in a JVM test. */
fun fakeDevice(address: String): BluetoothDevice =
    mockk<BluetoothDevice>().also { every { it.address } returns address }

/** Runs `Dispatchers.Main` (used by Session and SessionManager) on an unconfined test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
