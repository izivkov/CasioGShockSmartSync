package org.avmedia.gshockGoogleSync.data.repository

import org.avmedia.gshockGoogleSync.health.HealthConnectManager
import org.avmedia.gshockGoogleSync.health.IHealthConnectManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthRepository @Inject constructor(
    healthManager: IHealthConnectManager
) : IHealthConnectManager by healthManager