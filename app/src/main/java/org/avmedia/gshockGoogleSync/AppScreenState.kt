package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.session.BackgroundConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.avmedia.gshockGoogleSync.utils.AppForeground
import org.avmedia.gshockGoogleSync.session.EntryReason
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.session.SessionPhase
import org.avmedia.gshockGoogleSync.session.SessionState
import org.avmedia.gshockGoogleSync.homeassistant.HaScreenState
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.avmedia.gshockGoogleSync.session.KeepConnected
import org.avmedia.gshockGoogleSync.session.OpenOnConnect
import org.avmedia.gshockGoogleSync.session.WatchList
import javax.inject.Inject
import javax.inject.Singleton

enum class AppScreen {
    INITIAL,
    PRE_CONNECTION,
    CONTENT_SELECTOR,
    MAIN_NAVIGATION,
    RUN_ACTIONS,
    ERROR,

    /** The Home Assistant screen: shown whatever the watch connection does. */
    HOME_ASSISTANT,

    /** The watch list opened from the connected screens: shown whatever the watch connection does, until left. */
    WATCH_LIST
}

data class AppNavigationState(
    val screen: AppScreen = AppScreen.INITIAL,
    val errorMessage: String? = null
)

/**
 * The screen to show, derived entirely from the watch [SessionState]. Nothing navigates imperatively:
 * the Session decides where the app is, and this maps it to a screen.
 */
@Singleton
class AppScreenState @Inject constructor(
    sessionManager: SessionManager,
    haScreenState: HaScreenState,
    @ApplicationContext appContext: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val state: StateFlow<AppNavigationState> =
        combine(
            sessionManager.sessionState, haScreenState.open, AppForeground.inFront, WatchList.open
        ) { session, haOpen, inFront, listOpen ->
            navigationFor(session, haOpen, inFront, listOpen)
        }
            .distinctUntilChanged()
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                navigationFor(
                    sessionManager.sessionState.value, haScreenState.open.value, AppForeground.inFront.value,
                    WatchList.open.value
                )
            )

    init {
        // An always-connected watch that connects, or is connected, while the app is in front on the first
        // page opens the main screen when its "Open when the watch connects" switch is on, or when the
        // person tapped its name. Otherwise the app stays where it is (sync and HA run unchanged).
        scope.launch {
            combine(
                sessionManager.sessionState, AppForeground.inFront, haScreenState.open, OpenOnConnect.waiting,
                WatchList.open
            ) { session, inFront, haOpen, waiting, listOpen ->
                OpenOnConnect.shouldOpen(session, inFront, !haOpen && !listOpen, waiting)
            }.collect { latch ->
                if (latch) sessionManager.activeSession?.let {
                    OpenOnConnect.consumed(it.state.value.watch.address)
                    it.enterMainNavigation()
                }
            }
        }
    }

    init {
        // The watch with Keep connected may reconnect by itself again once the chosen other watch's session ended and
        // nobody waits for it. A waited watch that opened its screens by itself (a button connection) is no longer waited for.
        scope.launch {
            combine(sessionManager.sessionState, OpenOnConnect.waiting) { session, waiting -> session to waiting }
                .collect { (session, waiting) ->
                    val phase = session.phase
                    if (phase is SessionPhase.Ready && phase.entryReason != EntryReason.BACKGROUND &&
                        OpenOnConnect.sameAddress(waiting, session.watch.address)
                    ) OpenOnConnect.consumed(session.watch.address)
                    KeepConnected.updatePause(session, OpenOnConnect.waiting.value)
                }
        }
    }

    companion object {
        internal fun navigationFor(
            session: SessionState,
            haOpen: Boolean = false,
            appInForeground: Boolean = false,
            watchListOpen: Boolean = false
        ): AppNavigationState =
            if (watchListOpen) AppNavigationState(AppScreen.WATCH_LIST)
            else if (haOpen) AppNavigationState(AppScreen.HOME_ASSISTANT)
            else when (val phase = session.phase) {
                SessionPhase.Disconnected -> AppNavigationState(AppScreen.INITIAL)

                // The pre-connection screen shows the connection progress.
                SessionPhase.Connecting -> AppNavigationState(
                    if (BackgroundConnection.active) AppScreen.INITIAL else AppScreen.PRE_CONNECTION
                )

                is SessionPhase.Ready -> AppNavigationState(
                    when {
                        session.isRunningActions -> AppScreen.RUN_ACTIONS
                        else -> when (phase.entryReason) {
                            EntryReason.NORMAL -> AppScreen.MAIN_NAVIGATION
                            EntryReason.CONTENT_SELECTOR -> AppScreen.CONTENT_SELECTOR
                            EntryReason.RUN_ACTIONS -> AppScreen.RUN_ACTIONS
                            // Stays on the first page; see OpenOnConnect for when it moves on.
                            EntryReason.BACKGROUND -> AppScreen.INITIAL
                        }
                    }
                )

                // A connection that ended normally returns to the start screen, which re-checks for
                // paired watches. A failed attempt or an error stays on the pre-connection screen.
                is SessionPhase.Closed -> when {
                    // A background connection never changes the screen, and its refusals are not errors
                    BackgroundConnection.active -> AppNavigationState(AppScreen.INITIAL)
                    else -> AppNavigationState(
                        screen = if (!phase.isError && session.button != null) {
                            AppScreen.INITIAL
                        } else {
                            AppScreen.PRE_CONNECTION
                        },
                        errorMessage = phase.reason.takeIf { phase.isError && it.isNotBlank() }
                    )
                }
            }
    }
}