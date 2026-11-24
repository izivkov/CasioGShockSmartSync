package org.avmedia.gshockGoogleSync.ui.settings

import AppText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ButtonData
import org.avmedia.gshockGoogleSync.ui.common.ButtonsRow
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import org.avmedia.gshockGoogleSync.ui.health.HealthLogShare
import org.avmedia.gshockGoogleSync.log.AppLog
import org.avmedia.gshockGoogleSync.log.LogLine
import org.avmedia.gshockGoogleSync.log.LogSource

/** The lines to show: newest first, only [filter]'s source when one is chosen (all lines, raw frames included). */
fun visibleLogLines(raw: List<String>, filter: LogSource?): List<LogLine> =
    AppLogModel { raw }.apply { load() }.page(filter, showRaw = true, limit = Int.MAX_VALUE).map { it.line }

/** Settings > App log: the whole app log, newest first in pages, a filter by source, raw frames on demand, Share and Clear. */
@Composable
fun AppLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val model = remember { AppLogModel() }
    var loaded by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf<LogSource?>(null) }
    var showRaw by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf(AppLogModel.PAGE) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) { withContext(Dispatchers.Default) { model.load() }; loaded++ }
    var lines by remember { mutableStateOf<List<ShownLine>>(emptyList()) }
    LaunchedEffect(loaded, filter, showRaw, limit) { lines = withContext(Dispatchers.Default) { model.page(filter, showRaw, limit) } }
    AppLogContent(
        shown = lines,
        filter = filter,
        onFilter = { filter = it; limit = AppLogModel.PAGE },
        showRaw = showRaw,
        onShowRaw = { showRaw = it; limit = AppLogModel.PAGE },
        onMore = { if (lines.size >= limit) limit += AppLogModel.PAGE },
        onShare = { HealthLogShare.share(context) },
        onClear = { AppLog.clear(); reload++ },
        onBack = onBack,
    )
}

/** For previews and screenshots: plain lines, ids by position. */
@Composable
internal fun AppLogContent(
    lines: List<LogLine>,
    filter: LogSource?,
    onFilter: (LogSource?) -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) = AppLogContent(
    lines.mapIndexed { i, l -> ShownLine(i, l, AppLogModel.isRawFrame(l)) }, filter, onFilter, true, {}, {}, onShare, onClear, onBack,
)

@Composable
internal fun AppLogContent(
    shown: List<ShownLine>,
    filter: LogSource?,
    onFilter: (LogSource?) -> Unit,
    showRaw: Boolean,
    onShowRaw: (Boolean) -> Unit,
    onMore: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle("App log", Modifier)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                    FilterChip(selected = filter == null, onClick = { onFilter(null) }, label = { Text("All") }, modifier = Modifier.padding(end = 6.dp))
                    LogSource.entries.forEach { s ->
                        FilterChip(selected = filter == s, onClick = { onFilter(s) }, label = { Text(s.label) }, modifier = Modifier.padding(end = 6.dp))
                    }
                    FilterChip(selected = showRaw, onClick = { onShowRaw(!showRaw) }, label = { Text("Raw frames") }, modifier = Modifier.padding(end = 6.dp))
                }
                if (shown.isEmpty()) {
                    AppText(text = "Nothing logged yet.", modifier = Modifier.weight(1f).padding(16.dp))
                } else {
                    val listState = rememberLazyListState()
                    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 50 } }
                    LaunchedEffect(nearEnd, shown.size) { if (nearEnd) onMore() }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        items(shown, key = { it.id }) { s -> LogRow(s.line) }
                    }
                }
                ButtonsRow(buttons = listOf(ButtonData("Share", onShare), ButtonData("Clear", onClear, enabled = shown.isNotEmpty()), ButtonData("Back", onBack)))
            }
        }
    }
}

@Composable
private fun LogRow(l: LogLine) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        AppText(text = listOfNotNull(l.time.takeIf { it.isNotEmpty() }, l.source?.label).joinToString("  "), fontSize = 11.sp)
        AppText(text = l.text, fontSize = 14.sp)
    }
}
