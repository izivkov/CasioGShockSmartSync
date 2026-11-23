package org.avmedia.gshockGoogleSync

import android.annotation.SuppressLint
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.pairing.CompanionDeviceCoordinator
import org.avmedia.gshockGoogleSync.services.DeviceManager
import org.avmedia.gshockGoogleSync.ui.actions.ActionRunner
import org.avmedia.gshockGoogleSync.utils.ActivityProvider
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class GShockApplication : Application() {
    private lateinit var eventHandler: MainEventHandler

    @Inject
    lateinit var deviceManager: DeviceManager

    @Inject
    lateinit var repository: GShockRepository

    @Inject
    lateinit var companionDeviceCoordinator: CompanionDeviceCoordinator

    @Inject
    lateinit var actionRunner: ActionRunner

    @Inject
    lateinit var sessionManager: org.avmedia.gshockGoogleSync.session.SessionManager

    @Inject
    lateinit var sessionEventBridge: org.avmedia.gshockGoogleSync.session.SessionEventBridge

    fun init() {
        Timber.i("Initializing GShockApplication")
        CoroutineScope(Dispatchers.IO).launch {
            companionDeviceCoordinator.init()
        }
    }

    @SuppressLint("NewApi")
    override fun onCreate() {
        super.onCreate()
        org.avmedia.gshockGoogleSync.webhook.HaSentView.appContext = applicationContext
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber planted for Debug")
        }

        org.avmedia.gshockapi.io.H5600HealthIO.rawDump =
            org.avmedia.gshockapi.io.H5600RawDump(org.avmedia.gshockGoogleSync.ui.health.HealthLogShare.dir(this))
        // Workouts: the raw bytes of every transfer are kept (failed or not); a workout is marked fetched on the watch only after it is stored.
        org.avmedia.gshockapi.io.H5600WorkoutIO.rawDump =
            org.avmedia.gshockapi.io.H5600RawDump(org.avmedia.gshockGoogleSync.ui.health.HealthLogShare.dir(this), prefix = "workout-")
        org.avmedia.gshockapi.io.H5600WorkoutIO.sink =
            { org.avmedia.gshockGoogleSync.history.WorkoutStore.save(applicationContext, it) }
        org.avmedia.gshockapi.casio.ConnectionKeepAlive.logFile =
            java.io.File(org.avmedia.gshockGoogleSync.ui.health.HealthLogShare.dir(this), org.avmedia.gshockapi.io.H5600RawDump.CONNECTION_LOG)
        org.avmedia.gshockGoogleSync.ui.health.H5600Session.onStored =
            { org.avmedia.gshockGoogleSync.history.HistoryStore.record(applicationContext, it) }
        org.avmedia.gshockGoogleSync.ui.health.H5600Session.dayMerger =
            { org.avmedia.gshockGoogleSync.history.HistoryStore.mergeDay(applicationContext, it) }
        ActivityProvider.initialize(this)
        eventHandler = MainEventHandler(
            context = this,
            repository = repository,
            sessionManager = sessionManager
        )
        eventHandler.setupEventSubscription()
        actionRunner.setupActionSubscriptions()
        sessionEventBridge.setupEventBridge()
    }

    override fun onTerminate() {
        super.onTerminate()
    }
}