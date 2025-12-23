package org.avmedia.gshockGoogleSync.scratchpad

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Manages storage of boolean action settings using a typesafe enum.
 * It is a client of the ScratchpadManager and operates on a buffer slice provided to it.
 */
@Singleton
class ActionsStorage @Inject constructor(
    private val manager: ScratchpadManager
) : ScratchpadClient {

    /**
     * Defines the typesafe names for all actions. The `ordinal` property
     * of each enum constant will be used as its index for storage.
     */
    enum class Action {
        SET_TIME,
        REMINDERS,
        PHONE_FINDER,
        TAKE_PHOTO,
        FLASHLIGHT,
        VOICE_ASSIST,
        SKIP_TO_NEXT_TRACK,
        PRAYER_ALARMS,
        PHONE_CALL,
        // Add more actions here. As long as it's less than 17, the storage size will be 2 bytes.
        // For example:
        // ANOTHER_ACTION,
        // YET_ANOTHER_ACTION
    }

    // This is the client's local copy of its data slice.
    private var scratchpadBuffer: ByteArray = ByteArray(getStorageSize())

    companion object {
        // The number of actions is now derived directly from the enum.
        private val ACTION_COUNT = Action.entries.size
        private const val BITS_PER_ACTION = 1 // Each action is a simple boolean
    }

    init {
        // Register this instance with the manager upon creation
        manager.register(this)
    }

    // --- Implementation of ScratchpadClient Interface ---

    override fun getStorageOffset(): Int {
        // ActionsStorage starts at position 3 (after AlarmNameStorage's 3 bytes)
        return 3
    }

    override fun getStorageSize(): Int {
        // Calculate the storage size dynamically.
        val totalBits = ACTION_COUNT * BITS_PER_ACTION
        return ceil(totalBits.toDouble() / 8.0).toInt()
    }

    override fun setBuffer(buffer: ByteArray) {
        // The manager gives us our slice. We must ensure we don't take a buffer of the wrong size.
        if (buffer.size == getStorageSize()) {
            this.scratchpadBuffer = buffer
        }
    }

    override fun getBuffer(): ByteArray {
        return this.scratchpadBuffer
    }

    // --- Public API for ViewModel/Use-Cases ---

    /**
     * Updates an action's boolean value in the local in-memory buffer.
     * This is a fast, in-memory operation. The manager handles persisting it.
     *
     * @param action The typesafe action to update.
     * @param enabled The boolean value to set for the action.
     */
    fun setAction(action: Action, enabled: Boolean) {
        val index = action.ordinal // Use the enum's ordinal as the index
        if (index !in 0 until ACTION_COUNT) return

        val byteIndex = index / 8
        val bitPosition = index % 8

        if (byteIndex >= scratchpadBuffer.size) return // Safety check

        var currentByte = scratchpadBuffer[byteIndex].toInt()
        if (enabled) {
            currentByte = currentByte or (1 shl bitPosition) // Set bit to 1
        } else {
            currentByte = currentByte and (1 shl bitPosition).inv() // Set bit to 0
        }
        scratchpadBuffer[byteIndex] = currentByte.toByte()
    }

    /**
     * Retrieves an action's boolean value from the in-memory buffer.
     *
     * @param action The typesafe action to retrieve.
     * @return The boolean value of the action.
     */
    fun getAction(action: Action): Boolean {
        val index = action.ordinal // Use the enum's ordinal as the index
        if (index !in 0 until ACTION_COUNT) return false

        val byteIndex = index / 8
        val bitPosition = index % 8

        if (byteIndex >= scratchpadBuffer.size) return false // Safety check

        val currentByte = scratchpadBuffer[byteIndex].toInt()
        return (currentByte shr bitPosition) and 1 == 1
    }

    /**
     * Resets the client's internal buffer to a cleared state (all false).
     */
    fun clear() {
        scratchpadBuffer = ByteArray(getStorageSize())
    }

    /**
     * Updates an action's boolean value in the local in-memory buffer.
     * Call save() to persist changes to the watch.
     *
     * @param action The typesafe action to update.
     * @param enabled The boolean value to set for the action.
     */
    suspend fun update(action: Action, enabled: Boolean) {
        setAction(action, enabled)
    }

    suspend fun save() {
        manager.save()
    }

    /**
     * Loads the data from the watch into the local buffer.
     */
    suspend fun load() {
        manager.load()
    }

}
