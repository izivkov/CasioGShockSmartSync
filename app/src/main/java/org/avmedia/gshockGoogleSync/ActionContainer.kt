package org.avmedia.gshockGoogleSync

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.camera.core.CameraSelector
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DateFormat
import java.time.Clock
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.scratchpad.ActionsStorage
import org.avmedia.gshockGoogleSync.scratchpad.AlarmNameStorage
import org.avmedia.gshockGoogleSync.scratchpad.EventStorage
import org.avmedia.gshockGoogleSync.services.NotificationProvider
import org.avmedia.gshockGoogleSync.ui.actions.CameraCaptureHelper
import org.avmedia.gshockGoogleSync.ui.actions.FlashlightHelper
import org.avmedia.gshockGoogleSync.ui.actions.PhoneFinder
import org.avmedia.gshockGoogleSync.ui.actions.PrayerAlarmsHelper
import org.avmedia.gshockGoogleSync.ui.actions.WatchTimeUpdater
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockGoogleSync.ui.events.CalendarEvents
import org.avmedia.gshockGoogleSync.ui.events.EventsModel
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockGoogleSync.ui.common.IWatchFeatureManager
import org.avmedia.gshockGoogleSync.utils.CyrillicToLatin
import org.avmedia.gshockapi.model.Alarm
import org.avmedia.gshockapi.model.Event
import org.avmedia.gshockapi.model.Settings
import org.avmedia.gshockapi.model.StepCounterData
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration

/**
 * Payload for the "AlarmsUpdated" event: the exact alarm list that was just
 * written to the watch. Lets listeners (e.g. AlarmViewModel) verify a
 * subsequent read-back genuinely reflects this write, rather than guessing
 * from "did anything change".
 */
data class AlarmsWritten(val alarms: List<Alarm>)

/**
 * Application-scoped so that watch button presses are handled even when no UI is composed.
 *
 * This used to be called ActionsViewModel and was itself injected directly by both UI
 * screens and background singletons (ActionRunner, VoiceDispatcher). That meant a process
 * woken in the background by GShockCompanionDeviceService (no Activity, no composition) had
 * nobody listening for "ButtonPressedInfoReceived", so presses were silently dropped until
 * the user opened the app. As a @Singleton created in GShockApplication.onCreate(), the
 * subscription exists for the whole process lifetime.
 *
 * The UI-facing name "ActionsViewModel" now belongs to a real @HiltViewModel (see
 * ActionsViewModel.kt) that wraps this container for Compose screens via hiltViewModel().
 * Background singletons (ActionRunner, VoiceDispatcher, VoiceCommandTable) and other
 * feature ViewModels (SettingsViewModel, AlarmViewModel, TimeViewModel, EventViewModel)
 * inject this class directly, since they have no screen lifecycle to scope to.
 */
@Singleton
class ActionContainer
@Inject
constructor(
    private val api: GShockRepository,
    private val prayerAlarmsHelper: PrayerAlarmsHelper,
    @param:ApplicationContext val appContext: Context, // Inject application context
    private val calendarEvents: CalendarEvents,
    private val actionsStorage: ActionsStorage,
    private val notificationProvider: NotificationProvider,
    private val watchTimeUpdater: WatchTimeUpdater,
    private val watchFeatureManager: IWatchFeatureManager,
    private val eventStorage: EventStorage,
    private val alarmNameStorage: AlarmNameStorage
) {
    /** Manually-created scope, since this class has no ViewModel/Activity lifecycle to tie one to. Lives as long as the process. */
    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _actions = MutableStateFlow<List<Action>>(emptyList())
    val actions: StateFlow<List<Action>> = _actions

    private val isDataLoaded = CompletableDeferred<Unit>()

    private val actionMap = mutableMapOf<Class<out Action>, Action>()

    private var saveJob: Job? = null

    private companion object {
        const val ONE_HOUR_MS = 60 * 60 * 1000L
        const val SIX_HOURS_MS = 6 * ONE_HOUR_MS
    }

    enum class RunMode {
        SYNC,
        ASYNC,
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

        containerScope.launch {
            updatedAction.save(appContext, actionsStorage)

            saveJob?.cancel()
            saveJob = containerScope.launch {
                delay(Duration.ZERO)
                actionsStorage.save()
                AppSnackbar(appContext.getString(R.string.actions_saved))
                saveJob = null
            }
        }
    }

    /**
     * No longer a ViewModel lifecycle callback. Kept so a pending debounced save can be
     * flushed explicitly if a caller ever needs to; the process-scoped instance is not
     * torn down on Activity destruction.
     */
    fun onCleared() {
        saveJob?.let {
            saveJob?.cancel()
            containerScope.launch { actionsStorage.save() }
        }
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
            "ActionContainer",
            arrayOf(
                EventAction("WatchInitializationCompleted") {
                    containerScope.launch { updateActionsAndMap(loadData(appContext)) }
                }
            )
        )
    }

    // Method to load the initial list of actions
    private fun loadInitialActions() {
        val initialActions = buildList {
            add(ToggleFlashlightAction("Toggle Flashlight", false))
            add(StartVoiceAssistAction("Start Voice Assistant", false))
            add(NextTrack(appContext.getString(R.string.next_track), false))
            add(TogglePlayPauseAction(appContext.getString(R.string.play_pause), false))
            add(FindPhoneAction(appContext.getString(R.string.find_phone), false))
            add(SetTimeAction(appContext.getString(R.string.set_time), true, watchTimeUpdater))
            add(
                SetEventsAction(
                    appContext.getString(R.string.set_reminders),
                    true,
                    api,
                    calendarEvents
                )
            )
            add(SetAlarmAction(appContext.getString(R.string.set_alarm), true))
            add(ClearAllAlarmsAction("Clear All Alarms", true)) // Hidden from UI, only for voice
            add(DisableAllAlarmsAction("Disable All Alarms", true)) // Hidden from UI, only for voice
            add(SetSettingsToDefaultAction("Set Settings to Default", true)) // Hidden from UI, only for voice
            add(SetSettingsAction("Set Settings", true)) // Hidden from UI, only for voice
            add(SetTimerAction("Set Timer", true)) // Hidden from UI, only for voice/Send-to-Watch
            add(ClearStepHistoryAction("Clear Step History", true)) // Hidden from UI, direct-invocation only
            add(
                PhotoAction(
                    appContext.getString(R.string.take_photo),
                    false,
                    CameraOrientation.BACK
                )
            )
            add(PrayerAlarmsAction("Set Prayer Alarms", false, prayerAlarmsHelper))
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
        VOICE_COMMAND, // Triggered by voice command
        ALWAYS_CONNECTED, // Some watches are always connected, but the watch keeps connecting and
        // disconnecting periodically.
        DIRECT_INVOCATION, // Called directly from app code (e.g., a "Send to Watch" button)
    }

    abstract inner class Action(
        open var title: String,
        open var enabled: Boolean,
        var runMode: RunMode = RunMode.SYNC,
    ) {

        open fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
            RunEnvironment.ACTION_BUTTON_PRESSED, RunEnvironment.VOICE_COMMAND -> enabled
            else -> false
        }

        abstract fun run(context: Context)
        open suspend fun runSuspend(context: Context) {
            run(context)
        }

        /** For actions whose real work is [runSuspend]: run it on the container scope. */
        protected fun launchRunSuspend(context: Context) {
            containerScope.launch { runSuspend(context) }
        }

        /** Key under which [enabled] is persisted; null for actions that are not stored. */
        private val storageKey: ActionsStorage.Action?
            get() = when (this) {
                is SetTimeAction -> ActionsStorage.Action.SET_TIME
                is SetEventsAction -> ActionsStorage.Action.REMINDERS
                is FindPhoneAction -> ActionsStorage.Action.PHONE_FINDER
                is PhotoAction -> ActionsStorage.Action.TAKE_PHOTO
                is ToggleFlashlightAction -> ActionsStorage.Action.FLASHLIGHT
                is StartVoiceAssistAction -> ActionsStorage.Action.VOICE_ASSIST
                is NextTrack -> ActionsStorage.Action.SKIP_TO_NEXT_TRACK
                is TogglePlayPauseAction -> ActionsStorage.Action.TOGGLE_PLAY_PAUSE
                is PrayerAlarmsAction -> ActionsStorage.Action.PRAYER_ALARMS
                is PhoneDialAction -> ActionsStorage.Action.PHONE_CALL
                else -> null
            }

        open suspend fun save(context: Context, actionsStorage: ActionsStorage) {
            storageKey?.let { actionsStorage.update(it, enabled) }
        }

        open suspend fun load(context: Context, actionsStorage: ActionsStorage) {
            storageKey?.let { enabled = actionsStorage.getAction(it) }
            Timber.d("Load value: ${this.javaClass.simpleName}, $enabled")
        }

        open fun validate(context: Context): Boolean {
            return true
        }
    }

    inner class SetEventsAction(
        override var title: String,
        override var enabled: Boolean,
        val api: GShockRepository,
        val calendarEvents: CalendarEvents
    ) : Action(title, enabled, RunMode.ASYNC) {

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
            RunEnvironment.NORMAL_CONNECTION,
            RunEnvironment.ACTION_BUTTON_PRESSED,
            RunEnvironment.AUTO_TIME_ADJUSTMENT ->
                enabled && watchFeatureManager.isFeatureSupported("actions.reminders") &&
                        !eventStorage.isManualMode()
            RunEnvironment.VOICE_COMMAND -> enabled
            RunEnvironment.DIRECT_INVOCATION -> true
            else -> false
        }

        override fun run(context: Context) {
            if (eventStorage.isManualMode()) {
                Timber.d("SetEventsAction skipped - Manual Mode is active")
                return
            }
            EventsModel.refresh(calendarEvents.getEventsFromCalendar())
            containerScope.launch {
                writeEvents(context, EventsModel.events)
            }
        }

        /**
         * Entry point for callers that already have the exact events list to write
         * (currently: EventViewModel.sendEventsToWatch(), via RunEnvironment.DIRECT_INVOCATION).
         * Deliberately bypasses the isManualMode() skip above - manual mode is precisely
         * the scenario this entry point exists to handle.
         */
        suspend fun runWithEvents(context: Context, events: List<Event>) {
            runCatching {
                writeEvents(context, events)
            }.onFailure {
                Timber.e(it, "Failed to send events to watch")
                AppSnackbar("Error: ${it.message ?: ""}")
            }
        }

        private suspend fun writeEvents(context: Context, events: List<Event>) {
            val processedEvents = events.map {
                it.copy(title = CyrillicToLatin.transliterate(it.title))
            }
            api.setEvents(ArrayList(processedEvents))
            val freshEvents = api.getEventsFromWatch()
            ProgressEvents.onNext("EventsUpdated", freshEvents)
            AppSnackbar(context.getString(R.string.reminders_sent_to_watch))
        }
    }

    inner class ToggleFlashlightAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {

        override fun run(context: Context) {
            FlashlightHelper.toggle(context)
        }
    }

    inner class FindPhoneAction(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
            RunEnvironment.ACTION_BUTTON_PRESSED ->
                enabled && watchFeatureManager.isFeatureSupported("actions.find_phone")
            RunEnvironment.VOICE_COMMAND -> enabled
            RunEnvironment.FIND_PHONE_PRESSED -> true
            else -> false
        }

        override fun run(context: Context) {
            PhoneFinder.ring(context)
        }
    }

    inner class SetTimeAction(
        override var title: String,
        override var enabled: Boolean,
        val watchTimeUpdater: WatchTimeUpdater
    ) :
        Action(
            title,
            enabled,
            RunMode.ASYNC,
        ) {

        private var lastSet: Long? = null

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
            RunEnvironment.ACTION_BUTTON_PRESSED, RunEnvironment.VOICE_COMMAND -> enabled
            RunEnvironment.AUTO_TIME_ADJUSTMENT -> true
            RunEnvironment.ALWAYS_CONNECTED ->
                watchFeatureManager.isFeatureSupported("time_adjustment.always_connected") &&
                        isOlderThan(lastSet, ONE_HOUR_MS)
            else -> false
        }

        override fun run(context: Context) {

            // actions are sun on the main lifecycle scope, because the Actions Fragment never gets
            // created.
            CoroutineScope(Dispatchers.Main).launch {
                watchTimeUpdater.updateTime()
                lastSet = System.currentTimeMillis()
            }
        }
    }


    inner class StartVoiceAssistAction(
        override var title: String,
        override var enabled: Boolean,
    ) : Action(title, enabled, RunMode.SYNC) {
        override fun run(context: Context) {
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

    inner class NextTrack(
        override var title: String,
        override var enabled: Boolean,
    ) : Action(title, enabled, RunMode.ASYNC) {
        override fun run(context: Context) {
            sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT).onFailure {
                if (it is ActivityNotFoundException) {
                    AppSnackbar(context.getString(R.string.cannot_go_to_next_track))
                }
            }
        }
    }

    inner class TogglePlayPauseAction(
        override var title: String,
        override var enabled: Boolean,
    ) : Action(title, enabled, RunMode.ASYNC) {
        override fun run(context: Context) {
            sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE).onFailure {
                Timber.e(it, "Could not toggle play/pause")
            }
        }
    }

    /** Dispatches a media key press (down + up) to the active media session. */
    private fun sendMediaKey(context: Context, keyCode: Int): Result<Unit> = runCatching {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventTime = SystemClock.uptimeMillis()
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { keyAction ->
            audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, keyAction, keyCode, 0))
        }
    }

    private suspend fun writeAlarmsToWatch(
        context: Context,
        alarms: List<Alarm>,
        clearNames: Boolean = false,
        updateHourlyChime: Boolean = false,
    ) {
        if (clearNames) {
            alarmNameStorage.clear()
        } else {
            alarms.forEachIndexed { index, alarm ->
                alarmNameStorage.put(alarm.name ?: "", index)
            }
        }
        alarmNameStorage.save()

        api.setAlarms(ArrayList(alarms))

        val hasHourlyChime = watchFeatureManager.isFeatureSupported("alarms.chime")
        if (hasHourlyChime && updateHourlyChime) {
            val chimeEnabled = alarms.firstOrNull()?.hasHourlyChime ?: false
            api.setSettings(api.getSettings().copy(hourlyChime = chimeEnabled))
        }
        val settings = if (hasHourlyChime) api.getSettings() else null

        val alarmsFromWatch = api.getAlarms()
            .take(watchFeatureManager.getAlarmCount())
            .mapIndexed { index, alarm ->
                val name = alarmNameStorage.get(index)
                alarm.copy(
                    name = name,
                    hasHourlyChime = if (index == 0 && settings != null) {
                        settings.hourlyChime
                    } else alarm.hasHourlyChime
                )
            }

        ProgressEvents.onNext("AlarmsUpdated", AlarmsWritten(alarmsFromWatch))
        AppSnackbar(context.getString(R.string.alarms_set_to_watch))
    }

    inner class PrayerAlarmsAction(
        override var title: String,
        override var enabled: Boolean,
        val prayerAlarmsHelper: PrayerAlarmsHelper
    ) : Action(title, enabled, RunMode.ASYNC) {

        private var lastSet: Long? = null

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
            RunEnvironment.NORMAL_CONNECTION,
            RunEnvironment.ACTION_BUTTON_PRESSED,
            RunEnvironment.AUTO_TIME_ADJUSTMENT,
            RunEnvironment.VOICE_COMMAND -> enabled
            RunEnvironment.ALWAYS_CONNECTED ->
                enabled &&
                        watchFeatureManager.isFeatureSupported("time_adjustment.always_connected") &&
                        isOlderThan(lastSet, SIX_HOURS_MS)
            else -> false
        }

        override fun run(context: Context) {
            containerScope.launch {
                prayerAlarmsHelper
                    .createNextPrayerAlarms(watchFeatureManager.getAlarmCount())
                    .onSuccess { alarms ->
                        val setAlarmAction = getAction(SetAlarmAction::class.java)
                        if (setAlarmAction.runWithAlarms(context, alarms)) {
                            lastSet = System.currentTimeMillis()
                        }
                    }
                    .onFailure { error ->
                        Timber.e("Could not set prayer alarms: ${error.message}")
                        AppSnackbar("Failed to set prayer alarms: ${error.message}")
                    }
            }
        }
    }

    inner class Separator(override var title: String, override var enabled: Boolean) :
        Action(title, enabled) {
        override fun run(context: Context) {
        }
    }


    inner class PhoneDialAction(
        override var title: String,
        override var enabled: Boolean,
        var phoneNumber: String
    ) : Action(title, enabled) {
        init {
            Timber.d("PhoneDialAction")
        }

        override fun run(context: Context) {

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

    inner class PhotoAction(
        override var title: String,
        override var enabled: Boolean,
        var cameraOrientation: CameraOrientation,
    ) : Action(title, enabled, RunMode.ASYNC) {
        init {
            Timber.d("PhotoAction: orientation: $cameraOrientation")
        }

        override fun run(context: Context) {

            val cameraSelector =
                when (cameraOrientation) {
                    CameraOrientation.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                    CameraOrientation.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                }
            val cameraHelper = CameraCaptureHelper(context, cameraSelector)

            // Launch a coroutine to take the picture
            CoroutineScope(Dispatchers.Main).launch {
                cameraHelper
                    .takePicture()
                    .onSuccess { result ->
                        AppSnackbar(context.getString(R.string.image_captured, result))
                    }
                    .onFailure { error ->
                        AppSnackbar(
                            context.getString(
                                R.string.camera_capture_error,
                                error.message ?: "Unknown error"
                            )
                        )
                    }
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

    inner class SetAlarmAction(
        override var title: String,
        override var enabled: Boolean,
        var alarmHour: Int = 8,
        var alarmMinute: Int = 0
    ) : Action(title, enabled, RunMode.ASYNC) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName} for $alarmHour:$alarmMinute")
            launchRunSuspend(context)
        }

        override suspend fun runSuspend(context: Context) {
            runCatching {
                alarmNameStorage.load()
                val alarms = api.getAlarms()
                val alarmCount = watchFeatureManager.getAlarmCount()

                val alarmList = alarms.take(alarmCount).mapIndexed { index, alarm ->
                    alarm.copy(name = alarmNameStorage.get(index))
                }.toMutableList()

                val existingIndex = alarmList.indexOfFirst { it.hour == alarmHour && it.minute == alarmMinute }
                if (existingIndex != -1) {
                    alarmList[existingIndex] = alarmList[existingIndex].copy(enabled = true)
                } else {
                    val indexToUpdate = alarmList.indexOfFirst { !it.enabled }.let { if (it == -1) 0 else it }
                    alarmList[indexToUpdate] = alarmList[indexToUpdate].copy(
                        hour = alarmHour,
                        minute = alarmMinute,
                        enabled = true,
                        name = ""
                    )
                }

                writeAlarmsToWatch(context, alarmList)
            }.onFailure {
                Timber.e(it, "Failed to set watch alarm via voice")
                AppSnackbar("Failed to set watch alarm")
            }
        }

        /**
         * Entry point for callers that already have the exact, full alarm list to write
         * (currently: AlarmViewModel.sendAlarmsToWatch(), via RunEnvironment.DIRECT_INVOCATION).
         * Unlike the voice path, this does not upsert a single slot by hour/minute - it writes
         * the list exactly as given.
         */
        suspend fun runWithAlarms(
            context: Context,
            alarms: List<Alarm>,
            updateHourlyChime: Boolean = false,
        ): Boolean {
            return runCatching {
                writeAlarmsToWatch(context, alarms, updateHourlyChime = updateHourlyChime)
                true
            }.onFailure {
                Timber.e(it, "Failed to send alarms to watch")
                AppSnackbar("Failed to set watch alarm")
            }.getOrDefault(false)
        }

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
            RunEnvironment.VOICE_COMMAND -> enabled
            RunEnvironment.DIRECT_INVOCATION -> true
            else -> false
        }

        override suspend fun save(context: Context, actionsStorage: ActionsStorage) {
            LocalDataStorage.put(context, this.javaClass.simpleName + ".hour", alarmHour.toString())
            LocalDataStorage.put(context, this.javaClass.simpleName + ".minute", alarmMinute.toString())
            super.save(context, actionsStorage)
        }

        override suspend fun load(context: Context, actionsStorage: ActionsStorage) {
            alarmHour = LocalDataStorage.get(context, this.javaClass.simpleName + ".hour", "8")!!.toInt()
            alarmMinute = LocalDataStorage.get(context, this.javaClass.simpleName + ".minute", "0")!!.toInt()
            super.load(context, actionsStorage)
        }
    }

    inner class ClearAllAlarmsAction(
        override var title: String,
        override var enabled: Boolean
    ) : Action(title, enabled, RunMode.ASYNC) {
        override fun run(context: Context) {
            containerScope.launch {
                runCatching {
                    val alarms = api.getAlarms()
                    val alarmCount = watchFeatureManager.getAlarmCount()
                    val updatedAlarms = alarms.take(alarmCount).map {
                        it.copy(enabled = false, hour = 0, minute = 0)
                    }

                    writeAlarmsToWatch(context, updatedAlarms, clearNames = true)
                }.onFailure {
                    Timber.e(it, "Failed to clear all alarms via voice")
                    AppSnackbar("Failed to clear alarms")
                }
            }
        }

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
            RunEnvironment.VOICE_COMMAND -> enabled
            else -> false
        }
    }

    inner class DisableAllAlarmsAction(
        override var title: String,
        override var enabled: Boolean
    ) : Action(title, enabled, RunMode.ASYNC) {
        override fun run(context: Context) {
            containerScope.launch {
                runCatching {
                    val alarms = api.getAlarms()
                    val alarmCount = watchFeatureManager.getAlarmCount()
                    val updatedAlarms = alarms.take(alarmCount).map {
                        it.copy(enabled = false)
                    }

                    writeAlarmsToWatch(context, updatedAlarms)
                }.onFailure {
                    Timber.e(it, "Failed to disable all alarms via voice")
                    AppSnackbar("Failed to disable alarms")
                }
            }
        }

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
            RunEnvironment.VOICE_COMMAND -> enabled
            else -> false
        }
    }

    inner class SetSettingsAction(
        override var title: String,
        override var enabled: Boolean,
        var settingName: String = "",
        var settingValue: String = "",
    ) : Action(title, enabled, RunMode.ASYNC) {
        override fun run(context: Context) {
            Timber.d("running ${this.javaClass.simpleName} for $settingName=$settingValue")
            launchRunSuspend(context)
        }

        override suspend fun runSuspend(context: Context) {
            runCatching {
                val current = api.getSettings()
                val toSend = when (settingName) {
                    "auto light" -> current.copy(autoLight = settingValue.toBoolean())
                    "power saving" -> current.copy(powerSavingMode = settingValue.toBoolean())
                    "language" -> current.copy(language = settingValue)
                    "time format" -> current.copy(timeFormat = settingValue)
                    "date format" -> current.copy(dateFormat = settingValue)
                    "light duration" -> current.copy(lightDuration = settingValue)
                    "button tone" -> {
                        val enabled = settingValue.toBoolean()
                        current.copy(buttonTone = enabled, keyVibration = enabled)
                    }
                    else -> current
                }
                sendSettings(context, toSend)
            }.onFailure {
                Timber.e(it, "Failed to send settings to watch")
            }
        }

        /**
         * Entry point for callers that already have a fully-built Settings object
         * (currently: SettingsViewModel.sendToWatch(), via RunEnvironment.DIRECT_INVOCATION).
         * Bypasses the settingName/settingValue single-field mapping used by voice commands,
         * since the caller already knows every field it wants to send.
         */
        suspend fun runWithSettings(context: Context, settings: Settings): Boolean {
            return runCatching {
                sendSettings(context, settings)
                true
            }.onFailure {
                Timber.e(it, "Failed to send settings to watch")
            }.getOrDefault(false)
        }

        private suspend fun sendSettings(context: Context, settings: Settings) {
            api.setSettings(settings)
            val freshSettings = api.getSettings()
            ProgressEvents.onNext("SettingsUpdated", freshSettings)
            AppSnackbar(context.getString(R.string.settings_sent_to_watch))
        }

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
            RunEnvironment.VOICE_COMMAND -> enabled
            RunEnvironment.DIRECT_INVOCATION -> true
            else -> false
        }
    }

    inner class SetSettingsToDefaultAction(
        override var title: String,
        override var enabled: Boolean
    ) : Action(title, enabled, RunMode.ASYNC) {

        override fun run(context: Context) = launchRunSuspend(context)

        override suspend fun runSuspend(context: Context) {
            runCatching {
                // Calculation of smart defaults using the same logic as SettingsViewModel
                val settings = Settings()
                val currentLocale = Locale.getDefault()

                // Language
                settings.language = when (currentLocale.language) {
                    "es" -> "Spanish"
                    "fr" -> "French"
                    "de" -> "German"
                    "it" -> "Italian"
                    "ru" -> "Russian"
                    else -> "English"
                }

                val dateTimePattern = SimpleDateFormat().toPattern()
                val datePattern = dateTimePattern.split(" ")[0]
                val timePattern = dateTimePattern.split(" ")[1]

                settings.dateFormat = if (datePattern.lowercase().startsWith("d")) "DD:MM" else "MM:DD"
                settings.timeFormat = if (timePattern[0] == 'h') "12h" else "24h"

                val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val buttonTone = notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
                settings.buttonTone = buttonTone
                settings.keyVibration = buttonTone

                settings.autoLight = false
                settings.lightDuration = "2s"

                if (watchFeatureManager.isFeatureSupported("settings.power_saving")) {
                    val batteryLevel = api.getBatteryLevel()
                    settings.powerSavingMode = batteryLevel <= 15
                }

                if (watchFeatureManager.isFeatureSupported("settings.multiple_fonts")) {
                    // Default to Standard
                    settings.font = "Standard"
                }

                settings.timeAdjustment = true
                settings.adjustmentTimeMinutes = 30

                val setSettingsAction = getAction(SetSettingsAction::class.java)
                if (!setSettingsAction.runWithSettings(context, settings)) {
                    return@runCatching
                }

                // Also update local storage for time adjustment notifications if needed
                val notifyMe = LocalDataStorage.getTimeAdjustmentNotification(appContext)
                LocalDataStorage.setTimeAdjustmentNotification(appContext, notifyMe)
                LocalDataStorage.setFineTimeAdjustment(appContext, 0)

            }.onFailure {
                Timber.e(it, "Failed to set settings to default")
                AppSnackbar("Failed to set defaults")
            }
        }

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
            RunEnvironment.VOICE_COMMAND -> enabled
            else -> false
        }
    }

    inner class SetTimerAction(
        override var title: String,
        override var enabled: Boolean,
        var timerValueS: Int = 0,
    ) : Action(title, enabled, RunMode.ASYNC) {
        override fun run(context: Context) = launchRunSuspend(context)

        override suspend fun runSuspend(context: Context) = runWithTimer(context, timerValueS)

        /**
         * Entry point for callers that already know the timer value to send
         * (currently: TimeViewModel.onAction(UpdateTimer), via RunEnvironment.DIRECT_INVOCATION).
         */
        suspend fun runWithTimer(context: Context, timerValueSeconds: Int) {
            runCatching {
                writeTimer(context, timerValueSeconds)
            }.onFailure {
                Timber.e(it, "Failed to send timer to watch")
            }
        }

        private suspend fun writeTimer(context: Context, timerValueSeconds: Int) {
            val before = api.getTimer()
            api.setTimer(timerValueSeconds)

            // Handle watch hardware latency with retries
            var freshTimer = before
            for (attempt in 1..3) {
                delay(500)
                freshTimer = api.getTimer()
                if (freshTimer != before) break
            }

            ProgressEvents.onNext("TimerUpdated", freshTimer)
            AppSnackbar(context.getString(R.string.timer_set))
        }

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
            RunEnvironment.VOICE_COMMAND -> enabled
            RunEnvironment.DIRECT_INVOCATION -> true
            else -> false
        }
    }

    inner class ClearStepHistoryAction(
        override var title: String,
        override var enabled: Boolean,
    ) : Action(title, enabled, RunMode.ASYNC) {
        override fun run(context: Context) = launchRunSuspend(context)

        override suspend fun runSuspend(context: Context) {
            runClear()
        }

        /**
         * Clears the watch's step history and returns the fresh (zeroed) reading.
         * Entry point for RunEnvironment.DIRECT_INVOCATION callers
         * (currently: TimeViewModel via TimeAction.ClearStepHistory). There is no
         * voice command for this yet, so shouldRun() only permits DIRECT_INVOCATION.
         */
        suspend fun runAndGetFreshData(): Result<StepCounterData> = runClear()

        private suspend fun runClear(): Result<StepCounterData> = runCatching {
            // Watches without a step counter never answer, so fail here instead of timing out.
            check(org.avmedia.gshockapi.WatchInfo.hasStepCounter) { "This watch has no step counter" }

            // First call with peek = false finalizes the transaction and clears watch history.
            api.getStepCount(peek = false)

            // Short delay to allow the watch to process the clear command.
            delay(500)

            // Second call with peek = true reads the fresh (zeroed) state.
            api.getStepCount(peek = true)
        }

        override fun shouldRun(runEnvironment: RunEnvironment): Boolean = when (runEnvironment) {
            RunEnvironment.DIRECT_INVOCATION -> true
            else -> false
        }
    }

    private fun isOlderThan(timestampMs: Long?, ageMs: Long): Boolean =
        timestampMs == null || System.currentTimeMillis() - timestampMs > ageMs

    private fun runIt(action: Action, context: Context) {
        Timber.d("running ${action.javaClass.simpleName}")
        runCatching { action.run(context) }.onFailure {
            Timber.e(it, "Action failed: '${action.title}' (${action.javaClass.simpleName})")
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

    /**
     * Runs every action that opts in to [environment]. When [announce] is set, the titles of the
     * actions about to run are published as "ActionNames" for the RunActions screen.
     */
    private fun runFor(
        environment: RunEnvironment,
        context: Context,
        announce: Boolean = false,
        afterRun: () -> Unit = {},
    ) {
        containerScope.launch {
            isDataLoaded.await()
            val actions = _actions.value.filter { it.shouldRun(environment) }
            if (announce) ProgressEvents.onNext("ActionNames", actions.map { it.title })
            runFilteredActions(context, actions)
            afterRun()
        }
    }

    fun runActionsForActionButton(context: Context) =
        runFor(RunEnvironment.ACTION_BUTTON_PRESSED, context, announce = true)

    fun runActionForConnection(context: Context) =
        runFor(RunEnvironment.NORMAL_CONNECTION, context)

    fun runActionForAlwaysConnected(context: Context) =
        runFor(RunEnvironment.ALWAYS_CONNECTED, context)

    fun runActionFindPhone(context: Context) =
        runFor(RunEnvironment.FIND_PHONE_PRESSED, context)

    fun runActionsForAutoTimeSetting(context: Context) =
        runFor(RunEnvironment.AUTO_TIME_ADJUSTMENT, context, announce = true) {
            // Only notify for watches that are not always connected.
            if (LocalDataStorage.getTimeAdjustmentNotification(context) &&
                !watchFeatureManager.isFeatureSupported("time_adjustment.always_connected")
            ) {
                showTimeSyncNotification()
            }
        }

    private fun showTimeSyncNotification() {
        val dateStr =
            DateFormat.getDateTimeInstance().format(Date(Clock.systemDefaultZone().millis()))
        val watchName = watchFeatureManager.getWatchName()
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
        val start = System.currentTimeMillis()

        // Load data from watch
        val startWatch = System.currentTimeMillis()
        actionsStorage.load()
        Timber.d("actionsStorage.load() (watch communication) took ${System.currentTimeMillis() - startWatch} ms")

        if (api.isScratchpadReset()) {
            _actions.value.forEach { it.save(context, actionsStorage) }
            actionsStorage.save()
        } else {
            _actions.value.forEach { it.load(context, actionsStorage) }
        }

        LocalDataStorage.put(context, "ActionsInitialized", "true")

        if (!isDataLoaded.isCompleted) {
            isDataLoaded.complete(Unit)
        }

        Timber.d("Total loadData took ${System.currentTimeMillis() - start} ms")

        return _actions.value
    }

    suspend fun runSingleActionSuspend(action: Action) {
        isDataLoaded.await()
        action.runSuspend(appContext)
    }

    fun save() {
        containerScope.launch { actionsStorage.save() }
    }
}