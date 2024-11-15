/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-13, 10:56 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-13, 10:56 p.m.
 */

package org.avmedia.gShockSmartSyncCompose.ui.actions

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraCaptureHelper(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    fun initCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            imageCapture = ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                .build()

            try {
                cameraProvider.unbindAll()

                // Bind the image capture use case without any preview
                cameraProvider.bindToLifecycle(
                    (context as androidx.lifecycle.LifecycleOwner),
                    cameraSelector,
                    imageCapture
                )
            } catch (exc: Exception) {
                Timber.e("Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(context))

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun takePicture(onImageCaptured: (String) -> Unit, onError: (String) -> Unit) {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Images")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "Unknown error")
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onImageCaptured("Image saved at ${outputFileResults.savedUri}")
                }
            }
        )
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}
