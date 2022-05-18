/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.camera.core.CameraSelector
import com.google.gson.Gson
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.ble.Connection.sendMessage
import org.avmedia.gShockPhoneSync.utils.CameraCapture
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gShockPhoneSync.utils.Utils
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.time.Clock
import java.util.*

object ActionsModel {

    abstract class Action(
        open var title: String,
        open var enabled: Boolean,
        var isEmergency: Boolean = false
    ) {
        abstract fun run(context: Context)

        open fun save(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            val value = enabled
            LocalDataStorage.put(key, value.toString(), context)
        }

        open fun load(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            enabled = LocalDataStorage.get(key, "false", context).toBoolean()
        }

        open fun validate(context: Context): Boolean {
            return true
        }
    }

    class SetTimeAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")

            if (!Utils.isDebugMode()) {
                sendMessage(
                    "{ action: \"SET_TIME\", value: ${
                        Clock.systemDefaultZone().millis()
                    } }"
                )
            }
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            enabled = LocalDataStorage.get(key, "true", context).toBoolean()
        }
    }

    class SetLocationAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
        }
    }

    class StartVoiceAssistAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            try {
                context.startActivity(Intent(Intent.ACTION_VOICE_COMMAND).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                Utils.snackBar(context, "Voice Assistant not available on this device!")
            }
        }
    }

    class Separator(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
        }

        override fun load(context: Context) {
            super.save(context)
        }
    }

    class MapAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
        }
    }

    class PhoneDialAction(
        override var title: String,
        override var enabled: Boolean,
        var phoneNumber: String
    ) : Action(title, enabled, true) {
        init {
            Timber.d("PhoneDialAction")
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")

            val dialIntent = Intent(Intent.ACTION_CALL)
            dialIntent.data = Uri.parse("tel:$phoneNumber")
            context.startActivity(dialIntent)
        }

        override fun save(context: Context) {
            super.save(context)
            val key = this.javaClass.simpleName + ".phoneNumber"
            LocalDataStorage.put(key, phoneNumber.toString(), context)
        }

        override fun load(context: Context) {
            super.load(context)
            val key = this.javaClass.simpleName + ".phoneNumber"
            phoneNumber = LocalDataStorage.get(key, "", context).toString()
        }

        override fun validate(context: Context): Boolean {
            if (phoneNumber.isEmpty()) {
                Utils.snackBar(context, "Phone number cannot be empty!")
                return false
            }

            return true
        }
    }

    enum class CAMERA_ORIENTATION(cameraOrientation: String) {
        FRONT("FRONT"), BACK("BACK");
    }

    class PhotoAction(
        override var title: String,
        override var enabled: Boolean,
        var cameraOrientation: CAMERA_ORIENTATION
    ) : Action(title, enabled) {
        init {
            Timber.d("PhotoAction: orientation: $cameraOrientation")
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")

            (context as Activity).runOnUiThread {
                val cameraSelector: CameraSelector =
                    if (cameraOrientation == CAMERA_ORIENTATION.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                CameraCapture(context, cameraSelector).start()
                Timber.d("Photo taken...")
            }
        }

        override fun save(context: Context) {
            super.save(context)
            val key = this.javaClass.simpleName + ".cameraOrientation"
            LocalDataStorage.put(key, cameraOrientation.toString(), context)
        }

        override fun load(context: Context) {
            super.load(context)
            val key = this.javaClass.simpleName + ".cameraOrientation"
            cameraOrientation = if (LocalDataStorage.get(key, "BACK", context)
                    .toString() == "BACK"
            ) CAMERA_ORIENTATION.BACK else CAMERA_ORIENTATION.FRONT
        }
    }

    class EmailLocationAction(
        override var title: String,
        override var enabled: Boolean,
        var emailAddress: String,
        var extraText: String
    ) : Action(title, enabled, true) {
        init {
            Timber.d("EmailLocationAction: emailAddress: $emailAddress")
            Timber.d("EmailLocationAction: extraText: $extraText")
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
        }

        override fun save(context: Context) {
            val key = this.javaClass.simpleName + ".emailAddress"
            LocalDataStorage.put(key, emailAddress.toString(), context)
            super.save(context)
        }

        override fun load(context: Context) {
            super.load(context)

            val key = this.javaClass.simpleName + ".emailAddress"
            emailAddress = LocalDataStorage.get(key, "", context).toString()
            extraText =
                "Sent by G-shock App:\n https://play.google.com/store/apps/details?id=org.avmedia.gshockGoogleSync"
        }
    }

    val actions = ArrayList<ActionsModel.Action>()

    init {
        // actions.add(MapAction("Map", false))
        // actions.add(SetLocationAction("Save location to G-maps", false))
        actions.add(SetTimeAction("Set Time", true))
        actions.add(PhotoAction("Take a photo", false, CAMERA_ORIENTATION.BACK))
        actions.add(StartVoiceAssistAction("Start Voice Assist", true))

        actions.add(Separator("Emergency Actions:", false))

        actions.add(PhoneDialAction("Make a phone call", true, ""))
        // actions.add(EmailLocationAction("Send my location by email", true, "", "Come get me"))
    }

    fun clear() {
        actions.clear()
    }

    fun isEmpty(): Boolean {
        return actions.size == 0
    }

    @Synchronized
    fun fromJson(jsonStr: String) {
        val gson = Gson()
        val actionStr = gson.fromJson(jsonStr, Array<Action>::class.java)
        actions.addAll(actionStr)
    }

    @Synchronized
    fun toJson(): String {
        val gson = Gson()
        return gson.toJson(actions)
    }

    object FileSpecs {

        const val TAG = "CasioGShockSync"
        const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        const val PHOTO_EXTENSION = ".jpg"
        const val RATIO_4_3_VALUE = 4.0 / 3.0
        const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        fun createFile(baseFolder: File, format: String, extension: String) =
            File(
                baseFolder, SimpleDateFormat(format, Locale.US)
                    .format(System.currentTimeMillis()) + extension
            )

        fun getOutputDirectory(context: Context): File {
            val appContext = context.applicationContext

            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() }
            }

            return if (mediaDir != null && mediaDir.exists())
                mediaDir else appContext.filesDir
        }
    }
}