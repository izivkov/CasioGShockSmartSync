package org.avmedia.gshockGoogleSync.scratchpad

import org.avmedia.gshockapi.WatchInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * A utility class providing a static lookup set for standard alarm names.
 */
class AlarmsLookupSet {
    enum class AlarmCode(val value: Int) {
        Daily(0),
        Fajr(1),
        Dhuhr(2),
        Asr(3),
        Maghrib(4),
        Isha(5)
    }

    companion object {
        val alarmCodeSet = setOf(
            AlarmCode.Daily.value to "Daily",
            AlarmCode.Fajr.value to "Fajr",
            AlarmCode.Dhuhr.value to "Dhuhr",
            AlarmCode.Asr.value to "Asr",
            AlarmCode.Maghrib.value to "Maghrib",
            AlarmCode.Isha.value to "Isha"
        )
    }
}

/**
 * Manages the persistent storage of custom user-defined names for alarms.
 * Packs 6 alarm names (3 bits each) into 18 bits.
 */
@Singleton
class AlarmNameStorage @Inject constructor(
    private val manager: ScratchpadManager
) : ScratchpadClient {
    private var namesMap: Map<Int, String> = emptyMap()
    private var codesMap: Map<String, Int> = emptyMap()

    private val alarmCodes = IntArray(ALARM_COUNT) { NO_NAME_INDEX }

    companion object {
        private const val ALARM_COUNT = 6
        private const val BITS_PER_ALARM = 3
        private const val NO_NAME_INDEX = 0x7
    }

    init {
        manager.register(this)
        setNames(AlarmsLookupSet.alarmCodeSet)
    }

    override fun getBitSize(): Int = ALARM_COUNT * BITS_PER_ALARM

    override fun decode(data: ByteArray) {
        for (i in 0 until ALARM_COUNT) {
            val startBit = i * BITS_PER_ALARM
            var code = 0
            for (bit in 0 until BITS_PER_ALARM) {
                val currentBit = startBit + bit
                val byteIndex = currentBit / 8
                val bitPos = currentBit % 8
                if (byteIndex < data.size) {
                    if ((data[byteIndex].toInt() shr bitPos) and 1 == 1) {
                        code = code or (1 shl bit)
                    }
                }
            }
            // Validation: reset to NO_NAME if invalid
            if (code !in 0..5 && code != NO_NAME_INDEX) {
                code = NO_NAME_INDEX
            }
                alarmCodes[i] = code
            }
        }

    override fun encode(): ByteArray {
        val resultSize = ceil(getBitSize().toDouble() / 8.0).toInt()
        val result = ByteArray(resultSize)
        
        for (i in 0 until ALARM_COUNT) {
            val startBit = i * BITS_PER_ALARM
            val code = alarmCodes[i]
            for (bit in 0 until BITS_PER_ALARM) {
                val currentBit = startBit + bit
                val byteIndex = currentBit / 8
                val bitPos = currentBit % 8
                if (byteIndex < result.size) {
                    var currentByte = result[byteIndex].toInt()
                    if ((code shr bit) and 1 == 1) {
                        currentByte = currentByte or (1 shl bitPos)
                    } else {
                        currentByte = currentByte and (1 shl bitPos).inv()
                    }
                    result[byteIndex] = currentByte.toByte()
                }
            }
        }
        return result
    }

    fun setNames(names: Set<Pair<Int, String>>) {
        namesMap = names.associate { it.first to it.second }
        codesMap = names.associate { it.second to it.first }
    }

    fun put(name: String, index: Int) {
        if (index !in 0 until ALARM_COUNT) return
            alarmCodes[index] = codesMap[name] ?: NO_NAME_INDEX
        }

    fun get(index: Int): String {
        if (index !in 0 until ALARM_COUNT) return ""
        val code = alarmCodes[index]
        return if (code == NO_NAME_INDEX) "" else namesMap[code] ?: ""
    }

    fun clear() {
        for (i in 0 until ALARM_COUNT) alarmCodes[i] = NO_NAME_INDEX
    }

    suspend fun save() = manager.save()
    suspend fun load() = manager.load()
}
