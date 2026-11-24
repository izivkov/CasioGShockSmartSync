package org.avmedia.gshockGoogleSync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import org.avmedia.gshockGoogleSync.session.WatchList
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

    LinkBarLayout(sessionState.link, Modifier.fillMaxSize().padding(contentPadding)) {
        when (state.screen) {
            AppScreen.INITIAL, AppScreen.PRE_CONNECTION, AppScreen.ERROR -> PreConnectionScreen()
            AppScreen.CONTENT_SELECTOR -> ContentSelector(viewModel)
            AppScreen.MAIN_NAVIGATION -> MainScreens(viewModel)
            AppScreen.RUN_ACTIONS -> RunActionsScreen()
            AppScreen.HOME_ASSISTANT -> {
                androidx.activity.compose.BackHandler(onBack = viewModel::closeHa)
                HaScreen(viewModel = hiltViewModel(), onBack = viewModel::closeHa)
            }
            AppScreen.WATCH_LIST -> {
                androidx.activity.compose.BackHandler(onBack = viewModel::closeWatchList)
                // Back only while a watch is still connected; with none it is the first page (Back still closes it).
                val connected = sessionState.phase is org.avmedia.gshockGoogleSync.session.SessionPhase.Ready
                PreConnectionScreen(
                    onBack = if (WatchList.showsBack(openedFromConnectedScreens = true, watchConnected = connected)) viewModel::closeWatchList else null,
                    watchSwitch = viewModel.watchSwitch,
                )
            }
        }
    }
}

@Composable
private fun MainScreens(viewModel: AppNavigationViewModel) =
    BottomNavigationBarWithPermissions(
        repository = viewModel.repository,
        onOpenHa = viewModel::openHa,
        onOpenWatchList = viewModel::openWatchList,
        keepsLink = viewModel::keepsLink,
    )

/**
 * The screen with the link bar on top while the watch's link is away. The bar is its own row and pushes the
 * screen down (it takes the status bar inset), so it never covers the screen's title.
 */
@Composable
internal fun LinkBarLayout(link: LinkState, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier) {
        LinkBar(link, Modifier.align(Alignment.CenterHorizontally))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(if (link != LinkState.UP) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)
        ) { content() }
    }
}

@Composable
private fun ContentSelector(viewModel: AppNavigationViewModel) {
    val sessionState by viewModel.sessionState.collectAsState()
    when (sessionState.button) {
        ConnectionButton.FROM_ACTION, ConnectionButton.AUTOMATIC_SYNC -> RunActionsScreen()
        ConnectionButton.FIND -> RunFindPhoneScreen()
        ConnectionButton.ALWAYS_CONNECTED, ConnectionButton.NORMAL, null -> MainScreens(viewModel)
    }
}

/** A small bar while the watch's link is away: the screen and its data stay as they are. */
@Composable
internal fun LinkBar(link: LinkState, modifier: Modifier = Modifier) {
    if (link == LinkState.UP) return
    androidx.compose.material3.Surface(
        // Edge to edge: below the status bar, not under it.
        modifier = modifier.statusBarsPadding(),
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
