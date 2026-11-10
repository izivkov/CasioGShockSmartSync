package org.avmedia.gshockGoogleSync

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.pairing.CompanionDeviceCoordinator
import org.avmedia.gshockGoogleSync.session.SessionManager

@HiltViewModel
class AppNavigationViewModel @Inject constructor(
    private val screenState: AppScreenState,
    val repository: GShockRepository,
    val companionDeviceCoordinator: CompanionDeviceCoordinator,
    sessionManager: SessionManager
) : ViewModel() {
    val state = screenState.state
    val sessionState = sessionManager.sessionState

    fun showMainNavigation() {
        screenState.showMainNavigation()
    }
}
