package org.avmedia.gshockGoogleSync.ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * ActionsViewModel is application-scoped (see its class doc), so the UI can no longer obtain
 * it with hiltViewModel() -- that would build an Activity-scoped instance and the enabled
 * flags shown on screen would drift from the ones the background runner executes.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ActionsEntryPoint {
    fun actionsViewModel(): ActionsViewModel
}

/** Returns the single process-wide ActionsViewModel. */
@Composable
fun rememberActionsViewModel(): ActionsViewModel {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, ActionsEntryPoint::class.java)
                .actionsViewModel()
    }
}
