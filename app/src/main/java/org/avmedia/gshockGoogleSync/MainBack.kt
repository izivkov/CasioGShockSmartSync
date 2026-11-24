package org.avmedia.gshockGoogleSync

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** True when Back on [route] opens the watch list: on the Time tab (the start tab), where Back used to close the app. */
internal fun backOpensWatchList(route: String?): Boolean = route == null || route == Screens.Time.route

/** Back on the Time tab opens the watch list; on any other tab the navigation goes back to Time as before. */
@Composable
internal fun TimeTabBack(route: String?, onOpenWatchList: () -> Unit) {
    BackHandler(enabled = backOpensWatchList(route), onBack = onOpenWatchList)
}
