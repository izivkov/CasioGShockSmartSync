/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-05-13, 10:56 p.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-05-13, 10:56 p.m.
 */

package org.avmedia.gShockPhoneSync.utils

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Insets
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata.FLASH_MODE_TORCH
import android.media.MediaActionSound
import android.os.Build
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.FLASH_MODE_AUTO
import androidx.camera.core.ImageCapture.FLASH_MODE_ON
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import org.avmedia.gShockPhoneSync.customComponents.ActionsModel.FileSpecs.RATIO_16_9_VALUE
import org.avmedia.gShockPhoneSync.customComponents.ActionsModel.FileSpecs.RATIO_4_3_VALUE
import org.avmedia.gShockPhoneSync.databinding.FragmentActionsBinding
import org.jetbrains.anko.contentView
import org.jetbrains.anko.layoutInflater
import timber.log.Timber
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

typealias LumaListener = (luma: Double) -> Unit

class CameraCapture(val context: Context, private val cameraSelector: CameraSelector) {
    private var cameraManager: CameraManager? = null
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var viewBinding: FragmentActionsBinding
    private val windowManager: WindowManager = (context as Activity).windowManager
    private val currentContextView = (context as Activity).contentView

    data class ScreenSize(val width: Int, val height: Int)

    fun start() {
        val currentContextView = (context as Activity).contentView
        viewBinding = FragmentActionsBinding.inflate(context.layoutInflater)
        context.setContentView(viewBinding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val rotation = viewBinding.viewFinder.display.rotation

            // Preview
            val preview = Preview.Builder()
                .setTargetRotation(rotation)
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            val screenSize = getScreenSize(context as Activity)
            val screenAspectRatio = aspectRatio(screenSize.width, screenSize.height)

            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setFlashMode(FLASH_MODE_AUTO)
                .setTargetRotation(rotation)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        Timber.d("Average luminosity: $luma")
                    })
                }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    (context as AppCompatActivity), cameraSelector, imageCapture, imageAnalyzer
                )

                takePhoto()

            } catch (exc: Exception) {
                Timber.d("Use case binding failed")
            } finally {
                stopCamera()
            }

        }, ContextCompat.getMainExecutor(context))
    }

    private fun stopCamera() {
        cameraExecutor.shutdown()

        // restore the context, so se can continue running normal
        (context as Activity).setContentView(currentContextView)
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun getScreenSize(activity: Activity): ScreenSize {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
            val insets: Insets = windowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            ScreenSize(windowMetrics.bounds.width(), windowMetrics.bounds.height())
        } else {
            val displayMetrics: DisplayMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            ScreenSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                // put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GShock-Pictures")
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                (context as Activity).contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Timber.d("Photo capture failed: ${exc.message}")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
                    val msg = "Find the picture in your [Photos] app"
                    if (currentContextView != null) {
                        Utils.snackBar(currentContextView, msg)
                    }
                    Timber.d(msg)
                }
            }
        )
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    }

    companion object {
        private const val TAG = "CameraCapture3"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}