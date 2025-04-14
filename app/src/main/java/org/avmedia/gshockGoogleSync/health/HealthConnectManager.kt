package org.avmedia.gshockGoogleSync.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata.Companion.autoRecordedWithId
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

import androidx.health.connect.client.records.ExerciseSessionRecord
import java.time.Instant
import java.time.ZoneOffset

class HealthConnectManager(private val context: Context) : IHealthConnectManager {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    private val _permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),

        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
    )

    val permissions: Set<String> get() = _permissions

    fun requestPermissionsActivityContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    suspend fun hasPermissions(): Boolean {
        return try {
            client.permissionController.getGrantedPermissions().containsAll(_permissions)
        } catch (e: Exception) {
            false
        }
    }

    // Read
    suspend fun readDailySteps(): Long? {
        val today = LocalDateTime.now()
        val startTime = today.withHour(0).withMinute(0)
        val endTime = today.withHour(23).withMinute(59)

        val request = AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )

        return try {
            val response = client.aggregate(request)
            response[StepsRecord.COUNT_TOTAL]
        } catch (e: Exception) {
            null
        }
    }

    suspend fun readHeartRateSamples(): List<Double> {
        val now = LocalDateTime.now()
        val startTime = now.minusHours(24)

        val request = ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, now)
        )

        return try {
            val response = client.readRecords(request)
            response.records.flatMap { record ->
                record.samples.map { it.beatsPerMinute.toDouble() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun readSleepSessions(): Long {
        val now = LocalDateTime.now()
        val startTime = now.minusDays(1)

        val request = ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, now)
        )

        return try {
            val response = client.readRecords(request)
            response.records.fold(0L) { acc, record ->
                acc + Duration.between(record.startTime, record.endTime).toMinutes()
            }
        } catch (e: Exception) {
            0L
        }
    }

    // Write
    private val device = Device(
        manufacturer = "Casio",
        model = "DW-H5600",
        type = Device.TYPE_WATCH,
    )
    private val metadata = autoRecordedWithId(
        id = "G-Shock Smart Sync",
        device
    )

    suspend fun writeStepsRecord(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        steps: Long
    ): Boolean {
        val record = StepsRecord(
            count = steps,
            startTime = startTime.atZone(ZoneId.systemDefault()).toInstant(),
            endTime = endTime.atZone(ZoneId.systemDefault()).toInstant(),
            startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
            endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime),
            metadata = metadata
        )

        return try {
            client.insertRecords(listOf(record))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun writeHeartRateRecord(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        samples: List<HeartRateRecord.Sample>
    ): Boolean {
        val record = HeartRateRecord(
            startTime = startTime.atZone(ZoneId.systemDefault()).toInstant(),
            endTime = endTime.atZone(ZoneId.systemDefault()).toInstant(),
            startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
            endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime),
            samples = samples,
            metadata = metadata
        )

        return try {
            client.insertRecords(listOf(record))
            true
        } catch (e: Exception) {
            false
        }
    }

    // Helper function to create heart rate samples
    fun createHeartRateSample(
        time: LocalDateTime,
        beatsPerMinute: Long
    ): HeartRateRecord.Sample {
        return HeartRateRecord.Sample(
            time = time.atZone(ZoneId.systemDefault()).toInstant(),
            beatsPerMinute = beatsPerMinute
        )
    }

    suspend fun writeExerciseSession(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        title: String,
        notes: String
    ): Boolean {
        // Add missing imports
        val exerciseRecord = ExerciseSessionRecord(
            startTime = startTime.atZone(ZoneId.systemDefault()).toInstant(),
            endTime = endTime.atZone(ZoneId.systemDefault()).toInstant(),
            startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
            endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            title = title,
            notes = notes,
            metadata = metadata     // Added missing metadata
        )

        return try {
            client.insertRecords(listOf(exerciseRecord))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun writeSleepSession(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        notes: String = ""
    ): Boolean {
        val sleepRecord = SleepSessionRecord(
            startTime = startTime.atZone(ZoneId.systemDefault()).toInstant(),
            endTime = endTime.atZone(ZoneId.systemDefault()).toInstant(),
            startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
            endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime),
            stages = emptyList(),
            notes = notes,
            title = "Sleep",
            metadata = metadata
        )

        return try {
            client.insertRecords(listOf(sleepRecord))
            true
        } catch (e: Exception) {
            false
        }
    }

    // Sleep Stage
    suspend fun writeSleepSessionWithStages(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        stages: List<SleepStage>,
        notes: String = ""
    ): Boolean {
        val sleepStages = stages.map { stage ->
            SleepSessionRecord.Stage(
                stage = stage.type,
                startTime = stage.startTime.atZone(ZoneId.systemDefault()).toInstant(),
                endTime = stage.endTime.atZone(ZoneId.systemDefault()).toInstant(),
            )
        }

        val sleepRecord = SleepSessionRecord(
            startTime = startTime.atZone(ZoneId.systemDefault()).toInstant(),
            endTime = endTime.atZone(ZoneId.systemDefault()).toInstant(),
            startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
            endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime),
            stages = sleepStages,
            notes = notes,
            title = "Sleep",
            metadata = metadata
        )

        return try {
            client.insertRecords(listOf(sleepRecord))
            true
        } catch (e: Exception) {
            false
        }
    }
    data class SleepStage(
        val type: Int,
        val startTime: LocalDateTime,
        val endTime: LocalDateTime
    ) {
        companion object {
            val STAGE_TYPE_AWAKE = SleepSessionRecord.STAGE_TYPE_AWAKE
            val STAGE_TYPE_DEEP = SleepSessionRecord.STAGE_TYPE_DEEP
            val STAGE_TYPE_LIGHT = SleepSessionRecord.STAGE_TYPE_LIGHT
            val STAGE_TYPE_REM = SleepSessionRecord.STAGE_TYPE_REM
            val STAGE_TYPE_UNKNOWN = SleepSessionRecord.STAGE_TYPE_UNKNOWN
        }
    }

    //
    override fun getSteps(start: Instant, end: Instant): Flow<Long> {
        TODO("Not yet implemented")
    }

    override fun getLatestHeartRate(): Any {
        TODO("Not yet implemented")
    }

    override fun getLastSleepSession(): Any {
        TODO("Not yet implemented")
    }
}
