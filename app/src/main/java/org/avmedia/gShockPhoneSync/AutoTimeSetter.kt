/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:24 p.m.
 */

package org.avmedia.gShockPhoneSync

import android.app.Activity
import androidx.camera.core.CameraSelector
import org.avmedia.gShockPhoneSync.ble.Connection
import org.avmedia.gShockPhoneSync.ui.actions.ActionsModel
import org.avmedia.gShockPhoneSync.ui.events.EventsModel
import org.avmedia.gShockPhoneSync.utils.CameraCapture
import org.avmedia.gShockPhoneSync.utils.WatchDataEvents
import timber.log.Timber
import java.time.Clock

open class AutoTimeSetter {
    init {
        val name = this.javaClass.simpleName
        WatchDataEvents.addSubject("AUTO_TIME_SET")
        WatchDataEvents.subscribe(name, "AUTO_TIME_SET", onNext = {
            updateTimeAndCalender()
        })
    }

    private fun updateTimeAndCalender() {
        // INZ test only, remove later...
        (MainActivity.applicationContext() as Activity).runOnUiThread {
            val cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            CameraCapture(MainActivity.applicationContext(), cameraSelector).start()
            Timber.d("Photo taken...")
        }
        // end remove.

        Connection.sendMessage("{action: \"SET_TIME\", value: ${Clock.systemDefaultZone().millis()}}")
        Connection.sendMessage("{action: \"SET_REMINDERS\", value: ${EventsModel.getSelectedEvents()}}")
    }
}
