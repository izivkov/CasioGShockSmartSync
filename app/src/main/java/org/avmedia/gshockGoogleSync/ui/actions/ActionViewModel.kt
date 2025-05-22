package org.avmedia.gshockGoogleSync.ui.actions

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.services.NotificationProvider
import org.avmedia.gshockGoogleSync.ui.actions.ActionsViewModel.CoroutineScopes.mainScope
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.events.CalendarEvents
import org.avmedia.gshockGoogleSync.ui.events.EventsModel
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber
import java.text.DateFormat
import java.time.Clock
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val api: GShockRepository,
    val translateApi: TranslateRepository,
    @ApplicationContext private val appContext: Context, // Inject application context
    private val calendarEvents: CalendarEvents
) : ViewModel() {
    private val _actions = MutableStateFlow<ArrayList<Action>>(arrayListOf())
    val actions: StateFlow<List<Action>> = _actions

    @Inject
    lateinit var notificationProvider: NotificationProvider

    private val actionMap = mutableMapOf<Class<out Action>, Action>()

    enum class RunMode {
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
            updatedAction.save(appContext)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Action> getAction(type: Class<T>): T {
        return actionMap[type] as? T
            ?: throw IllegalStateException("Action of type ${type.simpleName} not found in actionMap.")
    }

    init {
        loadInitialActions()
        updateActionsAndMap(loadData(appContext))
    }

    // Method to load the initial list of actions
    private fun loadInitialActions() {
        val initialActions = arrayListOf(
            ToggleFlashlightAction("Toggle Flashlight", false),
            StartVoiceAssistAction("Start Voice Assistant", false, translateApi),
            NextTrack("Skip to next track", false, translateApi),

            FindPhoneAction(
                translateApi.getString(appContext, R.string.find_phone),
                translateApi,
                true
            ),
            SetTimeAction(translateApi.getString(appContext, R.string.set_time), true, api),
            SetEventsAction(
                translateApi.getString(appContext, R.string.set_reminders),
                false,
                api,
                translateApi,
                calendarEvents
            ),
            PhotoAction(
                translateApi.getString(appContext, R.string.take_photo),
                false,
                CameraOrientation.BACK,
                translateApi
            ),
            PrayerAlarmsAction("Set Prayer Alarms", true, api),
            Separator(translateApi.getString(appContext, R.string.emergency_actions), false),
            PhoneDialAction(translateApi.getString(appContext, R.string.make_phonecall), false, ""),
        )

        _actions.value = initialActions
    }

    enum class RunEnvironment {
        NORMAL_CONNECTION,      // Connected by long-pressing the LOWER-LEFT button
        ACTION_BUTTON_PRESSED,  // Connected by short-pressing the LOWER-RIGHT button
        AUTO_TIME_ADJUSTMENT,   // Connected automatically during auto time update
        FIND_PHONE_PRESSED,     // The user has activated the "Find Phone" function
        ALWAYS_CONNECTED,       // Some watches are always connected, but the watch keeps connecting and disconnecting periodically.
    }

    abstract class Action(
        open var title: String,
        open var enabled: Boolean,
        var runMode: RunMode = RunMode.SYNC,
    ) {
        val ENABLED: String = ".enabled"

        open fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            return when (runEnvironment) {
                RunEnvironment.ACTION_BUTTON_PRESSED -> enabled
                RunEnvironment.NORMAL_CONNECTION -> false
                RunEnvironment.AUTO_TIME_ADJUSTMENT -> false
                RunEnvironment.FIND_PHONE_PRESSED -> false
                RunEnvironment.ALWAYS_CONNECTED -> false
            }
        }

        abstract fun run(context: Context)

        open fun save(context: Context) {
            val key = this.javaClass.simpleName + ENABLED
            val value = enabled
            LocalDataStorage.put(context, key, value.toString())
        }

        open fun load(context: Context) {
            val key = this.javaClass.simpleName + ENABLED
            enabled = LocalDataStorage.get(context, key, "false").toBoolean()
            Timber.d("Load value: $key, $enabled")
        }

        open fun validate(context: Context): Boolean {
            return true
        }
    }

    data class SetEventsAction(
        override var title: String,
        override var enabled: Boolean,
        val api: GShockRepository,
        val translateApi: TranslateRepository,
        val calendarEvents: CalendarEvents
    ) :
        Action(title, enabled, RunMode.ASYNC) {

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            return when (runEnvironment) {
                RunEnvironment.NORMAL_CONNECTION -> enabled && WatchInfo.hasReminders
                RunEnvironment.ACTION_BUTTON_PRESSED -> enabled && WatchInfo.hasReminders
                RunEnvironment.AUTO_TIME_ADJUSTMENT -> enabled && WatchInfo.hasReminders
                RunEnvironment.FIND_PHONE_PRESSED -> false
                RunEnvironment.ALWAYS_CONNECTED -> false
            }
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            EventsModel.refresh(calendarEvents.getEventsFromCalendar())
            api.setEvents(EventsModel.events)
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName + ENABLED
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
            val key = this.javaClass.simpleName + ENABLED
            enabled = LocalDataStorage.get(context, key, "false").toBoolean()
        }
    }

    data class FindPhoneAction(
        override var title: String,
        val translateApi: TranslateRepository,
        override var enabled: Boolean
    ) :
        Action(title, enabled) {

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            return when (runEnvironment) {
                RunEnvironment.NORMAL_CONNECTION -> false
                RunEnvironment.ACTION_BUTTON_PRESSED -> enabled && WatchInfo.findButtonUserDefined
                RunEnvironment.AUTO_TIME_ADJUSTMENT -> false
                RunEnvironment.FIND_PHONE_PRESSED -> true
                RunEnvironment.ALWAYS_CONNECTED -> false
            }
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            PhoneFinder.ring(context)
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName + ENABLED
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
        override var title: String, override var enabled: Boolean, val api: GShockRepository
    ) :
        Action(
            title,
            enabled,
            RunMode.ASYNC,
        ) {

        private var lastSet: Long? = null

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            // update every hour
            val setTimeConditionAlwaysConnected =
                (WatchInfo.alwaysConnected && (lastSet == null || System.currentTimeMillis() - lastSet!! > 1000 * 60 * 60))

            return when (runEnvironment) {
                RunEnvironment.NORMAL_CONNECTION -> false
                RunEnvironment.ACTION_BUTTON_PRESSED -> enabled
                RunEnvironment.AUTO_TIME_ADJUSTMENT -> true
                RunEnvironment.FIND_PHONE_PRESSED -> false
                RunEnvironment.ALWAYS_CONNECTED -> setTimeConditionAlwaysConnected
            }
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            val timeOffset = LocalDataStorage.getFineTimeAdjustment(context)
            val timeMs = System.currentTimeMillis() + timeOffset

            // actions are sun on the main lifecycle scope, because the Actions Fragment never gets created.
            mainScope.launch {
                api.setTime(timeMs = timeMs)
                lastSet = timeMs
            }
        }

        override fun load(context: Context) {
            val key = this.javaClass.simpleName + ENABLED
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

    data class StartVoiceAssistAction(
        override var title: String,
        override var enabled: Boolean,
        val translateApi: TranslateRepository,
    ) :
        Action(title, enabled, RunMode.ASYNC) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            runCatching {
                val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                context.startActivity(intent)
            }.onFailure {
                if (it is ActivityNotFoundException) {
                    AppSnackbar(
                        translateApi.getString(
                            context,
                            R.string.voice_assistant_not_available_on_this_device
                        )
                    )
                }
            }
        }
    }

    data class NextTrack(
        override var title: String,
        override var enabled: Boolean,
        val translateApi: TranslateRepository,
    ) :
        Action(title, enabled, RunMode.ASYNC) {
        override fun run(context: Context) {
            Timber.d("running \${this.javaClass.simpleName}")
            runCatching {
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
            }.onFailure {
                if (it is ActivityNotFoundException) {
                    AppSnackbar(translateApi.getString(context, R.string.cannot_go_to_next_track))
                }
            }
        }
    }

    data class PrayerAlarmsAction(
        override var title: String, override var enabled: Boolean, val api: GShockRepository
    ) :
        Action(title, enabled, RunMode.ASYNC) {

        private var lastSet: Long? = null
        override fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            // update every 6 hours
            val setTimeConditionAlwaysConnected =
                (WatchInfo.alwaysConnected && (lastSet == null || System.currentTimeMillis() - lastSet!! > 6000 * 60 * 60))

            return when (runEnvironment) {
                RunEnvironment.NORMAL_CONNECTION -> enabled
                RunEnvironment.ACTION_BUTTON_PRESSED -> enabled
                RunEnvironment.AUTO_TIME_ADJUSTMENT -> enabled
                RunEnvironment.FIND_PHONE_PRESSED -> false
                RunEnvironment.ALWAYS_CONNECTED -> setTimeConditionAlwaysConnected
            }
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            val alarms = PrayerAlarmsHelper.createNextPrayerAlarms(context, WatchInfo.alarmCount)
            if (alarms == null) {
                Timber.e("Could not set prayer alarms")
                return
            }
            mainScope.launch {
                // getAlarms need to be run first, otherwise setAlarms() will not work
                api.getAlarms()
                api.setAlarms(alarms)
                lastSet = System.currentTimeMillis()
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
            dialIntent.data = "tel:$phoneNumber".toUri()
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

    enum class CameraOrientation {
        FRONT, BACK;
    }

    data class PhotoAction(
        override var title: String,
        override var enabled: Boolean,
        var cameraOrientation: CameraOrientation,
        val translateApi: TranslateRepository,
    ) : Action(title, enabled, RunMode.ASYNC) {
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
                        AppSnackbar(
                            translateApi.getString(
                                context,
                                R.string.image_captured,
                                captureResult!!
                            )
                        )
                        // Handle result, maybe pass it to the UI or save it
                    },
                    onError = { error ->
                        captureResult = "Error: $error"
                        AppSnackbar(
                            translateApi.getString(
                                context,
                                R.string.camera_capture_error,
                                captureResult!!
                            )
                        )
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
            ) CameraOrientation.BACK else CameraOrientation.FRONT
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
        runCatching {
            action.run(context)
        }.onFailure {
            when (it) {
                is SecurityException -> AppSnackbar(
                    context.getString(
                        R.string.you_have_not_given_permission_to_to_run_action,
                        action.title
                    )
                )

                else -> AppSnackbar("Could not run action \${action.title}. Reason: \$it")
            }
        }
    }

    fun runActionsForActionButton(context: Context) {
        updateActionsAndMap(loadData(context))

        val actions = _actions.value.filter { it.shouldRun(RunEnvironment.ACTION_BUTTON_PRESSED) }
        ProgressEvents.onNext("ActionNames", actions.map { it.title })
        runFilteredActions(context, actions)
    }

    fun runActionForConnection(context: Context) {
        updateActionsAndMap(loadData(context))

        runFilteredActions(context, _actions.value.filter {
            it.shouldRun(RunEnvironment.NORMAL_CONNECTION)
        })
    }

    fun runActionForAlwaysConnected(context: Context) {
        updateActionsAndMap(loadData(context))

        runFilteredActions(context, _actions.value.filter {
            it.shouldRun(RunEnvironment.ALWAYS_CONNECTED)
        })
    }

    fun runActionsForAutoTimeSetting(context: Context) {
        updateActionsAndMap(loadData(context))

        val actions = _actions.value.filter { it.shouldRun(RunEnvironment.AUTO_TIME_ADJUSTMENT) }
        ProgressEvents.onNext("ActionNames", actions.map { it.title })

        runFilteredActions(
            context,
            _actions.value.filter { it.shouldRun(RunEnvironment.AUTO_TIME_ADJUSTMENT) })

        // show notification if configured
        if (LocalDataStorage.getTimeAdjustmentNotification(context)
            && !WatchInfo.alwaysConnected
        ) { // only create notification for not-always connected watches.
            showTimeSyncNotification()
        }
    }

    fun runActionFindPhone(context: Context) {
        val actionsToRun = _actions.value.filter {
            it.shouldRun(RunEnvironment.FIND_PHONE_PRESSED)
        }
        runFilteredActions(context, actionsToRun)
    }

    private fun showTimeSyncNotification() {
        val dateStr =
            DateFormat.getDateTimeInstance().format(Date(Clock.systemDefaultZone().millis()))

        var msg = "Time set at $dateStr"
        val watchName = WatchInfo.name
        msg += " for $watchName watch"

        notificationProvider.createNotification(
            "G-Shock Smart Sync",
            msg,
            NotificationManager.IMPORTANCE_DEFAULT
        )
    }

    private fun runFilteredActions(context: Context, filteredActions: List<Action>) {

        filteredActions.sortedWith(compareBy { it.runMode.ordinal }) // run SYNC actions first
            .forEach {
                if (it.runMode == RunMode.ASYNC) {
                    Timber.d("------------> running ${it.javaClass.simpleName}")
                    // actions are run on the main lifecycle scope, because the Actions Fragment never gets created.
                    mainScope.launch {
                        println("Running action ASYNC: ${it.title}")
                        runIt(it, context)
                    }
                } else {
                    println("Running action SYNC: ${it.title}")
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
