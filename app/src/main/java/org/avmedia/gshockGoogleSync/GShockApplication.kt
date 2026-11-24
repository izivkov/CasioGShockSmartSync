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
        org.avmedia.gshockGoogleSync.homeassistant.HaSentView.appContext = applicationContext
        // The removed HTTP calls feature: drop what it stored, once and quietly.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                org.avmedia.gshockGoogleSync.utils.LocalDataStorage.deleteMatching(
                    applicationContext, org.avmedia.gshockGoogleSync.homeassistant.RemovedFeatureCleanup::isRemoved,
                )
            }.onFailure { Timber.w(it, "Cleanup of removed settings failed") }
        }
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber planted for Debug")
        }

        // The App log is the only log: the transfer exports of earlier versions are removed.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { org.avmedia.gshockGoogleSync.ui.health.HealthLogShare.dropOldExports(applicationContext) }
                .onFailure { Timber.w(it, "Removing the old transfer exports failed") }
        }
        // Workouts: a workout is marked fetched on the watch only after it is stored.
        org.avmedia.gshockapi.io.H5600WorkoutIO.sink =
            { org.avmedia.gshockGoogleSync.history.WorkoutStore.save(applicationContext, it) }
        // The App log: the app's lines and the library's (its log hook) in one rolling file.
        org.avmedia.gshockGoogleSync.log.AppLog.install(
            java.io.File(org.avmedia.gshockGoogleSync.ui.health.HealthLogShare.dir(this), org.avmedia.gshockGoogleSync.log.AppLog.FILE_NAME),
        )
        org.avmedia.gshockGoogleSync.ui.health.H5600Session.onStored =
            { org.avmedia.gshockGoogleSync.history.HistoryStore.record(applicationContext, it) }
        org.avmedia.gshockGoogleSync.ui.health.H5600Session.dayMerger =
            { org.avmedia.gshockGoogleSync.history.HistoryStore.mergeDay(applicationContext, it) }
        // Before the first connection: which watch keeps connected in background (migrated once from the old behaviour).
        org.avmedia.gshockGoogleSync.session.KeepConnected.load(applicationContext)
        ActivityProvider.initialize(this)
        eventHandler = MainEventHandler(
            context = this,
            repository = repository,
            sessionManager = sessionManager
        )
        eventHandler.setupEventSubscription()
        actionRunner.setupActionSubscriptions()
        sessionEventBridge.setupEventBridge()
        // The same start with or without a screen (a reboot, a killed app: the companion device service starts us).
        org.avmedia.gshockGoogleSync.log.AppLog.log(
            org.avmedia.gshockGoogleSync.log.LogSource.App,
            "App start: session events, Home Assistant, background reads and the watch coordinator ready; " +
                "Keep connected in background: ${org.avmedia.gshockGoogleSync.session.KeepConnected.holder.value ?: "none"}",
        )
    }

    override fun onTerminate() {
        super.onTerminate()
    }
}