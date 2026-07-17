package org.avmedia.gshockGoogleSync.scratchpad

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Manages storage of boolean action settings.
 * Packs 9 boolean actions into 9 bits.
 */
@Singleton
class ActionsStorage @Inject constructor(
    private val manager: ScratchpadManager
) : ScratchpadClient {

    enum class Action {
        SET_TIME,
        REMINDERS,
        PHONE_FINDER,
        TAKE_PHOTO,
        FLASHLIGHT,
        VOICE_ASSIST,
        SKIP_TO_NEXT_TRACK,
        PRAYER_ALARMS,
        PHONE_CALL
    }

    private val actionStates = BooleanArray(Action.entries.size).apply {
        // Sensible defaults
        this[Action.SET_TIME.ordinal] = true
        this[Action.REMINDERS.ordinal] = true
    }

    init {
        manager.register(this)
    }

    override fun getBitSize(): Int = Action.entries.size

    override fun decode(data: ByteArray) {
        Action.entries.forEachIndexed { index, _ ->
            val byteIndex = index / 8
            val bitPos = index % 8
            if (byteIndex < data.size) {
                actionStates[index] = (data[byteIndex].toInt() shr bitPos) and 1 == 1
            }
        }
    }

    override fun encode(): ByteArray {
        val resultSize = ceil(getBitSize().toDouble() / 8.0).toInt()
        val result = ByteArray(resultSize)
        
        Action.entries.forEachIndexed { index, _ ->
            val byteIndex = index / 8
            val bitPos = index % 8
            if (byteIndex < result.size) {
                var currentByte = result[byteIndex].toInt()
                if (actionStates[index]) {
                    currentByte = currentByte or (1 shl bitPos)
                } else {
                    currentByte = currentByte and (1 shl bitPos).inv()
                }
                result[byteIndex] = currentByte.toByte()
            }
        }
        return result
    }

    fun setAction(action: Action, enabled: Boolean) {
        actionStates[action.ordinal] = enabled
    }

    fun getAction(action: Action): Boolean = actionStates[action.ordinal]

    fun clear() {
        for (i in actionStates.indices) actionStates[i] = false
    }

    suspend fun update(action: Action, enabled: Boolean) {
        setAction(action, enabled)
    }

    suspend fun save() = manager.save()
    suspend fun load() = manager.load()
}
