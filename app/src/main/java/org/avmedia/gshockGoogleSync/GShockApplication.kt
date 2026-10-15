package org.avmedia.gshockGoogleSync

import android.annotation.SuppressLint
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.pairing.CompanionDevicePresenceMonitor
import org.avmedia.gshockGoogleSync.pairing.DeviceAssociationManager
import org.avmedia.gshockGoogleSync.services.DeviceManager
import org.avmedia.gshockGoogleSync.ui.actions.ActionRunner
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.utils.ActivityProvider
import org.avmedia.gshockGoogleSync.utils.CrashReportHelper
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class GShockApplication : Application(), IScreenManager {
    private lateinit var eventHandler: MainEventHandler

    @Inject
    lateinit var deviceManager: DeviceManager

    @Inject
    lateinit var repository: GShockRepository

    @Inject
    lateinit var companionDevicePresenceMonitor: CompanionDevicePresenceMonitor

    @Inject
    lateinit var deviceAssociationManager: DeviceAssociationManager

    @Inject
    lateinit var actionRunner: ActionRunner

    @Inject
    lateinit var appScreenState: AppScreenState

    fun init() {
        Timber.i("Initializing GShockApplication")
        CoroutineScope(Dispatchers.IO).launch {
            deviceAssociationManager.init()
        }
    }

    @SuppressLint("NewApi")
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber planted for Debug")
        }

        CrashReportHelper.installGlobalHandler(this)
        ActivityProvider.initialize(this)
        eventHandler = MainEventHandler(context = this, repository = repository, screenManager = this)
        eventHandler.setupEventSubscription()
        actionRunner.setupActionSubscriptions()
    }

    override fun onTerminate() {
        super.onTerminate()
    }

    override fun showContentSelector(repository: GShockRepository) {
        appScreenState.showContentSelector(repository)
    }

    override fun showRunActionsScreen() {
        appScreenState.showRunActionsScreen()
    }

    override fun showPreConnectionScreen() {
        appScreenState.showPreConnectionScreen()
    }

    override fun showInitialScreen() {
        appScreenState.showInitialScreen()
    }

    override fun showError(message: String) {
        appScreenState.showError(message)
        AppSnackbar(message)
    }
}
