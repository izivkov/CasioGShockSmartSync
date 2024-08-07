package org.avmedia.gShockPhoneSync.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.MainActivity.Companion.getLifecycleScope
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo

object DnDSetter {
    private var connected = false

    fun start() {
        val dnDSetActions = arrayOf(
            EventAction("DnD On") {
                if (connected && WatchInfo.hasDnD && LocalDataStorage.getMirrorPhoneDnd()) {
                    getLifecycleScope().launch(Dispatchers.IO) {
                        val settings = api().getSettings()
                        settings.DnD = true
                        api().setSettings(settings)
                    }
                }
            },
            EventAction("DnD Off") {
                if (connected && WatchInfo.hasDnD && LocalDataStorage.getMirrorPhoneDnd()) {
                    getLifecycleScope().launch(Dispatchers.IO) {
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