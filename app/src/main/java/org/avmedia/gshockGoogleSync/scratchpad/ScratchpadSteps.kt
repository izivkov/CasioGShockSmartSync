package org.avmedia.gshockGoogleSync.scratchpad

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages storage of step-related goals and user body metrics.
 * Packs Step Goal (16 bits) and Weight (16 bits) into 32 bits total.
 * Weight is stored in 100-gram units (e.g., 70.0kg = 700 units).
 */
@Singleton
class ScratchpadSteps @Inject constructor(
    private val manager: ScratchpadManager
) : ScratchpadClient {

    private var stepGoal: Int = 10000
    private var weight: Int = 70

    init {
        manager.register(this)
    }

    override fun getBitSize(): Int = 32

    override fun decode(data: ByteArray) {
        if (data.size >= 4) {
            // Decode Step Goal (16 bits, LE)
            stepGoal = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
            
            // Decode Weight (16 bits, LE, stored as 100g units)
            weight = (data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)
        }
        
        // Basic validation for defaults
        if (stepGoal <= 0) stepGoal = 10000
        if (weight <= 0) weight = 700 // 70.0kg
    }

    override fun encode(): ByteArray {
        val result = ByteArray(4)
        
        // Encode Step Goal
        result[0] = (stepGoal and 0xFF).toByte()
        result[1] = ((stepGoal shr 8) and 0xFF).toByte()
        
        // Encode Weight
        result[2] = (weight and 0xFF).toByte()
        result[3] = ((weight shr 8) and 0xFF).toByte()
        
        return result
    }

    fun getStepGoal(): Int = stepGoal
    fun setStepGoal(value: Int) {
        stepGoal = value
    }

    fun getWeight(): Int = weight
    fun setWeight(value: Int) {
        weight = value
    }

    suspend fun save() = manager.save()
    suspend fun load() = manager.load()
}
