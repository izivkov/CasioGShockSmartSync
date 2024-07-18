package org.avmedia.gShockPhoneSync.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.MainActivity.Companion.getLifecycleScope
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

object AutoLightSetter {
    private var connected = false

    init {
        val autoLightSetActions = arrayOf(
            EventAction("onSunrise") {
                if (connected && LocalDataStorage.getAutoLightNightOnly()) {
                    getLifecycleScope().launch(Dispatchers.IO) {
                        val settings = api().getSettings()
                        settings.autoLight = false
                        api().setSettings(settings)
                    }
                }
            },
            EventAction("onSunset") {
                if (connected && LocalDataStorage.getAutoLightNightOnly()) {
                    getLifecycleScope().launch(Dispatchers.IO) {
                        val settings = api().getSettings()
                        settings.autoLight = true
                        api().setSettings(settings)
                    }
                }
            },
            EventAction("Disconnect") {
                connected = false
            },
            EventAction("WatchInitializationCompleted") {
                connected = true
            },
        )

        ProgressEvents.runEventActions(this.javaClass.name, autoLightSetActions)
    }
}