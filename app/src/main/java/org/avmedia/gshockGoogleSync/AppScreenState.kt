package org.avmedia.gshockGoogleSync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import javax.inject.Inject
import javax.inject.Singleton

enum class AppScreen {
    INITIAL,
    PRE_CONNECTION,
    CONTENT_SELECTOR,
    MAIN_NAVIGATION,
    RUN_ACTIONS,
    ERROR
}

data class AppNavigationState(
    val screen: AppScreen = AppScreen.INITIAL,
    val errorMessage: String? = null
)

@Singleton
class AppScreenState @Inject constructor() : IScreenManager {
    private val mutableState = MutableStateFlow(AppNavigationState())
    val state: StateFlow<AppNavigationState> = mutableState.asStateFlow()

    override fun showContentSelector(repository: GShockRepository) {
        updateScreen(AppScreen.CONTENT_SELECTOR)
    }

    override fun showRunActionsScreen() {
        updateScreen(AppScreen.RUN_ACTIONS)
    }

    override fun showPreConnectionScreen() {
        updateScreen(AppScreen.PRE_CONNECTION)
    }

    override fun showInitialScreen() {
        updateScreen(AppScreen.INITIAL)
    }

    override fun showError(message: String) {
        mutableState.update { it.copy(screen = AppScreen.ERROR, errorMessage = message) }
    }

    fun showMainNavigation() {
        updateScreen(AppScreen.MAIN_NAVIGATION)
    }

    private fun updateScreen(screen: AppScreen) {
        mutableState.update { it.copy(screen = screen) }
    }
}