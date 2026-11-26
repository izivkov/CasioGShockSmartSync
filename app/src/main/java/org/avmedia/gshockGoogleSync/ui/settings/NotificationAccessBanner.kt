package org.avmedia.gshockGoogleSync.ui.settings

import AppText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.avmedia.gshockGoogleSync.notifications.NotificationAccess
import org.avmedia.gshockGoogleSync.ui.common.AppButton
import org.avmedia.gshockGoogleSync.ui.common.AppCard

/** On the watch list: Android's notification access is off while phone notifications are on (after a re-pair, often). */
@Composable
fun NotificationAccessBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val warning = remember { NotificationAccess.warning(context) } ?: return
    NotificationAccessBannerContent(warning, { NotificationAccess.openSettings(context) }, modifier)
}

@Composable
fun NotificationAccessBannerContent(warning: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppText(warning, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            AppButton("Open notification access", onOpen, Modifier.fillMaxWidth())
        }
    }
}
