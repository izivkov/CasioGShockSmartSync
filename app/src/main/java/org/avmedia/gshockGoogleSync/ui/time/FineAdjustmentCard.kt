package org.avmedia.gshockGoogleSync.ui.time

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import org.avmedia.gshockGoogleSync.ui.settings.FineAdjustmentRow
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage

/** Fine Adjustment (moved here from Settings): saved and applied with a time sync ~1.5 s after the last change. */
@Composable
fun FineAdjustmentCard(modifier: Modifier = Modifier, timeModel: TimeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var value by remember { mutableIntStateOf(LocalDataStorage.getFineTimeAdjustment(context)) }
    var changedByUser by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!changedByUser) return@LaunchedEffect
        delay(1_500) // debounce while the user is still adjusting
        LocalDataStorage.setFineTimeAdjustment(context, value)
        timeModel.onAction(TimeAction.SendTimeToWatch) // location is re-sent after the sync
    }

    AppCard(modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        FineAdjustmentRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            value = value,
            onValueChange = { changedByUser = true; value = it },
        )
    }
}
