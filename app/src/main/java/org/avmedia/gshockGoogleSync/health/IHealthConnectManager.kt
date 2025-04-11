package org.avmedia.gshockGoogleSync.health

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface IHealthConnectManager {
    suspend fun hasPermission(): Boolean
    fun getSteps(start: Instant, end: Instant): Flow<Long>
    abstract fun getLatestHeartRate(): Any
    abstract fun getLastSleepSession(): Any
}