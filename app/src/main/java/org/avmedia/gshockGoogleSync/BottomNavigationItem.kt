package org.avmedia.gshockGoogleSync

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockapi.WatchInfo
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
                ResourceLocaleKey(R.string.time, java.util.Locale("ca")) to { "Hora" },
            )
        )

        val baseItems = mutableListOf(
            BottomNavigationItem(
                label = Utils.shortenString(
                    translateApi.stringResource(LocalContext.current, R.string.time), 7
                ),
                icon = ImageVector.vectorResource(id = R.drawable.time),
                route = Screens.Time.route,
                translateApi
            ),
            BottomNavigationItem(
                label = Utils.shortenString(
                    translateApi.stringResource(LocalContext.current, R.string.alarms), 7
                ),
                icon = ImageVector.vectorResource(id = R.drawable.ic_alarm_black_24dp),
                route = Screens.Alarms.route,
                translateApi
            ),
        )

        if (WatchInfo.hasReminders) {
            baseItems.add(
                BottomNavigationItem(
                    label = Utils.shortenString(
                        translateApi.stringResource(LocalContext.current, R.string.events), 7
                    ),
                    icon = ImageVector.vectorResource(id = R.drawable.ic_event_black_24dp),
                    route = Screens.Events.route,
                    translateApi
                )
            )
        }

        // Add remaining items
        baseItems.addAll(
            listOf(
                BottomNavigationItem(
                    label = Utils.shortenString(
                        translateApi.stringResource(LocalContext.current, R.string.actions), 7
                    ),
                    icon = ImageVector.vectorResource(id = R.drawable.generic_action_item),
                    route = Screens.Actions.route,
                    translateApi
                ),
                BottomNavigationItem(
                    label = Utils.shortenString(
                        translateApi.stringResource(LocalContext.current, R.string.settings), 7
                    ),
                    icon = ImageVector.vectorResource(id = R.drawable.ic_settings),
                    route = Screens.Settings.route,
                    translateApi
                )
            )
        )

        return baseItems
    }
}