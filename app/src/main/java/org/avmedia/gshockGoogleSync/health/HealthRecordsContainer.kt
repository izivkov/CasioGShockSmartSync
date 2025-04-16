package org.avmedia.gshockGoogleSync.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Record

class HealthRecordsContainer {
    private val sleepRecords = mutableListOf<SleepSessionRecord>()
    private val exerciseRecords = mutableListOf<ExerciseSessionRecord>()
    private val stepsRecords = mutableListOf<StepsRecord>()
    private val heartRateRecords = mutableListOf<HeartRateRecord>()

    private fun addSleepRecord(record: SleepSessionRecord) = sleepRecords.add(record)
    private fun addExerciseRecord(record: ExerciseSessionRecord) = exerciseRecords.add(record)
    private fun addStepsRecord(record: StepsRecord) = stepsRecords.add(record)
    private fun addHeartRateRecord(record: HeartRateRecord) = heartRateRecords.add(record)

    fun addRecord(record: Record) {
        when (record) {
            is SleepSessionRecord -> addSleepRecord(record)
            is ExerciseSessionRecord -> addExerciseRecord(record)
            is StepsRecord -> addStepsRecord(record)
            is HeartRateRecord -> addHeartRateRecord(record)
        }
    }

    fun getAllRecords(): List<Record> = sleepRecords + exerciseRecords + stepsRecords + heartRateRecords

    suspend fun insertToHealthConnect(healthConnectClient: HealthConnectClient) {
        val records = getAllRecords()
        if (records.isNotEmpty()) {
            healthConnectClient.insertRecords(records)
        }
    }

    fun clear() {
        sleepRecords.clear()
        exerciseRecords.clear()
        stepsRecords.clear()
        heartRateRecords.clear()
    }

    fun size() = getAllRecords().size
}

