/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.actions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import com.google.gson.Gson
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.ble.Connection.sendMessage
import org.avmedia.gShockPhoneSync.casio.CasioTimeZone
import org.avmedia.gShockPhoneSync.casio.WatchFactory
import org.avmedia.gShockPhoneSync.ui.events.EventsModel
import org.avmedia.gShockPhoneSync.utils.*
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.time.Clock
import java.util.*

object ActionsModel {

    val actions = ArrayList<Action>()

    enum class RUN_MODE(value: Int) {
        SYNC(0), ASYNC(1),
    }

    init {
        // actions.add(MapAction("Map", false))
        // actions.add(SetLocationAction("Save location to G-maps", false))
        actions.add(SetTimeAction("Set Time", true))
        actions.add(SetEventsAction("Set Reminders from Google Calender", false))
        actions.add(PhotoAction("Take a photo", false, CAMERA_ORIENTATION.BACK))
        actions.add(ToggleFlashlightAction("Toggle Flashlight", false))
        actions.add(StartVoiceAssistAction("Start Voice Assist", true))

        actions.add(Separator("Emergency Actions:", false))

        actions.add(PhoneDialAction("Make a phone call", true, ""))
        // actions.add(EmailLocationAction("Send my location by email", true, "", "Come get me"))
    }

    abstract class Action(
        open var title: String,
        open var enabled: Boolean,
        var isEmergency: Boolean = false,
        var runMode: RUN_MODE = RUN_MODE.SYNC
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

    class SetEventsAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            sendMessage("{action: \"SET_REMINDERS\", value: ${EventsModel.getSelectedEvents()}}")
            Utils.snackBar(context, "Events Sent to Watch")
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            enabled = LocalDataStorage.get(key, "false", context).toBoolean()
        }
    }

    class ToggleFlashlightAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            Flashlight.toggle(context)
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            enabled = LocalDataStorage.get(key, "false", context).toBoolean()
        }
    }

    class SetTimeAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            createAppEventsSubscription(context)
        }

        private fun createAppEventsSubscription(context: Context) {
            ProgressEvents.subscriber.start(
                this.javaClass.simpleName,

                {
                    when (it) {
                        // For setting time, we need to wait until the watch has been initialised.
                        ProgressEvents.Events.WatchInitializationCompleted -> {
                            if (!Utils.isDebugMode()) {

                                // Update the HomeTime according to the current TimeZone
                                // This could be optimised to be called only if the
                                // timezone has changed, but this adds complexity.
                                // Maybe we can do this in the future.
                                CasioTimeZone.setHomeTime(TimeZone.getDefault().id)

                                sendMessage(
                                    "{ action: \"SET_TIME\", value: ${
                                        Clock.systemDefaultZone().millis()
                                    }}"
                                )
                            }
                        }
                    }
                },
                { throwable ->
                    Timber.d("Got error on subscribe: $throwable")
                    throwable.printStackTrace()
                })
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
                context.startActivity(Intent(Intent.ACTION_VOICE_COMMAND).setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
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

            val dialIntent =
                Intent(Intent.ACTION_CALL).setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
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

    /*
    Note: Alternatively, actions can run autonomously, when certain conditions were met:
    1. User pressed Action button (lower-right) on the watch
    2. The action is enabled
    3. Certain progress event received.

    However, this way gives us more control on how to start the actions.
     */

    private fun runIt(action: Action, context: Context) {
        try {
            action.run(context)
        } catch (e: SecurityException) {
            Utils.snackBar(
                context,
                "You have not given permission to to run action ${action.title}."
            )
        } catch (e: Exception) {
            Utils.snackBar(context, "Could not run action ${action.title}. Reason: $e")
        }
    }

    fun runActions(context: Context) {

        // Here we select which actions to run.
        // If we got here from auto-time set, just run the Time action and the Calender action
        // If we got here by pressing the lower-right (actions) button, run all enabled actions.

        var actionsToRum = if (WatchFactory.watch.isAutoTimeStarted()) {
            // if we are auto-setting time, just run the TimeSetAction and SetEventsAction
            val autoActions = ArrayList<Action>()
            autoActions.add(SetTimeAction("Set Time", true))
            autoActions.add(SetEventsAction("Set Calender", true))

            // INZ just for test, remove later
            autoActions.add(PhotoAction("Take a picture", true, CAMERA_ORIENTATION.BACK))

            autoActions
        } else {
            // Use all enabled actions.
            // Sort by async mode first.
            actions.sortedWith(compareBy { it.runMode.ordinal })
        }

        actionsToRum
            .forEach {
                if (it.enabled) {
                    // Run in background for speed
                    if (it.runMode == RUN_MODE.ASYNC) {
                        GlobalScope.launch {
                            runIt(it, context)
                        }
                    } else {
                        Log.i("", "Running $it")
                        runIt(it, context)
                    }
                }
            }
    }

    fun loadData(context: Context) {
        actions.forEach {
            it.load(context)
        }
    }

    fun saveData(context: Context) {
        actions.forEach {
            it.save(context)
        }
    }

    fun hasTimeSet(): Boolean {
        if (WatchFactory.watch.isAutoTimeStarted()) {
            return true
        }

        actions.forEach {
            if (it.enabled && it is SetTimeAction) {
                return true
            }
        }
        return false
    }

    fun runActionsForAutoTimeSetting(context: Context) {
        runIt(SetTimeAction("Set Time", true), context)
        runIt(SetEventsAction("Set Reminders from Google Calender", false), context)
    }
}