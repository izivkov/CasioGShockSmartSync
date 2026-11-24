package org.avmedia.gshockGoogleSync

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.pairing.CompanionDeviceCoordinator
import org.avmedia.gshockGoogleSync.session.SessionManager
import org.avmedia.gshockGoogleSync.session.WatchList
import org.avmedia.gshockGoogleSync.session.WatchSwitch
import org.avmedia.gshockGoogleSync.homeassistant.HaScreenState
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import timber.log.Timber

@HiltViewModel
class AppNavigationViewModel @Inject constructor(
    screenState: AppScreenState,
    private val sessionManager: SessionManager,
    val repository: GShockRepository,
    val companionDeviceCoordinator: CompanionDeviceCoordinator,
    private val haScreenState: HaScreenState,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {
    val state = screenState.state
    val sessionState = sessionManager.sessionState

    /** The user left the entry screen (e.g. unlocked the cover). */
    fun showMainNavigation() {
        sessionManager.activeSession?.enterMainNavigation()
    }

    fun openHa() = haScreenState.show()

    fun closeHa() = haScreenState.hide()

    /** The watch list from the connected screens; the link stays up. */
    fun openWatchList() = WatchList.show()

    /** Back from the watch list. A watch let go for a pairing that did not happen is connected again. */
    fun closeWatchList() {
        WatchList.hide()
        if (sessionManager.activeSession == null) {
            val last = LocalDataStorage.get(appContext, "LastDeviceAddress", "").orEmpty()
            if (last.isNotBlank()) companionDeviceCoordinator.connectNow(last)
        }
    }

    val watchSwitch = WatchSwitch(
        connectedAddress = {
            sessionManager.activeSession?.let { it.state.value.watch.address.ifBlank { it.device?.address.orEmpty() } }
        },
        disconnectCurrent = {
            Timber.i("Watch list: letting the connected watch go")
            sessionManager.activeSession?.close("Another watch chosen")
            runCatching { repository.disconnect() }.onFailure { Timber.e(it, "Disconnect failed") }
        },
        connect = companionDeviceCoordinator::connectNow,
    )
}

/** The bottom tab to show again when the main screens come back (e.g. Settings after Home Assistant). Used once. */
object MainTabReturn {
    @Volatile private var route: String? = null

    fun set(route: String?) { this.route = route }

    fun take(): String? = route.also { route = null }
}
