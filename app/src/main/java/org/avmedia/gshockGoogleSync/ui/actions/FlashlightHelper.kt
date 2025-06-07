package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraManager.TorchCallback
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import timber.log.Timber

object FlashlightHelper {
    private var cameraId: String? = null
    private var currentState = false
    private var cameraManager: CameraManager? = null

    private val torchCallback = object : TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            if (cameraId == this@FlashlightHelper.cameraId) {
                currentState = enabled
            }
        }
    }

    private fun turnOnOff(context: Context, state: Boolean) = runCatching {
        getCameraManager(context)?.let { manager ->
            getCameraId(manager)?.let { id ->
                manager.setTorchMode(id, state)
                currentState = state
            }
        }
    }.onFailure { e ->
        Timber.e("Failed to turn flashlight ${if (state) "on" else "off"}: ${e.message}")
        AppSnackbar(context.getString(R.string.flashlight_not_available))
    }

    private fun getCameraManager(context: Context): CameraManager? = runCatching {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }.getOrNull()

    private fun getCameraId(manager: CameraManager): String? = runCatching {
        manager.cameraIdList.firstOrNull()
    }.getOrNull()

    fun toggle(context: Context) = runCatching {
        getCameraManager(context)?.also { manager ->
            cameraManager = manager
            manager.registerTorchCallback(torchCallback, null)
            turnOnOff(context, !currentState)
        }
    }.onFailure { e ->
        Timber.e("Failed to toggle flashlight: ${e.message}")
        AppSnackbar(context.getString(R.string.flashlight_not_available))
    }

    fun isOn(): Boolean = currentState
}
