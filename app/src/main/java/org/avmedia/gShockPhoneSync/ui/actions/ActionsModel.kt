/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-20, 7:47 p.m.
 */

package org.avmedia.gShockPhoneSync.ui.actions

import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import androidx.camera.core.CameraSelector
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext
import org.avmedia.gShockPhoneSync.R
import org.avmedia.gShockPhoneSync.services.NotificationProvider
import org.avmedia.gShockPhoneSync.ui.events.EventsModel
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gShockPhoneSync.utils.Utils
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber
import java.text.DateFormat
import java.time.Clock
import java.util.Date

@Suppress(
    "ClassName",
)
object ActionsModel {

    private val actions = ArrayList<Action>()

    enum class RUN_MODE {
        SYNC, ASYNC,
    }

    init {
        // actions.add(MapAction("Map", false))
        // actions.add(SetLocationAction("Save location to G-maps", false))

        val findPhoneText = applicationContext().getString(R.string.find_phone)
        actions.add(FindPhoneAction(findPhoneText, true))

        val setTimeText = applicationContext().getString(R.string.set_time)
        val timeAction = SetTimeAction(setTimeText, true)

        actions.add(timeAction)

        val setReminderText = applicationContext().getString(R.string.set_reminders)
        actions.add(SetEventsAction(setReminderText, false))

        val takePhotoText = applicationContext().getString(R.string.take_photo)
        actions.add(PhotoAction(takePhotoText, false, CAMERA_ORIENTATION.BACK))

        val toggleFlashlightText = applicationContext().getString(R.string.toggle_flashlight)
        actions.add(ToggleFlashlightAction(toggleFlashlightText, false))

        val voiceAssistantText = applicationContext().getString(R.string.start_voice_assistant)
        actions.add(StartVoiceAssistAction(voiceAssistantText, false))

        val nextTrackText = "Skip to next track"
        actions.add(NextTrack(nextTrackText, false))

        val prayerAlarmsText = "Set Prayer Alarms"
        actions.add(PrayerAlarmsAction(prayerAlarmsText, false))

        val emergencyActionsText = applicationContext().getString(R.string.emergency_actions)
        actions.add(Separator(emergencyActionsText, false))

        val makePhoneCallText = applicationContext().getString(R.string.make_phonecall)
        actions.add(PhoneDialAction(makePhoneCallText, false, ""))
        // actions.add(EmailLocationAction("Send my location by email", true, "", "Come get me"))
    }

    fun getActions(): ArrayList<Action> {
        return filter(actions)
    }

    private fun filter(actions: ArrayList<Action>): ArrayList<Action> {
        return actions.filter { action ->
            when (action) {
                is FindPhoneAction -> WatchInfo.findButtonUserDefined
                is SetEventsAction -> WatchInfo.hasReminders
                else -> true
            }
        } as ArrayList<Action>
    }

    enum class RunEnvironment {
        NORMAL_CONNECTION,
        ACTION_BUTTON_PRESSED,
        AUTO_TIME_ADJUSTMENT
    }

    abstract class Action(
        open var title: String,
        open var enabled: Boolean,
        var runMode: RUN_MODE = RUN_MODE.SYNC,
    ) {
        open fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            return when (runEnvironment) {
                RunEnvironment.ACTION_BUTTON_PRESSED -> enabled
                RunEnvironment.NORMAL_CONNECTION -> false
                RunEnvironment.AUTO_TIME_ADJUSTMENT -> false
            }
        }

        abstract fun run(context: Context)

        open fun save(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            val value = enabled
            LocalDataStorage.put(key, value.toString())
        }

        open fun load(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            enabled = LocalDataStorage.get(key, "false").toBoolean()
            Timber.d("Load value: $key, $enabled")
        }

        open fun validate(context: Context): Boolean {
            return true
        }
    }

    class SetEventsAction(
        override var title: String, override var enabled: Boolean
    ) :
        Action(title, enabled, RUN_MODE.ASYNC) {

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            return when (runEnvironment) {
                RunEnvironment.NORMAL_CONNECTION -> enabled && WatchInfo.hasReminders
                RunEnvironment.ACTION_BUTTON_PRESSED -> enabled && WatchInfo.hasReminders
                RunEnvironment.AUTO_TIME_ADJUSTMENT -> enabled && WatchInfo.hasReminders
            }
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            EventsModel.refresh(context)
            api().setEvents(EventsModel.events)
            // Utils.snackBar(context, "Events Sent to Watch")
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            enabled = LocalDataStorage.get(key, "false").toBoolean()
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
            enabled = LocalDataStorage.get(key, "false").toBoolean()
        }
    }

    class FindPhoneAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            PhoneFinder.ring(context)
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            enabled =
                LocalDataStorage.get(key, if (WatchInfo.findButtonUserDefined) "true" else "false")
                    .toBoolean()
        }
    }

    class SetTimeAction(
        override var title: String, override var enabled: Boolean
    ) :
        Action(
            title,
            enabled,
            RUN_MODE.ASYNC,
        ) { // only set time when connecting for alwaysConnected watches.

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            return when (runEnvironment) {
                RunEnvironment.NORMAL_CONNECTION -> WatchInfo.alwaysConnected
                RunEnvironment.ACTION_BUTTON_PRESSED -> enabled
                RunEnvironment.AUTO_TIME_ADJUSTMENT -> true
            }
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")

            // actions are sun on the main lifecycle scope, because the Actions Fragment never gets created.
            MainActivity.getLifecycleScope().launch {
                api().setTime()
            }
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            enabled =
                LocalDataStorage.get(key, if (WatchInfo.findButtonUserDefined) "false" else "true")
                    .toBoolean()
        }
    }

    class SetLocationAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
        }
    }

    class StartVoiceAssistAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled, RUN_MODE.ASYNC) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            try {
                context.startActivity(Intent(Intent.ACTION_VOICE_COMMAND).setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            } catch (e: ActivityNotFoundException) {
                Utils.snackBar(context, "Voice Assistant not available on this device!")
            }
        }
    }

    class NextTrack(override var title: String, override var enabled: Boolean) :
        Action(title, enabled, RUN_MODE.ASYNC) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val eventTime = SystemClock.uptimeMillis()

                val downEvent = KeyEvent(
                    eventTime,
                    eventTime,
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    0
                )
                audioManager.dispatchMediaKeyEvent(downEvent)

                val upEvent = KeyEvent(
                    eventTime,
                    eventTime,
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    0
                )
                audioManager.dispatchMediaKeyEvent(upEvent)

            } catch (e: ActivityNotFoundException) {
                Utils.snackBar(context, "Cannot go to Next Track!")
            }
        }
    }

    class PrayerAlarmsAction(
        override var title: String, override var enabled: Boolean
    ) :
        Action(title, enabled, RUN_MODE.ASYNC) {

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            return when (runEnvironment) {
                RunEnvironment.NORMAL_CONNECTION -> enabled
                RunEnvironment.ACTION_BUTTON_PRESSED -> enabled
                RunEnvironment.AUTO_TIME_ADJUSTMENT -> enabled
            }
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            val alarms = PrayerAlarms.createPrayerAlarms(context)
            if (alarms == null) {
                Utils.snackBar(context, "Could not set prayer alarms")
                return
            }
            MainActivity.getLifecycleScope().launch {
                // getAlarms need to be run first, otherwise setAlarms() will not work
                api().getAlarms()
                api().setAlarms(alarms)
                // Utils.snackBar(context, "Set Prayer Alarms")
            }
        }
    }

    class Separator(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
        }

        override fun load(context: Context) {
            // Do nothing.
        }
    }

    class MapAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
        }
    }

    class PhoneDialAction(
        override var title: String, override var enabled: Boolean, var phoneNumber: String
    ) : Action(title, enabled) {
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
            LocalDataStorage.put(key, phoneNumber)
        }

        override fun load(context: Context) {
            super.load(context)
            val key = this.javaClass.simpleName + ".phoneNumber"
            phoneNumber = LocalDataStorage.get(key, "").toString()
        }

        override fun validate(context: Context): Boolean {
            if (phoneNumber.isEmpty()) {
                Utils.snackBar(context, "Phone number cannot be empty!")
                return false
            }

            return true
        }
    }

    enum class CAMERA_ORIENTATION {
        FRONT, BACK;
    }

    class PhotoAction(
        override var title: String,
        override var enabled: Boolean,
        var cameraOrientation: CAMERA_ORIENTATION
    ) : Action(title, enabled, RUN_MODE.ASYNC) {
        init {
            Timber.d("PhotoAction: orientation: $cameraOrientation")
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            (context as Activity).runOnUiThread {
                val cameraSelector: CameraSelector =
                    if (cameraOrientation == CAMERA_ORIENTATION.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val capture = CameraCapture(context, cameraSelector)
                capture.start()
                Timber.d("Photo taken...")
            }
        }

        override fun save(context: Context) {
            super.save(context)
            val key = this.javaClass.simpleName + ".cameraOrientation"
            LocalDataStorage.put(key, cameraOrientation.toString())
        }

        override fun load(context: Context) {
            super.load(context)
            val key = this.javaClass.simpleName + ".cameraOrientation"
            cameraOrientation = if (LocalDataStorage.get(key, "BACK")
                    .toString() == "BACK"
            ) CAMERA_ORIENTATION.BACK else CAMERA_ORIENTATION.FRONT
        }
    }

    class EmailLocationAction(
        override var title: String,
        override var enabled: Boolean,
        var emailAddress: String,
        private var extraText: String
    ) : Action(title, enabled) {
        init {
            Timber.d("EmailLocationAction: emailAddress: $emailAddress")
            Timber.d("EmailLocationAction: extraText: $extraText")
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
        }

        override fun save(context: Context) {
            val key = this.javaClass.simpleName + ".emailAddress"
            LocalDataStorage.put(key, emailAddress)
            super.save(context)
        }

        override fun load(context: Context) {
            super.load(context)

            val key = this.javaClass.simpleName + ".emailAddress"
            emailAddress = LocalDataStorage.get(key, "").toString()
            extraText =
                "Sent by G-shock App:\n https://play.google.com/store/apps/details?id=org.avmedia.gshockGoogleSync"
        }
    }

    object FileSpecs {

        const val RATIO_4_3_VALUE = 4.0 / 3.0
        const val RATIO_16_9_VALUE = 16.0 / 9.0
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
                context, "You have not given permission to to run action ${action.title}."
            )
        } catch (e: Exception) {
            Utils.snackBar(context, "Could not run action ${action.title}. Reason: $e")
        }
    }

    fun runActionsForActionButton(context: Context) {
        runFilteredActions(
            context,
            actions.filter { it.shouldRun(RunEnvironment.ACTION_BUTTON_PRESSED) })
    }

    fun runActionForConnection(context: Context) {
        runFilteredActions(context, actions.filter {
            Timber.i("===========> ${it.title}, ${it.enabled}")
            it.shouldRun(RunEnvironment.NORMAL_CONNECTION)
        })
    }

    fun runActionsForAutoTimeSetting(context: Context) {
        runFilteredActions(
            context,
            actions.filter { it.shouldRun(RunEnvironment.AUTO_TIME_ADJUSTMENT) })

        // show notification if configured
        if (LocalDataStorage.getTimeAdjustmentNotification()
            && !WatchInfo.alwaysConnected
        ) { // only create notification for not-always connected watches.
            showTimeSyncNotification(context)
        }
    }

    fun runActionFindPhone(context: Context) {
        runFilteredActions(context, listOf(FindPhoneAction("Find Phone", true)))
    }

    private fun showTimeSyncNotification(context: Context) {
        val dateStr =
            DateFormat.getDateTimeInstance().format(Date(Clock.systemDefaultZone().millis()))

        var msg = "Time set at $dateStr"
        val watchName = WatchInfo.name
        msg += " for $watchName watch"

        NotificationProvider.createNotification(
            context,
            "G-Shock Smart Sync",
            msg,
            NotificationManager.IMPORTANCE_DEFAULT
        )
    }

    private fun runFilteredActions(context: Context, filteredActions: List<Action>) {
        filteredActions.sortedWith(compareBy { it.runMode.ordinal }) // run SYNC actions first
            .forEach {
                if (it.runMode == RUN_MODE.ASYNC) {
                    Timber.d("running ${it.javaClass.simpleName}")
                    // actions are run on the main lifecycle scope, because the Actions Fragment never gets created.
                    MainActivity.getLifecycleScope().launch {
                        runIt(it, context)
                    }
                } else {
                    runIt(it, context)
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
}