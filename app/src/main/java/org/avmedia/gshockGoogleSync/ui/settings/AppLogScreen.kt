package org.avmedia.gshockGoogleSync.ui.settings

import AppText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.avmedia.gshockapi.log.AppLog
import org.avmedia.gshockapi.log.LogLine
import org.avmedia.gshockapi.log.LogSource

/** The lines to show: newest first, only [filter]'s source when one is chosen. */
fun visibleLogLines(raw: List<String>, filter: LogSource?): List<LogLine> =
    raw.asReversed().map(AppLog::parse).filter { filter == null || it.source == filter }

/** Settings > App log: the whole app log, a filter by source, Share and Clear. */
@Composable
fun AppLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var raw by remember { mutableStateOf<List<String>>(emptyList()) }
    var filter by remember { mutableStateOf<LogSource?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) { raw = withContext(Dispatchers.IO) { AppLog.readLines() } }
    AppLogContent(
        lines = visibleLogLines(raw, filter),
        filter = filter,
        onFilter = { filter = it },
        onShare = { HealthLogShare.share(context) },
        onClear = { AppLog.clear(); reload++ },
        onBack = onBack,
    )
}

@Composable
internal fun AppLogContent(
    lines: List<LogLine>,
    filter: LogSource?,
    onFilter: (LogSource?) -> Unit,
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
                }
                if (lines.isEmpty()) {
                    AppText(text = "Nothing logged yet.", modifier = Modifier.weight(1f).padding(16.dp))
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        items(lines) { l ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                AppText(text = listOfNotNull(l.time.takeIf { it.isNotEmpty() }, l.source?.label).joinToString("  "), fontSize = 11.sp)
                                AppText(text = l.text, fontSize = 14.sp)
                            }
                        }
                    }
                }
                ButtonsRow(buttons = listOf(ButtonData("Share", onShare), ButtonData("Clear", onClear, enabled = lines.isNotEmpty()), ButtonData("Back", onBack)))
            }
        }
    }
}
