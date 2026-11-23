package org.avmedia.gshockGoogleSync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.session.ConnectionButton
import org.avmedia.gshockGoogleSync.session.LinkState
import org.avmedia.gshockGoogleSync.ui.others.PreConnectionScreen
import org.avmedia.gshockGoogleSync.ui.others.RunActionsScreen
import org.avmedia.gshockGoogleSync.ui.others.RunFindPhoneScreen
import org.avmedia.gshockGoogleSync.homeassistant.HaScreen

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

    val sessionState by viewModel.sessionState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        when (state.screen) {
            AppScreen.INITIAL, AppScreen.PRE_CONNECTION ->
                PreConnectionScreen(onOpenHa = viewModel::openHa)
            AppScreen.CONTENT_SELECTOR -> ContentSelector(viewModel)
            AppScreen.MAIN_NAVIGATION ->
                BottomNavigationBarWithPermissions(
                    repository = viewModel.repository,
                    onOpenHa = viewModel::openHa,
                    keepsLink = { viewModel.sessionState.value.keepsLink },
                )
            AppScreen.RUN_ACTIONS -> RunActionsScreen()
            AppScreen.ERROR -> PreConnectionScreen(onOpenHa = viewModel::openHa)
            AppScreen.HOME_ASSISTANT -> {
                androidx.activity.compose.BackHandler(onBack = viewModel::closeHa)
                HaScreen(viewModel = hiltViewModel(), onBack = viewModel::closeHa)
            }
        }
        LinkBar(sessionState.link, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun ContentSelector(viewModel: AppNavigationViewModel) {
    val repository = viewModel.repository
    val sessionState by viewModel.sessionState.collectAsState()
    when (sessionState.button) {
        ConnectionButton.FROM_ACTION, ConnectionButton.AUTOMATIC_SYNC -> RunActionsScreen()
        ConnectionButton.FIND -> RunFindPhoneScreen()
        ConnectionButton.ALWAYS_CONNECTED, ConnectionButton.NORMAL, null ->
            BottomNavigationBarWithPermissions(
                repository = repository,
                onOpenHa = viewModel::openHa,
                keepsLink = { viewModel.sessionState.value.keepsLink },
            )
    }
}

/** A small bar while the watch's link is away: the screen and its data stay as they are. */
@Composable
internal fun LinkBar(link: LinkState, modifier: Modifier = Modifier) {
    if (link == LinkState.UP) return
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
    ) {
        androidx.compose.material3.Text(
            text = androidx.compose.ui.res.stringResource(
                if (link == LinkState.RECONNECTING) R.string.link_reconnecting else R.string.link_lost
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        )
    }
}
