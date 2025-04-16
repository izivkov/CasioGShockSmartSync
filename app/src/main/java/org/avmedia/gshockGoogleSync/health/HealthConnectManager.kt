package org.avmedia.gshockGoogleSync.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.metadata.Metadata.Companion.autoRecordedWithId
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

class HealthConnectManager(private val context: Context) : IHealthConnectManager {
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    private val _permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
    )
    val permissions: Set<String> get() = _permissions

    fun requestPermissionsActivityContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    suspend fun hasPermissions(): Boolean {
        return try {
            healthConnectClient.permissionController.getGrantedPermissions()
                .containsAll(_permissions)
        } catch (e: Exception) {
            false
        }
    }

    fun isHealthConnectAvailable(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    ///////////// READ / WRITE data

    private val gshockDataOriginName = "G-Shock Smart Sync"

    private val device = Device(
        manufacturer = "Casio",
        model = "DW-H5600",
        type = Device.TYPE_WATCH,
    )
    private val metadata = autoRecordedWithId(
        id = gshockDataOriginName,
        device
    )

    // Write step data
    suspend fun writeSteps(start: Instant, end: Instant, count: Long) {
        val record = StepsRecord(
            startTime = start,
            endTime = end,
            count = count,
            startZoneOffset = ZoneOffset.UTC,
            endZoneOffset = ZoneOffset.UTC,
            metadata = metadata
        )
        healthConnectClient.insertRecords(listOf(record))
    }

    // Write exercise session (e.g., walking)
    suspend fun writeExerciseSession(start: Instant, end: Instant, type: String) {
        val session = ExerciseSessionRecord(
            metadata = metadata,
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            title = "G-Shock Exercise",
        )
        healthConnectClient.insertRecords(listOf(session))
    }

    // Write sleep session
    suspend fun writeSleepSession(start: Instant, end: Instant) {
        val record = SleepSessionRecord(
            startTime = start,
            endTime = end,
            startZoneOffset = ZoneOffset.UTC,
            endZoneOffset = ZoneOffset.UTC,
            title = "Sleep (G-Shock)",
            metadata = metadata
        )
        healthConnectClient.insertRecords(listOf(record))
    }

    // Aggregate steps for a session period, from G-Shock only
    suspend fun aggregateStepsForPeriod(start: Instant, end: Instant): AggregationResult {
        return healthConnectClient.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                dataOriginFilter = setOf(DataOrigin(gshockDataOriginName))
            )
        )
    }

    // Aggregate exercise duration
    suspend fun aggregateExerciseDuration(
        start: Instant,
        end: Instant
    ): AggregationResult {
        return healthConnectClient.aggregate(
            AggregateRequest(
                metrics = setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                dataOriginFilter = setOf(DataOrigin(gshockDataOriginName))
            )
        )
    }

    // Aggregate sleep duration
    suspend fun aggregateSleepDuration(start: Instant, end: Instant): AggregationResult {
        return healthConnectClient.aggregate(
            AggregateRequest(
                metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                dataOriginFilter = setOf(DataOrigin(gshockDataOriginName))
            )
        )
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
