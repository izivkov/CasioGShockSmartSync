package org.avmedia.gshockGoogleSync.h5600

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle

/** "Custom" tab: the DW-H5600 additions (sound mode, watch face, sun/weather, notification apps). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomScreen(vm: H5600ViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle("Custom", Modifier)
                // Swipe down: re-read sound mode, watch face and the raw 0x42 packet from the watch.
                PullToRefreshBox(
                    isRefreshing = s.busy,
                    onRefresh = { vm.refresh() },
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        H5600Card(vm)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
