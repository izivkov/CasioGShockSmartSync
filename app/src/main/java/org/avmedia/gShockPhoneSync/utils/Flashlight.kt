/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-13, 10:56 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-13, 10:56 p.m.
 */

package org.avmedia.gShockPhoneSync.utils

import android.content.Context
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.TorchCallback
import androidx.core.content.getSystemService
import timber.log.Timber


object Flashlight {
    private lateinit var cameraId: String
    private var currentState = false
    private lateinit var cameraManager: CameraManager

    private var torchCallback: TorchCallback = object : TorchCallback() {
        override fun onTorchModeUnavailable(cameraId: String) {
            super.onTorchModeUnavailable(cameraId)
        }

        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            if (cameraId == Flashlight.cameraId) {
                currentState = enabled
            }
        }
    }

    private fun turnOnOff (context:Context, state: Boolean) {
        if (cameraManager.cameraIdList.isEmpty()) {
            Timber.d("Flashlight not available")
            Utils.snackBar(context, "Flashlight not available")
            return
        }

        cameraId = cameraManager!!.cameraIdList[0] // 0 is for back camera and 1 is for front camera
        cameraManager?.setTorchMode(cameraId, state)
    }

    fun toggle (context: Context) {
        cameraManager = (context.getSystemService() as CameraManager?)!!
        if (cameraManager == null) {
            Utils.snackBar(context, "Flashlight not available")
            return
        }

        cameraManager?.registerTorchCallback(torchCallback, null)
        turnOnOff(context, !currentState)
    }
}