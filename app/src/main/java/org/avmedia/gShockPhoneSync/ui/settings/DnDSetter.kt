package org.avmedia.gShockPhoneSync.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.MainActivity.Companion.getLifecycleScope
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber

object DnDSetter {
    private var connected = false

    init {
        getLifecycleScope().launch(Dispatchers.IO) {

            val dnDSetActions = arrayOf(
                EventAction("DnD On") {
                    if (connected) {
                        getLifecycleScope().launch(Dispatchers.IO) {
                            Timber.i("DnDSetter: got DnD On")
                            val settings = api().getSettings()
                            settings.DnD = true
                            api().setSettings(settings)
                        }
                    }
                },
                EventAction("DnD Off") {
                    if (connected) {
                        getLifecycleScope().launch(Dispatchers.IO) {
                            Timber.i("DnDSetter: got DnD Off")
                            val settings = api().getSettings()
                            settings.DnD = false
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

            ProgressEvents.runEventActions(this.javaClass.name, dnDSetActions)
        }
    }
}