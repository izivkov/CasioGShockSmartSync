package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.TorchCallback
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar

object FlashlightHelper {
    private lateinit var cameraId: String
    private var currentState = false
    private lateinit var cameraManager: CameraManager
    private lateinit var translateApi: TranslateRepository

    private val torchCallback = object : TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            if (cameraId == FlashlightHelper.cameraId) {
                currentState = enabled
            }
        }
    }

    private fun turnOnOff(context: Context, state: Boolean) {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (cameraManager.cameraIdList.isEmpty()) {
            AppSnackbar(translateApi.getString(context, R.string.flashlight_not_available))
            return
        }

        cameraId = cameraManager.cameraIdList[0] // 0 is for the back camera
        cameraManager.setTorchMode(cameraId, state)
        currentState = state
    }

    fun toggle(context: Context) {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraManager.registerTorchCallback(torchCallback, null)
        turnOnOff(context, !currentState)
    }

    fun isOn(): Boolean {
        return currentState
    }
}
