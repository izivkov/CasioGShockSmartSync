package org.avmedia.gShockSmartSyncCompose.ui.actions

import android.content.Context
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.TorchCallback
import org.avmedia.gShockSmartSyncCompose.ui.common.AppSnackbar

object FlashlightHelper {
    private lateinit var cameraId: String
    private var currentState = false
    private lateinit var cameraManager: CameraManager

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
            AppSnackbar("Flashlight not available")
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
