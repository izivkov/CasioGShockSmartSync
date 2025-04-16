package org.avmedia.gshockGoogleSync.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata.Companion.autoRecordedWithId
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneOffset

class HealthConnectManager(private val context: Context) : IHealthConnectManager {
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
    private val healthRecordsContainer by lazy {HealthRecordsContainer()}

    private val _permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )
    val permissions: Set<String> get() = _permissions

    init {
        healthRecordsContainer.clear()
    }

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

    fun addRecord(
        start: Instant,
        end: Instant,
        type: Int,
        count: Long? = null,
        title: String
    ) {
        val record: Record = when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> {
                ExerciseSessionRecord(
                    metadata = metadata,
                    startTime = start,
                    startZoneOffset = ZoneOffset.UTC,
                    endTime = end,
                    endZoneOffset = ZoneOffset.UTC,
                    exerciseType = type,
                    title = title
                )
            }
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> {
                requireNotNull(count) { "Count is required for walking" }
                require(count > 0) { "Count must be greater than 0 for walking" }

                StepsRecord(
                    startTime = start,
                    endTime = end,
                    count = count,
                    startZoneOffset = ZoneOffset.UTC,
                    endZoneOffset = ZoneOffset.UTC,
                    metadata = metadata
                )
            }
            else -> throw IllegalArgumentException("Unsupported exercise type: $type")
        }
        healthRecordsContainer.addRecord(record)
    }

    // 5. Insert all records
    suspend fun insertToHealthConnect(healthConnectClient: HealthConnectClient) {
        healthConnectClient.insertRecords(
            healthRecordsContainer.getAllRecords()
        )
        println("Inserted ${healthRecordsContainer.getAllRecords().size} records to Health Connect")
    }


    suspend fun simulateAndInsertWatchData() {
        val startTime = Instant.now().minusSeconds(3600) // 1 hour ago

        // Simulate running session
        addRecord(
            start = startTime,
            end = startTime.plusSeconds(1800), // 30 min
            type = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            title = "Morning Run"
        )

        // Simulate walking with steps
        addRecord(
            start = startTime.plusSeconds(2400), // 40 min after start
            end = startTime.plusSeconds(3000), // 50 min after start
            type = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            count = 2500,
            title = "Walking"
        )

        // Simulate strength training
        addRecord(
            start = startTime.plusSeconds(3600), // 1 hour after start
            end = startTime.plusSeconds(4200), // 1 hour 10 min after start
            type = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = "Gym Workout"
        )

        // Another running session
        addRecord(
            start = startTime.plusSeconds(4800), // 1 hour 20 min after start
            end = startTime.plusSeconds(5400), // 1 hour 30 min after start
            type = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            title = "Evening Run"
        )

        // Another walking session
        addRecord(
            start = startTime.plusSeconds(6000), // 1 hour 40 min after start
            end = startTime.plusSeconds(6600), // 1 hour 50 min after start
            type = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            count = 1800,
            title = "Evening Walk"
        )

        // Insert all records to Health Connect
        insertToHealthConnect(healthConnectClient)
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
