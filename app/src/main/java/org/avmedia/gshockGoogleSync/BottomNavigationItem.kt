package org.avmedia.gshockGoogleSync

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.translateapi.ResourceLocaleKey

data class BottomNavigationItem(
    val label: String = "",
    val icon: ImageVector = Icons.Filled.Home,
    val route: String = "",
    val translateApi: TranslateRepository,
) {
    @Composable
    fun bottomNavigationItems(): List<BottomNavigationItem> {

        translateApi.addOverwrites(
            arrayOf(
                ResourceLocaleKey(R.string.settings, java.util.Locale("ca")) to "Config.",
                ResourceLocaleKey(R.string.time, java.util.Locale("ca")) to "Hora",
            )
        )

        return listOf(
            BottomNavigationItem(
                label = translateApi.stringResource(LocalContext.current, R.string.time),
                icon = ImageVector.vectorResource(id = R.drawable.time), // Local icon
                route = Screens.Time.route,
                translateApi
            ),
            BottomNavigationItem(
                label = translateApi.stringResource(LocalContext.current, R.string.alarms),
                icon = ImageVector.vectorResource(id = R.drawable.ic_alarm_black_24dp), // Local icon
                route = Screens.Alarms.route,
                translateApi
            ),
            BottomNavigationItem(
                label = translateApi.stringResource(LocalContext.current, R.string.events),
                icon = ImageVector.vectorResource(id = R.drawable.ic_event_black_24dp), // Local icon
                route = Screens.Events.route,
                translateApi
            ),
            BottomNavigationItem(
                label = translateApi.stringResource(LocalContext.current, R.string.actions),
                icon = ImageVector.vectorResource(id = R.drawable.generic_action_item), // Local icon
                route = Screens.Actions.route,
                translateApi
            ),
            BottomNavigationItem(
                label = translateApi.stringResource(LocalContext.current, R.string.settings),
                icon = ImageVector.vectorResource(id = R.drawable.ic_settings), // Local icon
                route = Screens.Settings.route,
                translateApi
            ),
        )
    }
}