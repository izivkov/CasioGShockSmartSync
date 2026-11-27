package org.avmedia.gshockapi.io

import CachedIO
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.utils.Utils
import timber.log.Timber

// ============================================================================
// Pure Functional Core: Button Press Detection
// ============================================================================

/**
 * Pure functional core for button press detection.
 *
 * All methods are pure: no mutable state, no side effects.
 * Handles button code parsing and identification.
 */
@RequiresApi(Build.VERSION_CODES.O)
object ButtonPressedIOFunctional {
    /**
     * Pure parser: Identifies which button was pressed.
     *
     * Protocol format:
     * [8] - Button code:
     *     0-1 = LOWER_LEFT
     *     2   = FIND_PHONE
     *     3   = NO_BUTTON (auto-time set, no physical button)
     *     4   = LOWER_RIGHT
     *     0xA, 0xB, 0xD, 0xE = ALWAYS_CONNECTED_CONNECTION
     *
     * Example data:
     *   RIGHT BUTTON: 0x10 17 62 07 38 85 CD 7F ->04<- 03 0F FF FF FF FF 24 00 00 00
     *   LEFT BUTTON:  0x10 17 62 07 38 85 CD 7F ->01<- 03 0F FF FF FF FF 24 00 00 00
     *   RESET:        0x10 17 62 16 05 85 dd 7f ->00<- 03 0f ff ff ff ff 24 00 00 00
     *   AUTO-TIME:    0x10 17 62 16 05 85 dd 7f ->03<- 03 0f ff ff ff ff 24 00 00 00
     *   FIND PHONE:   0x10 07 7A 29 33 A1 C6 7F ->02<- 03 0F FF FF FF FF 24 00 00 00
     */
    fun parseButtonPress(data: String): Result<IO.WatchButton> =
        runCatching {
            if (data.isEmpty()) {
                throw IllegalArgumentException("Empty button data")
            }

            val bleIntArr = Utils.toIntArray(data)
            if (bleIntArr.size < 19) {
                throw IllegalArgumentException("Button data too short: ${bleIntArr.size} bytes (need 19)")
            }

            val pressedButton = bleIntArr[8]
            when (pressedButton) {
                in 0..1 -> IO.WatchButton.LOWER_LEFT
                2 -> IO.WatchButton.FIND_PHONE
                3 -> IO.WatchButton.NO_BUTTON // auto time set, no physical button pressed
                4 -> IO.WatchButton.LOWER_RIGHT
                else -> {
                    // For always-connected watches, check if 0b1000 bit is set
                    // Possible values: 0xA, 0xB, 0xD, 0xE all have bit 3 set
                    val alwaysRunningMask = 0b1000
                    if (pressedButton and alwaysRunningMask != 0) {
                        IO.WatchButton.ALLAYS_CONNECTED_CONNECTION
                    } else {
                        IO.WatchButton.LOWER_LEFT
                    }
                }
            }
        }

    /**
     * Pure parser: the raw "kind of connection" byte, [8], or null if the data is too short.
     */
    fun parseConnectionKind(data: String): Int? =
        runCatching { Utils.toIntArray(data) }
            .getOrNull()
            ?.takeIf { it.size >= 19 }
            ?.get(8)
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Button Press IO handler with state management.
 *
 * Detects which watch button was pressed and provides access to the last button state.
 * Uses pure functional core for all button code parsing.
 */
@RequiresApi(Build.VERSION_CODES.O)
object ButtonPressedIO {
    private data class State(
        val lastKnownButton: IO.WatchButton = IO.WatchButton.INVALID,
        val connectionKind: Int? = null,
        val deferredResult: CompletableDeferred<IO.WatchButton>? = null,
    )

    private var state = State()

    /** The last valid 0x10 answer and when it came. The connection setup and init read the same answer: one read, both use it. */
    private data class Answer(val button: IO.WatchButton, val at: Long)

    @Volatile private var answer: Answer? = null

    @Volatile var clock: () -> Long = System::currentTimeMillis

    /** An answer this old is not shared any more: init asks again. */
    const val SHARE_WINDOW_MS = 30_000L

    fun forgetAnswer() {
        answer = null
    }

    private fun freshAnswer(): IO.WatchButton? = answer?.takeIf { clock() - it.at in 0..SHARE_WINDOW_MS }?.button

    suspend fun request(): IO.WatchButton {
        Timber.i("Button info request started (0x10)")
        // Always ask the watch: a value cached for an earlier watch must not answer for this one.
        CachedIO.remove("10")
        return CachedIO.request("10") { key ->
            val shared = freshAnswer()
            if (shared != null) {
                // The kind of connection came with that same answer and stays as it was read.
                Timber.i("Button info already received on this link: $shared")
                shared
            } else {
                resetConnectionKind()
                val deferred = CompletableDeferred<IO.WatchButton>()
                synchronized(this) {
                    if (state.deferredResult != null) {
                        Timber.w("BTN_TEST replacing a pending button request; the earlier one can no longer be answered")
                    }
                    state = state.copy(deferredResult = deferred)
                    Timber.i("BTN_TEST request pending=true")
                }
                try {
                    Timber.i("Sending button info request (0x$key)")
                    IO.request(key)
                    deferred.await()
                } finally {
                    synchronized(this) {
                        if (state.deferredResult === deferred) {
                            state = state.copy(deferredResult = null)
                        }
                    }
                }
            }
        }.also { button ->
            state = state.copy(lastKnownButton = button)
            Timber.i("Button info request completed: $button")
        }
    }

    fun get(): IO.WatchButton = state.lastKnownButton

    /**
     * The raw kind of connection reported by the watch for the current connection,
     * or null if it has not been read yet.
     */
    fun getConnectionKind(): Int? = state.connectionKind

    fun resetConnectionKind() {
        synchronized(this) {
            state = state.copy(connectionKind = null)
        }
    }

    fun put(value: Any) {
        CachedIO.put("10", value)
    }

    fun onReceived(data: String) {
        Timber.i("Button info response received: $data")
        synchronized(this) {
            state = state.copy(connectionKind = ButtonPressedIOFunctional.parseConnectionKind(data))
        }
        // Use pure function to parse button press
        ButtonPressedIOFunctional.parseButtonPress(data)
            .fold(
                onSuccess = { button ->
                    answer = Answer(button, clock())
                    synchronized(this) {
                        if (state.deferredResult == null) {
                            Timber.w("BTN_TEST reply dropped: no request was pending")
                        }
                        state.deferredResult?.complete(button)
                        state = state.copy(deferredResult = null)
                    }
                },
                onFailure = { error ->
                    Timber.e("Failed to parse button press: ${error.message}")
                    synchronized(this) {
                        state.deferredResult?.complete(IO.WatchButton.INVALID)
                        state = state.copy(deferredResult = null)
                    }
                },
            )
    }
}
