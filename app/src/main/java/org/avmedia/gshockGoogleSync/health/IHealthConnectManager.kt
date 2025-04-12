package org.avmedia.gshockGoogleSync.health

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface IHealthConnectManager {
    fun getSteps(start: Instant, end: Instant): Flow<Long>
    abstract fun getLatestHeartRate(): Any
    abstract fun getLastSleepSession(): Any
}