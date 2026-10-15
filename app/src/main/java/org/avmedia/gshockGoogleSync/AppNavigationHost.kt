package org.avmedia.gshockGoogleSync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.ui.others.CoverScreen
import org.avmedia.gshockGoogleSync.ui.others.PreConnectionScreen
import org.avmedia.gshockGoogleSync.ui.others.RunActionsScreen
import org.avmedia.gshockGoogleSync.ui.others.RunFindPhoneScreen

@Composable
fun AppNavigationHost(
    contentPadding: PaddingValues,
    viewModel: AppNavigationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val companionDeviceCoordinator = viewModel.companionDeviceCoordinator

    LaunchedEffect(state.screen) {
        if (state.screen == AppScreen.INITIAL) {
            companionDeviceCoordinator.checkPairedDevicesOrNotify()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        when (state.screen) {
            AppScreen.INITIAL, AppScreen.PRE_CONNECTION -> PreConnectionScreen()
            AppScreen.CONTENT_SELECTOR -> ContentSelector(viewModel)
            AppScreen.MAIN_NAVIGATION ->
                BottomNavigationBarWithPermissions(repository = viewModel.repository)
            AppScreen.RUN_ACTIONS -> RunActionsScreen()
            AppScreen.ERROR -> PreConnectionScreen()
        }
    }
}

@Composable
private fun ContentSelector(viewModel: AppNavigationViewModel) {
    val repository = viewModel.repository
    when {
        repository.isAlwaysConnectedConnectionPressed() -> {
            CoverScreen(
                onUnlock = viewModel::showMainNavigation,
                isConnected = repository.isConnected()
            )
        }

        repository.isActionButtonPressed() || repository.isAutoTimeStarted() -> RunActionsScreen()
        repository.isFindPhoneButtonPressed() -> RunFindPhoneScreen()
        else -> BottomNavigationBarWithPermissions(repository = repository)
    }
}