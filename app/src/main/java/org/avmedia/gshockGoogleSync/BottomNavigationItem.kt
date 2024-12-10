package org.avmedia.gshockGoogleSync

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import org.avmedia.translateapi.DynamicResourceApi

data class BottomNavigationItem(
    val label: String = "",
    val icon: ImageVector = Icons.Filled.Home,
    val route: String = "",
) {
    @Composable
    fun bottomNavigationItems(): List<BottomNavigationItem> {
        return listOf(
            BottomNavigationItem(
                label = DynamicResourceApi.getApi().stringResource(LocalContext.current, R.string.time),
                icon = ImageVector.vectorResource(id = R.drawable.time), // Local icon
                route = Screens.Time.route,
            ),
            BottomNavigationItem(
                label = DynamicResourceApi.getApi().stringResource(LocalContext.current, R.string.alarms),
                icon = ImageVector.vectorResource(id = R.drawable.ic_alarm_black_24dp), // Local icon
                route = Screens.Alarms.route
            ),
            BottomNavigationItem(
                label = DynamicResourceApi.getApi().stringResource(LocalContext.current, R.string.events),
                icon = ImageVector.vectorResource(id = R.drawable.ic_event_black_24dp), // Local icon
                route = Screens.Events.route
            ),
            BottomNavigationItem(
                label = DynamicResourceApi.getApi().stringResource(LocalContext.current, R.string.actions),
                icon = ImageVector.vectorResource(id = R.drawable.generic_action_item), // Local icon
                route = Screens.Actions.route
            ),
            BottomNavigationItem(
                label = DynamicResourceApi.getApi().stringResource(LocalContext.current, R.string.settings),
                icon = ImageVector.vectorResource(id = R.drawable.ic_settings), // Local icon
                route = Screens.Settings.route
            ),
        )
    }
}