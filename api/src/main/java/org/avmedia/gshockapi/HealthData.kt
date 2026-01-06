package org.avmedia.gshockapi

data class HealthData(
        val steps: Int,
        val calories: Int,
        val heartRate: Int,
        val distance: Int,
        val timestamp: String
) {
    fun toJson(): String {
        return "{\"steps\": $steps, \"calories\": $calories, \"heartRate\": $heartRate, \"distance\": $distance, \"timestamp\": \"$timestamp\"}"
    }
}
