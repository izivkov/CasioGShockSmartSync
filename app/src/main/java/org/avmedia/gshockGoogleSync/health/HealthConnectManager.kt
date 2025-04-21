package org.avmedia.gshockGoogleSync.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata.Companion.autoRecordedWithId
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

class HealthConnectManager(private val context: Context) : IHealthConnectManager {
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }
    private val healthRecordsContainer by lazy { HealthRecordsContainer() }

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

    enum class RecordClass {
        SLEEP_SESSION,
        EXERCISE_SESSION,
        STEPS,
        HEART_RATE
    }

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

    private fun addRecord(
        start: Instant,
        end: Instant,
        recordClass: RecordClass,
        type: Int? = null,
        steps: Long? = null,
        heartRateSamples: List<HeartRateRecord.Sample>? = null,
        title: String
    ) {
        val record: Record = when (recordClass) {
            RecordClass.SLEEP_SESSION -> {
                SleepSessionRecord(
                    metadata = metadata,
                    startTime = start,
                    startZoneOffset = ZoneOffset.UTC,
                    endTime = end,
                    endZoneOffset = ZoneOffset.UTC,
                    title = title,
                    notes = "Sleep recorded by G-Shock"
                )
            }

            RecordClass.HEART_RATE -> {
                require(!heartRateSamples.isNullOrEmpty()) { "Heart rate samples cannot be empty" }
                require(heartRateSamples.all { it.beatsPerMinute > 0 }) {
                    "All heart rate samples must have a positive beats per minute value"
                }

                HeartRateRecord(
                    startTime = start,
                    startZoneOffset = ZoneOffset.UTC,
                    endTime = end,
                    endZoneOffset = ZoneOffset.UTC,
                    samples = heartRateSamples,
                    metadata = metadata
                )
            }

            RecordClass.STEPS -> {
                requireNotNull(steps) { "Steps count is required" }
                require(steps > 0) { "Steps count must be greater than 0" }

                StepsRecord(
                    startTime = start,
                    endTime = end,
                    count = steps,
                    startZoneOffset = ZoneOffset.UTC,
                    endZoneOffset = ZoneOffset.UTC,
                    metadata = metadata
                )
            }

            RecordClass.EXERCISE_SESSION -> {
                requireNotNull(type) { "Exercise type is required" }
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
        }
        healthRecordsContainer.addRecord(record)
    }

    // 5. Insert all records
    private suspend fun insertToHealthConnect(healthConnectClient: HealthConnectClient) {
        healthConnectClient.insertRecords(
            healthRecordsContainer.getAllRecords()
        )
        println("Inserted ${healthRecordsContainer.getAllRecords().size} records to Health Connect")
    }

    suspend fun simulateAndInsertWatchData() {
        val startTime = Instant.now().minusSeconds(3600) // 1 hour ago

        // Add sleep record (typically from previous night)
        val sleepStart = startTime.minusSeconds(28800) // 8 hours before
        val sleepEnd = startTime.minusSeconds(1800) // 30 minutes before
        addRecord(
            recordClass = RecordClass.SLEEP_SESSION,
            start = sleepStart,
            end = sleepEnd,
            title = "Night Sleep"
        )

        // Simulate heart rate data
        val heartRateSamples = listOf(
            HeartRateRecord.Sample(startTime.plusSeconds(0), 75),
            HeartRateRecord.Sample(startTime.plusSeconds(60), 80),
            HeartRateRecord.Sample(startTime.plusSeconds(120), 85),
            HeartRateRecord.Sample(startTime.plusSeconds(180), 82)
        )

        // Add heart rate record
        addRecord(
            recordClass = RecordClass.HEART_RATE,
            type = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
            start = startTime,
            end = startTime.plusSeconds(180),
            heartRateSamples = heartRateSamples,
            title = "Heart Rate Measurement"
        )

        // Rest of the existing simulation code...
        addRecord(
            recordClass = RecordClass.SLEEP_SESSION,
            type = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            start = startTime,
            end = startTime.plusSeconds(1800),
            title = "Morning Run"
        )

        addRecord(
            recordClass = RecordClass.STEPS,
            type = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            start = startTime.plusSeconds(2400),
            end = startTime.plusSeconds(3000),
            steps = 2500,
            title = "Walking"
        )

        addRecord(
            recordClass = RecordClass.EXERCISE_SESSION,
            type = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            start = startTime.plusSeconds(3600),
            end = startTime.plusSeconds(4200),
            title = "Gym Workout"
        )

        addRecord(
            recordClass = RecordClass.EXERCISE_SESSION,
            type = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            start = startTime.plusSeconds(4800),
            end = startTime.plusSeconds(5400),
            title = "Evening Run"
        )

        addRecord(
            recordClass = RecordClass.STEPS,
            type = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            start = startTime.plusSeconds(6000),
            end = startTime.plusSeconds(6600),
            steps = 1800,
            title = "Evening Walk"
        )

        // Insert all records to Health Connect
        insertToHealthConnect(healthConnectClient)
    }

    // Data Aggregation
    data class AggregatedHealthData(
        val steps: Int = 0,
        val minHeartRate: Int = 0,
        val maxHeartRate: Int = 0,
        val avgHeartRate: Int = 0,
        val sleepDurationMinutes: Int = 0,
        val exerciseSessions: List<String> = emptyList()
    )

    suspend fun getAggregatedHealthData(
        startTime: Instant,
        endTime: Instant
    ): AggregatedHealthData {
        val timeRangeFilter = TimeRangeFilter.between(startTime, endTime)

        return runCatching {
            // Steps: Merge overlapping intervals and sum
            val totalSteps = runCatching {
                val stepsRequest = ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = timeRangeFilter
                )
                val stepsRecords = healthConnectClient.readRecords(stepsRequest).records
                mergeOverlappingSteps(stepsRecords)
            }.getOrDefault(0)

            // Heart Rate: Handle overlapping samples by taking closest values
            val (minHeartRate, maxHeartRate, avgHeartRate) = runCatching {
                val heartRateRequest = ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = timeRangeFilter
                )
                val heartRateRecords = healthConnectClient.readRecords(heartRateRequest).records
                processHeartRateRecords(heartRateRecords)
            }.getOrDefault(Triple(0, 0, 0))

            // Sleep: Merge overlapping sleep sessions
            val totalSleepMinutes = runCatching {
                val sleepRequest = ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = timeRangeFilter
                )
                val sleepRecords = healthConnectClient.readRecords(sleepRequest).records
                calculateTotalSleepTime(sleepRecords)
            }.getOrDefault(0)

            // Exercise: Remove duplicates within small time windows
            val exerciseTitles = runCatching {
                val exerciseRequest = ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = timeRangeFilter
                )
                val exerciseRecords = healthConnectClient.readRecords(exerciseRequest).records
                deduplicateExerciseSessions(exerciseRecords)
            }.getOrDefault(emptyList())

            AggregatedHealthData(
                steps = totalSteps,
                minHeartRate = minHeartRate,
                maxHeartRate = maxHeartRate,
                avgHeartRate = avgHeartRate,
                sleepDurationMinutes = totalSleepMinutes,
                exerciseSessions = exerciseTitles
            )
        }.onFailure { it.printStackTrace() }
            .getOrDefault(AggregatedHealthData())
    }

    private fun mergeOverlappingSteps(records: List<StepsRecord>): Int {
        if (records.isEmpty()) return 0

        // Sort by start time
        val sortedRecords = records.sortedBy { it.startTime }
        var totalSteps = 0
        var lastEndTime = sortedRecords[0].startTime

        for (record in sortedRecords) {
            if (record.startTime > lastEndTime) {
                // No overlap
                totalSteps += record.count.toInt()
            } else {
                // Handle overlap by calculating the non-overlapping portion
                val overlapRatio = 1 - (java.time.Duration.between(lastEndTime, record.endTime)
                    .toMillis().toDouble() /
                        java.time.Duration.between(record.startTime, record.endTime).toMillis())
                totalSteps += (record.count.toInt() * (1 - overlapRatio)).toInt()
            }
            lastEndTime = maxOf(lastEndTime, record.endTime)
        }
        return totalSteps
    }

    private fun processHeartRateRecords(records: List<HeartRateRecord>): Triple<Int, Int, Int> {
        val samples = mutableMapOf<Instant, Int>()

        // Keep only the latest sample for each timestamp
        records.forEach { record ->
            record.samples.forEach { sample ->
                samples[sample.time] = sample.beatsPerMinute.toInt()
            }
        }

        val heartRates = samples.values
        return if (heartRates.isNotEmpty()) {
            Triple(
                heartRates.minOrNull()!!,
                heartRates.maxOrNull()!!,
                heartRates.average().toInt()
            )
        } else {
            Triple(0, 0, 0)
        }
    }

    private fun calculateTotalSleepTime(records: List<SleepSessionRecord>): Int {
        if (records.isEmpty()) return 0

        // Sort by start time
        val sortedRecords = records.sortedBy { it.startTime }
        var totalMinutes = 0
        var lastEndTime = sortedRecords[0].startTime

        for (record in sortedRecords) {
            if (record.startTime > lastEndTime) {
                // No overlap
                totalMinutes += java.time.Duration.between(record.startTime, record.endTime)
                    .toMinutes().toInt()
            } else {
                // Handle overlap by taking the union of intervals
                totalMinutes += java.time.Duration.between(lastEndTime, record.endTime)
                    .toMinutes().toInt()
            }
            lastEndTime = maxOf(lastEndTime, record.endTime)
        }
        return totalMinutes
    }

    private fun deduplicateExerciseSessions(records: List<ExerciseSessionRecord>): List<String> {
        // Group sessions by title and keep only the latest one within a 5-minute window
        return records
            .groupBy { it.title }
            .mapNotNull { (title, sessions) ->
                title?.takeIf {
                    sessions.none { session1 ->
                        sessions.any { session2 ->
                            session1 != session2 &&
                                    abs(
                                        java.time.Duration.between(
                                            session1.startTime,
                                            session2.startTime
                                        )
                                            .toMinutes()
                                    ) < 5
                        }
                    }
                }
            }
    }
}
