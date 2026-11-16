package org.avmedia.gshockGoogleSync

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import org.avmedia.gshockGoogleSync.ui.common.LocalWatchFeatureManager
import org.avmedia.gshockGoogleSync.utils.Utils

data class BottomNavigationItem(
    val label: String = "",
    val icon: ImageVector = Icons.Filled.Home,
    val route: String = "",
) {
    @Composable
    fun bottomNavigationItems(): List<BottomNavigationItem> {
        val watchFeatureManager = LocalWatchFeatureManager.current

        val baseItems = mutableListOf(
            BottomNavigationItem(
                label = Utils.shortenString(
                    stringResource(R.string.time), 7
                ),
                icon = ImageVector.vectorResource(id = R.drawable.time),
                route = Screens.Time.route
            )
        )

        if (watchFeatureManager.isFeatureSupported("time.step_counter")) {
            baseItems.add(
                BottomNavigationItem(
                    label = Utils.shortenString(
                        stringResource(R.string.health), 7
                    ),
                    icon = Icons.Outlined.FavoriteBorder,
                    route = Screens.Health.route
                )
            )
        }

        baseItems.add(
            BottomNavigationItem(
                label = Utils.shortenString(
                    stringResource(R.string.alarms), 7
                ),
                icon = ImageVector.vectorResource(id = R.drawable.ic_alarm_black_24dp),
                route = Screens.Alarms.route,
            )
        )

        if (watchFeatureManager.isFeatureSupported("actions.reminders")) {
            baseItems.add(
                BottomNavigationItem(
                    label = Utils.shortenString(
                        stringResource(R.string.events), 7
                    ),
                    icon = ImageVector.vectorResource(id = R.drawable.ic_event_black_24dp),
                    route = Screens.Events.route
                )
            )
        }

        // Add remaining items. The Actions tab is hidden in this fork (not useful on the DW-H5600);
        // the background connection actions it configures (e.g. set time on auto-sync) still run
        // with their saved settings, and the route still exists.
        // "Custom" tab (fork): DW-H5600 additions, shown only for that watch family.
        if (org.avmedia.gshockapi.WatchInfo.model == org.avmedia.gshockapi.WatchInfo.WatchModel.DW_H5600) {
            baseItems.add(
                BottomNavigationItem(
                    label = "Custom",
                    icon = ImageVector.vectorResource(id = R.drawable.ic_tune_24),
                    route = Screens.Custom.route
                )
            )
        }
        baseItems.addAll(
            listOf(
                BottomNavigationItem(
                    label = Utils.shortenString(
                        stringResource(R.string.settings), 7
                    ),
                    icon = ImageVector.vectorResource(id = R.drawable.ic_settings),
                    route = Screens.Settings.route
                )
            )
        )

        return baseItems
    }
}
