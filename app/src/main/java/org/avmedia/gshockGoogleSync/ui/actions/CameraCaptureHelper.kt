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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.avmedia.gshockGoogleSync.utils.ActivityProvider
import timber.log.Timber

class CameraCaptureHelper(
        private val context: Context,
        private val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
) {

    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService? = null
    private val mediaActionSound = MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }

    suspend fun takePicture(): Result<String> =
            runCatching {
                if (!isCameraBound()) {
                    initCamera()
                }

                captureImage()
            }
                    .onFailure { stopCamera() }

    private fun isCameraBound(): Boolean {
        return imageCapture != null
    }

    private suspend fun initCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        imageCapture = ImageCapture.Builder().setFlashMode(ImageCapture.FLASH_MODE_AUTO).build()

        bindCameraToLifecycle()
    }

    private suspend fun bindCameraToLifecycle() =
            withContext(Dispatchers.Main) {
                val cameraProvider = getCameraProvider(context)
                val currentActivity =
                        ActivityProvider.getCurrentActivity()
                                ?: throw IllegalStateException(
                                        "No active activity found to bind camera"
                                )

                if (currentActivity !is LifecycleOwner) {
                    throw IllegalStateException("Current activity is not a LifecycleOwner")
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(currentActivity, cameraSelector, imageCapture!!)
                    Timber.d("Camera setup complete")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to bind camera")
                    throw e
                }
            }

    private suspend fun getCameraProvider(context: Context): ProcessCameraProvider =
            suspendCancellableCoroutine { continuation ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener(
                        {
                            try {
                                continuation.resume(cameraProviderFuture.get())
                            } catch (e: Exception) {
                                continuation.resumeWithException(e)
                            }
                        },
                        ContextCompat.getMainExecutor(context)
                )
            }

    private suspend fun captureImage(): String = suspendCancellableCoroutine { continuation ->
        val imageCapture =
                imageCapture
                        ?: run {
                            continuation.resumeWithException(
                                    IllegalStateException("Camera not initialized")
                            )
                            return@suspendCancellableCoroutine
                        }

        val outputOptions = createOutputOptions()
        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exception: ImageCaptureException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(exception)
                        }
                    }

                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedUri = outputFileResults.savedUri
                        if (savedUri == null) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                        IllegalStateException("Image saved but URI is null")
                                )
                            }
                            return
                        }

                        // Add EXIF data
                        runCatching {
                            context.contentResolver.openFileDescriptor(savedUri, "rw")?.use { pfd ->
                                val exif = ExifInterface(pfd.fileDescriptor)
                                addExifMetadata(exif)
                                exif.saveAttributes()
                            }
                            mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
                        }
                                .onFailure { e -> Timber.e(e, "Error adding EXIF data") }

                        if (continuation.isActive) {
                            continuation.resume("Image saved at $savedUri")
                        }
                        stopCamera()
                    }
                }
        )
    }

    private fun addExifMetadata(exif: ExifInterface) {
        val timestamp =
                SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                        .format(System.currentTimeMillis())

        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, "Created by GShock Smart Sync")
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "G-Shock Smart Sync")
        exif.setAttribute(
                ExifInterface.TAG_COPYRIGHT,
                "https://github.com/izivkov/CasioGShockSmartSync"
        )
        exif.setAttribute(ExifInterface.TAG_DATETIME, timestamp)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, timestamp)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, timestamp)
    }

    private fun createOutputOptions(): ImageCapture.OutputFileOptions {
        val name =
                SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                        .format(System.currentTimeMillis())

        val contentValues =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                    }
                }

        return ImageCapture.OutputFileOptions.Builder(
                        context.contentResolver,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                )
                .build()
    }

    private fun stopCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
                {
                    try {
                        val provider = providerFuture.get()
                        provider.unbindAll()
                        cameraExecutor?.shutdown()
                        imageCapture = null
                    } catch (e: Exception) {
                        Timber.e(e, "Error stopping camera")
                    }
                },
                ContextCompat.getMainExecutor(context)
        )
    }
}
