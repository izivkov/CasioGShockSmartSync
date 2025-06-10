/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-13, 10:56 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-13, 10:56 p.m.
 */

package org.avmedia.gshockGoogleSync.ui.actions

import android.content.ContentValues
import android.content.Context
import android.media.MediaActionSound
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import org.avmedia.gshockGoogleSync.services.LocationProvider
import org.avmedia.gshockGoogleSync.utils.ActivityProvider
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraCaptureHelper(
    private val context: Context,
    private val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
) {

    private data class CameraState(
        val imageCapture: ImageCapture? = null,
        val cameraExecutor: ExecutorService? = null,
        val mediaActionSound: MediaActionSound,
        var isCameraReady: Boolean = false
    )

    private var state = CameraState(
        mediaActionSound = MediaActionSound()
            .apply { load(MediaActionSound.SHUTTER_CLICK) },
        isCameraReady = false
    )

    fun initCamera(): Result<Unit> = runCatching {
        state = state.copy(
            cameraExecutor = Executors.newSingleThreadExecutor(),
            imageCapture = createImageCapture()
        )

        ProcessCameraProvider.getInstance(context).get()?.let { provider ->
            setupCamera(provider)
        }
    }

    private fun createImageCapture(): ImageCapture =
        ImageCapture.Builder()
            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
            .build()

    private fun setupCamera(cameraProvider: ProcessCameraProvider) {
        val currentActivity = ActivityProvider.getCurrentActivity() ?: return

        if (currentActivity !is LifecycleOwner) return

        state.imageCapture?.let { imageCapture ->
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    currentActivity,
                    cameraSelector,
                    imageCapture
                )
                state = state.copy(isCameraReady = true)
                Timber.d("Camera setup complete")
            }.onFailure { e ->
                Timber.e("Failed to bind camera: ${e.message}")
                state = state.copy(isCameraReady = false)
            }
        }
    }

    fun takePicture(
        onImageCaptured: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!state.isCameraReady) {
            // Try to reinitialize camera if it's not ready
            initCamera()
                .onSuccess {
                    // Wait a bit for camera to initialize
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (state.isCameraReady) {
                            captureImage(onImageCaptured, onError)
                        } else {
                            onError("Camera not ready")
                        }
                    }, 500)
                }
                .onFailure { e ->
                    onError("Failed to initialize camera: ${e.message}")
                }
            return
        }

        captureImage(onImageCaptured, onError)
    }

    private fun captureImage(
        onImageCaptured: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        state.imageCapture?.let { imageCapture ->
            createOutputOptions().let { outputOptions ->
                state.mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
                captureImage(
                    imageCapture,
                    outputOptions,
                    onImageCaptured,
                    onError
                )
            }
        } ?: onError("Camera not initialized")
    }

    private fun createOutputOptions(): ImageCapture.OutputFileOptions {
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
            }
        }.let { contentValues ->
            ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()
        }
    }

    private fun captureImage(
        imageCapture: ImageCapture,
        outputOptions: ImageCapture.OutputFileOptions,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Get location using LocationProvider service
        val location = LocationProvider.getLocation(context)?.let { loc ->
            android.location.Location("").apply {
                latitude = loc.latitude
                longitude = loc.longitude
            }
        }

        // Use the existing outputOptions and add metadata
        imageCapture.takePicture(
            outputOptions,  // Use the original outputOptions
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "Unknown error")
                    stopCamera()
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    outputFileResults.savedUri?.let { uri ->
                        runCatching {
                            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                                ExifInterface(pfd.fileDescriptor)
                                    .apply {
                                        // Add existing tags
                                        setAttribute(
                                            ExifInterface.TAG_USER_COMMENT,
                                            "Created by GShock Smart Sync"
                                        )
                                        setAttribute(
                                            ExifInterface.TAG_SOFTWARE,
                                            "GShock Smart Sync"
                                        )
                                        setAttribute(
                                            ExifInterface.TAG_COPYRIGHT,
                                            "https://github.com/izivkov/GshockSmartSync"
                                        )

                                        // Add date/time information
                                        val timestamp =
                                            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                                                .format(System.currentTimeMillis())
                                        setAttribute(ExifInterface.TAG_DATETIME, timestamp)
                                        setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, timestamp)
                                        setAttribute(
                                            ExifInterface.TAG_DATETIME_DIGITIZED,
                                            timestamp
                                        )

                                        saveAttributes()
                                    }
                            }
                        }.onFailure { e ->
                            Timber.e("Error adding EXIF data: ${e.message}")
                        }
                    }

                    onSuccess("Image saved at ${outputFileResults.savedUri}")
                    stopCamera()
                }
            }
        )
    }

    private fun stopCamera() {
        ProcessCameraProvider.getInstance(context).apply {
            addListener(
                {
                    get().unbindAll()
                    state.cameraExecutor?.shutdown()
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    }
}
