package org.avmedia.gShockPhoneSync.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.MainActivity.Companion.getLifecycleScope
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents

object AutoLightSetter {
    fun start() {
        val autoLightSetActions = arrayOf(
            EventAction("onSunrise") {
                getLifecycleScope().launch(Dispatchers.IO) {
                    val settings = api().getSettings()
                    settings.autoLight = false
                    api().setSettings(settings)
                }
            },
            EventAction("onSunset") {
                getLifecycleScope().launch(Dispatchers.IO) {
                    val settings = api().getSettings()
                    settings.autoLight = true
                    api().setSettings(settings)
                }
            }
        )

        ProgressEvents.runEventActions(this.javaClass.name, autoLightSetActions)
    }
}