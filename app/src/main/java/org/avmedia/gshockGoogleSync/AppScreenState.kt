package org.avmedia.gshockGoogleSync

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
import org.avmedia.gshockGoogleSync.webhook.WebhookScreenState
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.avmedia.gshockGoogleSync.session.OpenOnConnect
import javax.inject.Inject
import javax.inject.Singleton

enum class AppScreen {
    INITIAL,
    PRE_CONNECTION,
    CONTENT_SELECTOR,
    MAIN_NAVIGATION,
    RUN_ACTIONS,
    ERROR,

    /** The Webhooks screen: shown whatever the watch connection does. */
    WEBHOOKS,

    /** The Home Assistant screen: same rule as [WEBHOOKS]. */
    HOME_ASSISTANT
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
    webhookScreenState: WebhookScreenState,
    @ApplicationContext appContext: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val state: StateFlow<AppNavigationState> =
        combine(
            sessionManager.sessionState, webhookScreenState.open, webhookScreenState.haOpen, AppForeground.inFront
        ) { session, webhooksOpen, haOpen, inFront ->
            navigationFor(session, webhooksOpen, haOpen, inFront)
        }
            .distinctUntilChanged()
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                navigationFor(
                    sessionManager.sessionState.value, webhookScreenState.open.value,
                    webhookScreenState.haOpen.value, AppForeground.inFront.value
                )
            )

    init {
        // An always-connected watch that connects, or is connected, while the app is in front on the first
        // page opens the main screen when its "Open when the watch connects" switch is on, or when the
        // person tapped its name. Otherwise the app stays where it is (sync, webhooks, HA run unchanged).
        scope.launch {
            combine(
                sessionManager.sessionState, AppForeground.inFront, webhookScreenState.open,
                webhookScreenState.haOpen, OpenOnConnect.waiting
            ) { session, inFront, webhooksOpen, haOpen, waiting ->
                val address = session.watch.address
                OpenOnConnect.shouldOpen(
                    session, inFront, !webhooksOpen && !haOpen,
                    OpenOnConnect.isOn(appContext, address), waiting
                )
            }.collect { latch ->
                if (latch) sessionManager.activeSession?.let {
                    OpenOnConnect.consumed(it.state.value.watch.address)
                    it.enterMainNavigation()
                }
            }
        }
    }

    companion object {
        internal fun navigationFor(
            session: SessionState,
            webhooksOpen: Boolean = false,
            haOpen: Boolean = false,
            appInForeground: Boolean = false
        ): AppNavigationState =
            if (haOpen) AppNavigationState(AppScreen.HOME_ASSISTANT)
            else if (webhooksOpen) AppNavigationState(AppScreen.WEBHOOKS)
            else when (val phase = session.phase) {
                SessionPhase.Disconnected -> AppNavigationState(AppScreen.INITIAL)

                // The pre-connection screen shows the connection progress.
                SessionPhase.Connecting -> AppNavigationState(AppScreen.PRE_CONNECTION)

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
                is SessionPhase.Closed -> AppNavigationState(
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