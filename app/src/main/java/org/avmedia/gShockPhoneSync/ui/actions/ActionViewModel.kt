package org.avmedia.gShockPhoneSync.ui.actions

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.MainActivity.Companion.applicationContext
import org.avmedia.gShockPhoneSync.services.NotificationProvider
import org.avmedia.gShockPhoneSync.ui.actions.ActionsViewModel.CoroutineScopes.mainScope
import org.avmedia.gShockPhoneSync.ui.common.AppSnackbar
import org.avmedia.gShockPhoneSync.ui.events.EventsModel
import org.avmedia.gShockPhoneSync.utils.LocalDataStorage
import org.avmedia.gShockSmartSync.R
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber
import java.text.DateFormat
import java.time.Clock
import java.util.Date

class ActionsViewModel : ViewModel() {
    private val _actions = MutableStateFlow<ArrayList<Action>>(arrayListOf())
    val actions: StateFlow<List<Action>> = _actions

    private val actionMap = mutableMapOf<Class<out Action>, Action>()

    enum class RUN_MODE {
        SYNC, ASYNC,
    }

    private fun updateActionsAndMap(newActions: List<Action>) {
        val updatedActions = ArrayList<Action>()
        actionMap.clear()

        newActions.forEach { action ->
            updatedActions.add(action)
            actionMap[action::class.java] = action
        }
        _actions.value = updatedActions
    }

    fun <T : Action> updateAction(updatedAction: T) {
        val currentList = _actions.value
        val index = currentList.indexOfFirst { it::class == updatedAction::class }
        if (index != -1) {
            currentList[index] = updatedAction
            updateActionsAndMap(currentList)
            updatedAction.save(applicationContext())
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Action> getAction(type: Class<T>): T {
        return actionMap[type] as? T
            ?: throw IllegalStateException("Action of type ${type.simpleName} not found in actionMap.")
    }

    init {
        loadInitialActions()
        updateActionsAndMap(loadData(applicationContext()))
    }

    // Method to load the initial list of actions
    private fun loadInitialActions() {
        val initialActions = arrayListOf(
            ToggleFlashlightAction("Toggle Flashlight", false),
            StartVoiceAssistAction("Start Voice Assistant", false),
            NextTrack("Skip to next track", false),

            FindPhoneAction(applicationContext().getString(R.string.find_phone), true),
            SetTimeAction(applicationContext().getString(R.string.set_time), true),
            SetEventsAction(applicationContext().getString(R.string.set_reminders), false),
            PhotoAction(
                applicationContext().getString(R.string.take_photo),
                false,
                CAMERA_ORIENTATION.BACK
            ),
            PrayerAlarmsAction("Set Prayer Alarms", true),
            Separator(applicationContext().getString(R.string.emergency_actions), false),
            PhoneDialAction(applicationContext().getString(R.string.make_phonecall), false, ""),
        )

        _actions.value = initialActions
    }

    enum class RunEnvironment {
        NORMAL_CONNECTION,      // Connected by long-pressing the LOWER-LEFT button
        ACTION_BUTTON_PRESSED,  // Connected by short-pressing the LOWER-RIGHT button
        AUTO_TIME_ADJUSTMENT,   // Connected automatically during auto time update
        FIND_PHONE_PRESSED      // The user has activated the "Find Phone" function
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
                RunEnvironment.FIND_PHONE_PRESSED -> false
            }
        }

        abstract fun run(context: Context)

        open fun save(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            val value = enabled
            LocalDataStorage.put(context, key, value.toString())
        }

        open fun load(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            enabled = LocalDataStorage.get(context, key, "false").toBoolean()
            Timber.d("Load value: $key, $enabled")
        }

        open fun validate(context: Context): Boolean {
            return true
        }
    }

    data class SetEventsAction(
        override var title: String, override var enabled: Boolean
    ) :
        Action(title, enabled, RUN_MODE.ASYNC) {

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            return when (runEnvironment) {
                RunEnvironment.NORMAL_CONNECTION -> enabled && WatchInfo.hasReminders
                RunEnvironment.ACTION_BUTTON_PRESSED -> enabled && WatchInfo.hasReminders
                RunEnvironment.AUTO_TIME_ADJUSTMENT -> enabled && WatchInfo.hasReminders
                RunEnvironment.FIND_PHONE_PRESSED -> false
            }
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            EventsModel.refresh(context)
            api().setEvents(EventsModel.events)
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            enabled = LocalDataStorage.get(context, key, "false").toBoolean()
        }
    }

    data class ToggleFlashlightAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            FlashlightHelper.toggle(context)
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            enabled = LocalDataStorage.get(context, key, "false").toBoolean()
        }
    }

    data class FindPhoneAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            return when (runEnvironment) {
                RunEnvironment.NORMAL_CONNECTION -> false
                RunEnvironment.ACTION_BUTTON_PRESSED -> enabled && WatchInfo.findButtonUserDefined
                RunEnvironment.AUTO_TIME_ADJUSTMENT -> false
                RunEnvironment.FIND_PHONE_PRESSED -> true
            }
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            AppSnackbar("When found, lift phone to stop ringing")
            PhoneFinder.ring(context)
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            enabled =
                LocalDataStorage.get(
                    context,
                    key,
                    if (WatchInfo.findButtonUserDefined) "true" else "false"
                )
                    .toBoolean()
        }
    }

    data class SetTimeAction(
        override var title: String, override var enabled: Boolean
    ) :
        Action(
            title,
            enabled,
            RUN_MODE.ASYNC,
        ) {

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            return when (runEnvironment) {
                RunEnvironment.NORMAL_CONNECTION -> WatchInfo.alwaysConnected
                RunEnvironment.ACTION_BUTTON_PRESSED -> enabled
                RunEnvironment.AUTO_TIME_ADJUSTMENT -> true
                RunEnvironment.FIND_PHONE_PRESSED -> false
            }
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")

            val timeOffset = LocalDataStorage.getFineTimeAdjustment(context)
            val timeMs = System.currentTimeMillis() + timeOffset

            // actions are sun on the main lifecycle scope, because the Actions Fragment never gets created.
            mainScope.launch {
                api().setTime(timeMs = timeMs)
            }
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName + ".enabled"
            enabled =
                LocalDataStorage.get(
                    context,
                    key,
                    if (WatchInfo.findButtonUserDefined) "false" else "true"
                )
                    .toBoolean()
        }
    }

    data class SetLocationAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
        }
    }

    data class StartVoiceAssistAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled, RUN_MODE.ASYNC) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            try {
                val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                    // setPackage("com.google.android.googlequicksearchbox") // Set package for Google Assistant
                    flags =
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                AppSnackbar("Voice Assistant not available on this device!")
            }
        }
    }

    data class NextTrack(override var title: String, override var enabled: Boolean) :
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
                AppSnackbar("Cannot go to Next Track!")
            }
        }
    }

    data class PrayerAlarmsAction(
        override var title: String, override var enabled: Boolean
    ) :
        Action(title, enabled, RUN_MODE.ASYNC) {

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            return when (runEnvironment) {
                RunEnvironment.NORMAL_CONNECTION -> enabled
                RunEnvironment.ACTION_BUTTON_PRESSED -> enabled
                RunEnvironment.AUTO_TIME_ADJUSTMENT -> enabled
                RunEnvironment.FIND_PHONE_PRESSED -> false
            }
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            val alarms = PrayerAlarmsHelper.createPrayerAlarms(context)
            if (alarms == null) {
                Timber.e("Could not set prayer alarms")
                return
            }
            mainScope.launch {
                // getAlarms need to be run first, otherwise setAlarms() will not work
                api().getAlarms()
                api().setAlarms(alarms)
            }
        }
    }

    data class Separator(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
        }

        override fun load(context: Context) {
            // Do nothing.
        }
    }

    data class MapAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
        }
    }

    data class PhoneDialAction(
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
            LocalDataStorage.put(context, key, phoneNumber)
        }

        override fun load(context: Context) {
            super.load(context)
            val key = this.javaClass.simpleName + ".phoneNumber"
            phoneNumber = LocalDataStorage.get(context, key, "").toString()
        }

        override fun validate(context: Context): Boolean {
            if (phoneNumber.isEmpty()) {
                Timber.e("Phone number cannot be empty!")
                return false
            }

            return true
        }
    }

    enum class CAMERA_ORIENTATION {
        FRONT, BACK;
    }

    data class PhotoAction(
        override var title: String,
        override var enabled: Boolean,
        var cameraOrientation: CAMERA_ORIENTATION
    ) : Action(title, enabled, RUN_MODE.ASYNC) {
        init {
            Timber.d("PhotoAction: orientation: $cameraOrientation")
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")

            var captureResult: String?
            val cameraHelper = CameraCaptureHelper(context)

            // Initialize the camera
            cameraHelper.initCamera()

            // Launch a coroutine to take the picture
            mainScope.launch {
                cameraHelper.takePicture(
                    onImageCaptured = { result ->
                        captureResult = result
                        AppSnackbar("Image captured: $captureResult")
                        // Handle result, maybe pass it to the UI or save it
                    },
                    onError = { error ->
                        captureResult = "Error: $error"
                        AppSnackbar("Camera capture error: $captureResult")
                    }
                )
            }
        }

        override fun save(context: Context) {
            super.save(context)
            val key = this.javaClass.simpleName + ".cameraOrientation"
            LocalDataStorage.put(context, key, cameraOrientation.toString())
        }

        override fun load(context: Context) {
            super.load(context)
            val key = this.javaClass.simpleName + ".cameraOrientation"
            cameraOrientation = if (LocalDataStorage.get(context, key, "BACK")
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
            AppSnackbar("EmailLocationAction: emailAddress: $emailAddress")
            Timber.d("EmailLocationAction: extraText: $extraText")
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
        }

        override fun save(context: Context) {
            val key = this.javaClass.simpleName + ".emailAddress"
            LocalDataStorage.put(context, key, emailAddress)
            super.save(context)
        }

        override fun load(context: Context) {
            super.load(context)

            val key = this.javaClass.simpleName + ".emailAddress"
            emailAddress = LocalDataStorage.get(context, key, "").toString()
            extraText =
                "Sent by G-shock App:\n https://play.google.com/store/apps/details?id=org.avmedia.gshockGoogleSync"
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
            AppSnackbar("You have not given permission to to run action ${action.title}.")
        } catch (e: Exception) {
            AppSnackbar("Could not run action ${action.title}. Reason: $e")
        }
    }

    fun runActionsForActionButton(context: Context) {
        updateActionsAndMap(loadData(context))

        runFilteredActions(
            context,
            _actions.value.filter { it.shouldRun(RunEnvironment.ACTION_BUTTON_PRESSED) })
    }

    fun runActionForConnection(context: Context) {
        updateActionsAndMap(loadData(context))

        runFilteredActions(context, _actions.value.filter {
            it.shouldRun(RunEnvironment.NORMAL_CONNECTION)
        })
    }

    fun runActionsForAutoTimeSetting(context: Context) {
        updateActionsAndMap(loadData(context))

        runFilteredActions(
            context,
            _actions.value.filter { it.shouldRun(RunEnvironment.AUTO_TIME_ADJUSTMENT) })

        // show notification if configured
        if (LocalDataStorage.getTimeAdjustmentNotification(context)
            && !WatchInfo.alwaysConnected
        ) { // only create notification for not-always connected watches.
            showTimeSyncNotification(context)
        }
    }

    fun runActionFindPhone(context: Context) {
        runFilteredActions(context, _actions.value.filter {
            it.shouldRun(RunEnvironment.FIND_PHONE_PRESSED)
        })
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
                    Timber.d("------------> running ${it.javaClass.simpleName}")
                    // actions are run on the main lifecycle scope, because the Actions Fragment never gets created.
                    mainScope.launch {
                        runIt(it, context)
                    }
                } else {
                    runIt(it, context)
                }
            }
    }

    private fun loadData(context: Context): List<Action> {
        _actions.value.forEach {
            it.load(context)
        }
        return _actions.value
    }

    fun saveData(context: Context) {
        _actions.value.forEach {
            it.save(context)
        }
    }

    object CoroutineScopes {
        val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }
}
