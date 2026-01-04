package org.avmedia.gshockGoogleSync.ui.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.camera.core.CameraSelector
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DateFormat
import java.time.Clock
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.scratchpad.ActionsStorage
import org.avmedia.gshockGoogleSync.services.NotificationProvider
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.events.CalendarEvents
import org.avmedia.gshockGoogleSync.ui.events.EventsModel
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber

@HiltViewModel
class ActionsViewModel
@Inject
constructor(
        private val api: GShockRepository,
        private val prayerAlarmsHelper: PrayerAlarmsHelper,
        @param:ApplicationContext val appContext: Context, // Inject application context
        private val calendarEvents: CalendarEvents,
        private val actionsStorage: ActionsStorage,
        private val notificationProvider: NotificationProvider
) : ViewModel() {
    private val _actions = MutableStateFlow<List<Action>>(emptyList())
    val actions: StateFlow<List<Action>> = _actions

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private val actionMap = mutableMapOf<Class<out Action>, Action>()

    enum class RunMode {
        SYNC,
        ASYNC,
    }

    /**
     * Represents one-time UI events that should be handled by the UI layer (Fragment/Activity).
     *
     * These events are transient and are not part of the persistent state of the screen. Examples
     * include showing a Snackbar, navigation events, or showing a Toast.
     *
     * Usage:
     * - The ViewModel emits these events via a [SharedFlow].
     * - The UI observes the flow and performs the corresponding action (e.g., showing a message).
     */
    sealed class UiEvent {
        /**
         * Event to show a Snackbar with a specific message.
         * @property message The text message to display in the Snackbar.
         */
        data class ShowSnackbar(val message: String) : UiEvent()
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
        _actions.update { currentList ->
            val newList = ArrayList(currentList)
            val index = newList.indexOfFirst { it::class == updatedAction::class }
            if (index != -1) {
                newList[index] = updatedAction
                actionMap[updatedAction::class.java] = updatedAction
            }
            newList
        }

        viewModelScope.launch { updatedAction.save(appContext, actionsStorage) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Action> getAction(type: Class<T>): T {
        return actionMap[type] as? T
                ?: throw IllegalStateException(
                        "Action of type ${type.simpleName} not found in actionMap."
                )
    }

    init {
        loadInitialActions()
        setupEventSubscription()
    }

    // Subscribe to watch initialization event to load data after connection
    private fun setupEventSubscription() {
        ProgressEvents.runEventActions(
                "ActionViewModel",
                arrayOf(
                        EventAction("WatchInitializationCompleted") {
                            viewModelScope.launch { updateActionsAndMap(loadData(appContext)) }
                        }
                )
        )
    }

    // Method to load the initial list of actions
    private fun loadInitialActions() {
        val initialActions = buildList {
            add(ToggleFlashlightAction("Toggle Flashlight", false))
            add(StartVoiceAssistAction("Start Voice Assistant", false))
            add(NextTrack("Skip to next track", false))
            add(FindPhoneAction(appContext.getString(R.string.find_phone), false))
            add(SetTimeAction(appContext.getString(R.string.set_time), true, api))
            add(
                    SetEventsAction(
                            appContext.getString(R.string.set_reminders),
                            false,
                            api,
                            calendarEvents
                    )
            )
            add(
                    PhotoAction(
                            appContext.getString(R.string.take_photo),
                            false,
                            CameraOrientation.BACK
                    )
            )
            add(PrayerAlarmsAction("Set Prayer Alarms", false, api, prayerAlarmsHelper))
            add(Separator(appContext.getString(R.string.emergency_actions), false))
            add(PhoneDialAction(appContext.getString(R.string.make_phonecall), false, ""))
        }

        // Populate both _actions and actionMap immediately
        updateActionsAndMap(initialActions)
    }

    enum class RunEnvironment {
        NORMAL_CONNECTION, // Connected by long-pressing the LOWER-LEFT button
        ACTION_BUTTON_PRESSED, // Connected by short-pressing the LOWER-RIGHT button
        AUTO_TIME_ADJUSTMENT, // Connected automatically during auto time update
        FIND_PHONE_PRESSED, // The user has activated the "Find Phone" function
        ALWAYS_CONNECTED, // Some watches are always connected, but the watch keeps connecting and
        // disconnecting periodically.
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

        open suspend fun save(context: Context, actionsStorage: ActionsStorage) {
            val key = this.javaClass.simpleName + ENABLED
            val value = enabled

            val actionEnum =
                    when (this) {
                        is SetTimeAction -> ActionsStorage.Action.SET_TIME
                        is SetEventsAction -> ActionsStorage.Action.REMINDERS
                        is FindPhoneAction -> ActionsStorage.Action.PHONE_FINDER
                        is PhotoAction -> ActionsStorage.Action.TAKE_PHOTO
                        is ToggleFlashlightAction -> ActionsStorage.Action.FLASHLIGHT
                        is StartVoiceAssistAction -> ActionsStorage.Action.VOICE_ASSIST
                        is NextTrack -> ActionsStorage.Action.SKIP_TO_NEXT_TRACK
                        is PrayerAlarmsAction -> ActionsStorage.Action.PRAYER_ALARMS
                        is PhoneDialAction -> ActionsStorage.Action.PHONE_CALL
                        else -> null
                    }

            actionEnum?.let { actionsStorage.update(it, enabled) }
        }

        open suspend fun load(context: Context, actionsStorage: ActionsStorage) {
            val actionEnum =
                    when (this) {
                        is SetTimeAction -> ActionsStorage.Action.SET_TIME
                        is SetEventsAction -> ActionsStorage.Action.REMINDERS
                        is FindPhoneAction -> ActionsStorage.Action.PHONE_FINDER
                        is PhotoAction -> ActionsStorage.Action.TAKE_PHOTO
                        is ToggleFlashlightAction -> ActionsStorage.Action.FLASHLIGHT
                        is StartVoiceAssistAction -> ActionsStorage.Action.VOICE_ASSIST
                        is NextTrack -> ActionsStorage.Action.SKIP_TO_NEXT_TRACK
                        is PrayerAlarmsAction -> ActionsStorage.Action.PRAYER_ALARMS
                        is PhoneDialAction -> ActionsStorage.Action.PHONE_CALL
                        else -> null
                    }

            actionEnum?.let { enabled = actionsStorage.getAction(it) }
            Timber.d("Load value: ${this.javaClass.simpleName}, $enabled")
        }

        open fun validate(context: Context): Boolean {
            return true
        }
    }

    data class SetEventsAction(
            override var title: String,
            override var enabled: Boolean,
            val api: GShockRepository,
            val calendarEvents: CalendarEvents
    ) : Action(title, enabled, RunMode.ASYNC) {

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

        override suspend fun load(context: Context, actionsStorage: ActionsStorage) {
            enabled = actionsStorage.getAction(ActionsStorage.Action.REMINDERS)
        }
    }

    data class ToggleFlashlightAction(override var title: String, override var enabled: Boolean) :
            Action(title, enabled) {

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            FlashlightHelper.toggle(context)
        }

        override suspend fun load(context: Context, actionsStorage: ActionsStorage) {
            enabled = actionsStorage.getAction(ActionsStorage.Action.FLASHLIGHT)
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
                RunEnvironment.ALWAYS_CONNECTED -> false
            }
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            PhoneFinder.ring(context)
        }

        override suspend fun load(context: Context, actionsStorage: ActionsStorage) {
            enabled = actionsStorage.getAction(ActionsStorage.Action.PHONE_FINDER)
        }
    }

    data class SetTimeAction(
            override var title: String,
            override var enabled: Boolean,
            val api: GShockRepository
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
                    (WatchInfo.alwaysConnected &&
                            (lastSet == null ||
                                    System.currentTimeMillis() - lastSet!! > 1000 * 60 * 60))

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

            // actions are sun on the main lifecycle scope, because the Actions Fragment never gets
            // created.
            CoroutineScope(Dispatchers.Main).launch {
                api.setTime(timeMs = timeMs)
                lastSet = timeMs
            }
        }

        override suspend fun load(context: Context, actionsStorage: ActionsStorage) {
            enabled = actionsStorage.getAction(ActionsStorage.Action.SET_TIME)
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
    ) : Action(title, enabled, RunMode.SYNC) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            runCatching {
                val intent =
                        Intent(Intent.ACTION_VOICE_COMMAND).apply {
                            flags =
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        }
                context.startActivity(intent)
            }
                    .onFailure {
                        if (it is ActivityNotFoundException) {
                            AppSnackbar(
                                    context.getString(
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
    ) : Action(title, enabled, RunMode.ASYNC) {
        override fun run(context: Context) {
            Timber.d("running \${this.javaClass.simpleName}")
            runCatching {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val eventTime = SystemClock.uptimeMillis()

                val downEvent =
                        KeyEvent(
                                eventTime,
                                eventTime,
                                KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_MEDIA_NEXT,
                                0
                        )
                audioManager.dispatchMediaKeyEvent(downEvent)

                val upEvent =
                        KeyEvent(
                                eventTime,
                                eventTime,
                                KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_MEDIA_NEXT,
                                0
                        )
                audioManager.dispatchMediaKeyEvent(upEvent)
            }
                    .onFailure {
                        if (it is ActivityNotFoundException) {
                            AppSnackbar(context.getString(R.string.cannot_go_to_next_track))
                        }
                    }
        }
    }

    data class PrayerAlarmsAction(
            override var title: String,
            override var enabled: Boolean,
            val api: GShockRepository,
            val prayerAlarmsHelper: PrayerAlarmsHelper
    ) : Action(title, enabled, RunMode.ASYNC) {

        private var lastSet: Long? = null

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean {
            // update every 6 hours
            val setTimeConditionAlwaysConnected =
                    (enabled &&
                            WatchInfo.alwaysConnected &&
                            (lastSet == null ||
                                    System.currentTimeMillis() - lastSet!! > 6000 * 60 * 60))

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
            // vvv 4. CALL THE METHOD ON THE INSTANCE vvv
            CoroutineScope(Dispatchers.Main).launch {
                prayerAlarmsHelper
                        .createNextPrayerAlarms(WatchInfo.alarmCount)
                        .onSuccess { alarms ->
                            // getAlarms need to be run first, otherwise setAlarms() will not work
                            api.getAlarms()
                            api.setAlarms(ArrayList(alarms))
                            lastSet = System.currentTimeMillis()
                        }
                        .onFailure { error ->
                            Timber.e("Could not set prayer alarms: ${error.message}")
                            AppSnackbar("Failed to set prayer alarms: ${error.message}")
                        }
            }
        }
    }

    data class Separator(override var title: String, override var enabled: Boolean) :
            Action(title, enabled) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
        }

        override suspend fun load(context: Context, actionsStorage: ActionsStorage) {
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
            override var title: String,
            override var enabled: Boolean,
            var phoneNumber: String
    ) : Action(title, enabled) {
        init {
            Timber.d("PhoneDialAction")
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")

            val dialIntent =
                    Intent(Intent.ACTION_CALL)
                            .setFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            )
            dialIntent.data = "tel:$phoneNumber".toUri()
            context.startActivity(dialIntent)
        }

        override suspend fun save(context: Context, actionsStorage: ActionsStorage) {
            super.save(context, actionsStorage)
            val key = this.javaClass.simpleName + ".phoneNumber"
            LocalDataStorage.put(context, key, phoneNumber)
        }

        override suspend fun load(context: Context, actionsStorage: ActionsStorage) {
            super.load(context, actionsStorage)
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
        FRONT,
        BACK
    }

    data class PhotoAction(
            override var title: String,
            override var enabled: Boolean,
            var cameraOrientation: CameraOrientation,
    ) : Action(title, enabled, RunMode.ASYNC) {
        init {
            Timber.d("PhotoAction: orientation: $cameraOrientation")
        }

        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName}")
            var captureResult: String?
            val cameraSelector =
                    when (cameraOrientation) {
                        CameraOrientation.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                        CameraOrientation.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                    }
            val cameraHelper = CameraCaptureHelper(context, cameraSelector)

            // Initialize the camera
            cameraHelper.initCamera()

            // Launch a coroutine to take the picture
            CoroutineScope(Dispatchers.Main).launch {
                cameraHelper.takePicture(
                        onImageCaptured = { result ->
                            captureResult = result
                            AppSnackbar(context.getString(R.string.image_captured, captureResult!!))
                            // Handle result, maybe pass it to the UI or save it
                        },
                        onError = { error ->
                            captureResult = "Error: $error"
                            AppSnackbar(
                                    context.getString(
                                            R.string.camera_capture_error,
                                            captureResult!!
                                    )
                            )
                        }
                )
            }
        }

        override suspend fun save(context: Context, actionsStorage: ActionsStorage) {
            super.save(context, actionsStorage)
            val key = this.javaClass.simpleName + ".cameraOrientation"
            LocalDataStorage.put(context, key, cameraOrientation.toString())
        }

        override suspend fun load(context: Context, actionsStorage: ActionsStorage) {
            super.load(context, actionsStorage)
            val key = this.javaClass.simpleName + ".cameraOrientation"
            cameraOrientation =
                    if (LocalDataStorage.get(context, key, "BACK").toString() == "BACK")
                            CameraOrientation.BACK
                    else CameraOrientation.FRONT
        }
    }

    private fun runIt(action: Action, context: Context) {
        runCatching { action.run(context) }.onFailure {
            when (it) {
                is SecurityException ->
                        AppSnackbar(
                                context.getString(
                                        R.string.you_have_not_given_permission_to_to_run_action,
                                        action.title
                                )
                        )
                else -> AppSnackbar("Could not run action ${action.title}. Reason: $it")
            }
        }
    }

    fun runActionsForActionButton(context: Context) {
        viewModelScope.launch {
            val actions =
                    _actions.value.filter { it.shouldRun(RunEnvironment.ACTION_BUTTON_PRESSED) }
            ProgressEvents.onNext("ActionNames", actions.map { it.title })
            runFilteredActions(context, actions)
        }
    }

    fun runActionForConnection(context: Context) {
        viewModelScope.launch {
            runFilteredActions(
                    context,
                    _actions.value.filter { it.shouldRun(RunEnvironment.NORMAL_CONNECTION) }
            )
        }
    }

    fun runActionForAlwaysConnected(context: Context) {
        viewModelScope.launch {
            runFilteredActions(
                    context,
                    _actions.value.filter { it.shouldRun(RunEnvironment.ALWAYS_CONNECTED) }
            )
        }
    }

    fun runActionsForAutoTimeSetting(context: Context) {
        viewModelScope.launch {
            val actions =
                    _actions.value.filter { it.shouldRun(RunEnvironment.AUTO_TIME_ADJUSTMENT) }
            ProgressEvents.onNext("ActionNames", actions.map { it.title })

            runFilteredActions(
                    context,
                    _actions.value.filter { it.shouldRun(RunEnvironment.AUTO_TIME_ADJUSTMENT) }
            )

            // show notification if configured
            if (LocalDataStorage.getTimeAdjustmentNotification(context) &&
                            !WatchInfo.alwaysConnected
            ) { // only create notification for not-always connected watches.
                showTimeSyncNotification()
            }
        }
    }

    fun runActionFindPhone(context: Context) {
        val actionsToRun = _actions.value.filter { it.shouldRun(RunEnvironment.FIND_PHONE_PRESSED) }
        runFilteredActions(context, actionsToRun)
    }

    private fun showTimeSyncNotification() {
        val dateStr =
                DateFormat.getDateTimeInstance().format(Date(Clock.systemDefaultZone().millis()))
        val watchName = WatchInfo.name
        val text = "Time set at $dateStr for $watchName watch"

        notificationProvider.createNotification(
                NotificationProvider.NotificationContent(title = "G-Shock Smart Sync", text = text)
        )
    }

    private fun runFilteredActions(context: Context, filteredActions: List<Action>) {

        filteredActions.sortedWith(compareBy { it.runMode.ordinal }) // run SYNC actions first
                .forEach {
                    if (it.runMode == RunMode.ASYNC) {
                        Timber.d("------------> running ${it.javaClass.simpleName}")
                        // actions are run on the main lifecycle scope, because the Actions Fragment
                        // never gets created.
                        // Using GlobalScope or a custom scope here to ensure it runs even if VM is
                        // cleared,
                        // but ideally this should be tied to a service or work manager.
                        // For now, mirroring previous behavior but avoiding the custom static
                        // scope.
                        CoroutineScope(Dispatchers.Main).launch {
                            println("Running action ASYNC: ${it.title}")
                            runIt(it, context)
                        }
                    } else {
                        println("Running action SYNC: ${it.title}")
                        runIt(it, context)
                    }
                }
    }

    private suspend fun loadData(context: Context): List<Action> {
        // Load data from watch
        actionsStorage.load()

        if (api.isScratchpadReset()) {
            _actions.value.forEach { it.save(context, actionsStorage) }
            actionsStorage.save()
        } else {
            _actions.value.forEach { it.load(context, actionsStorage) }
        }

        LocalDataStorage.put(context, "ActionsInitialized", "true")

        return _actions.value
    }

    fun save() {
        viewModelScope.launch { actionsStorage.save() }
    }

    fun saveWithMessage(message: String) {
        viewModelScope.launch {
            actionsStorage.save()
            _uiEvents.emit(UiEvent.ShowSnackbar(message))
        }
    }
}
