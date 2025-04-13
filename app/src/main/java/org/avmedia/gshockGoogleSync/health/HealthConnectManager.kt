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
import java.time.Instant
import java.time.LocalDateTime


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

    /////////// Write
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
        startTime: Instant,
        endTime: Instant,
        steps: Long
    ): Boolean {
        val record = StepsRecord(
            count = steps,
            startTime = startTime,
            endTime = endTime,
            startZoneOffset = null,
            endZoneOffset = null,
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
        startTime: Instant,
        samples: List<HeartRateRecord.Sample>
    ): Boolean {
        val record = HeartRateRecord(
            startTime = startTime,
            endTime = startTime.plusSeconds(samples.size.toLong()),
            samples = samples,
            startZoneOffset = null,
            endZoneOffset = null,
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
        time: Instant,
        beatsPerMinute: Long
    ): HeartRateRecord.Sample {
        return HeartRateRecord.Sample(
            time = time,
            beatsPerMinute = beatsPerMinute
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
