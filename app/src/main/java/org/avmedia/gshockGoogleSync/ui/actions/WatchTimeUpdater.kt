package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.scratchpad.TimeSettingsStorage
import org.avmedia.gshockGoogleSync.ui.time.SolarTimeHelper
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchTimeUpdater @Inject constructor(
    private val api: GShockRepository,
    private val timeSettingsStorage: TimeSettingsStorage,
    @ApplicationContext private val context: Context
) {
    /**
     * Calculates the correct time offset according to the user's settings
     * (e.g. Local Mean Time, Local Solar Time, Fine adjustment) and sends it to the watch.
     */
    suspend fun updateTime() {
        timeSettingsStorage.load()
        val fineAdjustment = LocalDataStorage.getFineTimeAdjustment(context)
        val timeZoneOption = timeSettingsStorage.getTimeZoneOption()
        val timeZoneOffset = SolarTimeHelper.calculateTimeOffset(context, timeZoneOption)
        val timeMs = System.currentTimeMillis() + fineAdjustment + timeZoneOffset

        Timber.d($$"Setting time to watch with fine adjustment: $fineAdjustment and timezone offset: $timeZoneOffset")
        api.setTime(timeMs = timeMs)
    }
}