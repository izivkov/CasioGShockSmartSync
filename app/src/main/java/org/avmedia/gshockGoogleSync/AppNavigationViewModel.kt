package org.avmedia.gshockGoogleSync

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.pairing.CompanionDeviceCoordinator
import org.avmedia.gshockGoogleSync.session.SessionManager

@HiltViewModel
class AppNavigationViewModel @Inject constructor(
    screenState: AppScreenState,
    private val sessionManager: SessionManager,
    val repository: GShockRepository,
    val companionDeviceCoordinator: CompanionDeviceCoordinator
) : ViewModel() {
    val state = screenState.state
    val sessionState = sessionManager.sessionState

    /** The user left the entry screen (e.g. unlocked the cover). */
    fun showMainNavigation() {
        sessionManager.activeSession?.enterMainNavigation()
    }
}