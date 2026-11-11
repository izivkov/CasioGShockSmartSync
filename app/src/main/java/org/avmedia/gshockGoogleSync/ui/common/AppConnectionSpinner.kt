package org.avmedia.gshockGoogleSync.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import org.avmedia.gshockGoogleSync.di.SessionManagerEntryPoint
import org.avmedia.gshockGoogleSync.session.SessionPhase

/** Shows a spinner while the watch session is [SessionPhase.Connecting]. */
@Composable
fun AppConnectionSpinner(modifier: Modifier = Modifier) {
    val appContext = LocalContext.current.applicationContext
    val sessionManager = remember {
        EntryPointAccessors.fromApplication(appContext, SessionManagerEntryPoint::class.java)
            .sessionManager()
    }
    val sessionState by sessionManager.sessionState.collectAsState()

    if (sessionState.phase is SessionPhase.Connecting) {
        AppCard(
            modifier = modifier,
            padding = 8.dp,
            containerColor = Color.Transparent,
            elevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}
