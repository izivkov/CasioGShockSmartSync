package org.avmedia.gshockGoogleSync

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.pairing.DeviceAssociationManager

@HiltViewModel
class AppNavigationViewModel @Inject constructor(
    private val screenState: AppScreenState,
    val repository: GShockRepository,
    val deviceAssociationManager: DeviceAssociationManager
) : ViewModel() {
    val state = screenState.state

    fun showMainNavigation() {
        screenState.showMainNavigation()
    }
}