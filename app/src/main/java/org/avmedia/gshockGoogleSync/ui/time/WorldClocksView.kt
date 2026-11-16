package org.avmedia.gshockGoogleSync.ui.time

import AppText
import AppTextLarge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Phone-side world clocks on the Time tab (not stored on the watch). */
private val WORLD_CLOCKS = listOf(
    "Minsk" to ZoneId.of("Europe/Minsk"),
    "Los Angeles" to ZoneId.of("America/Los_Angeles"),
)

@Composable
fun WorldClocksView(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = ZonedDateTime.now()
        }
    }
    // Subscribe to the app-wide format so a 12/24h change recomposes this card immediately.
    val is24h by org.avmedia.gshockGoogleSync.utils.AppTimeFormat.is24h.collectAsState()
    val pattern = if (is24h) "HH:mm" else "h:mm a"
    val fmt = DateTimeFormatter.ofPattern(pattern)

    AppCard(modifier = modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            WORLD_CLOCKS.forEach { (city, zone) ->
                val there = now.withZoneSameInstant(zone)
                val dayDiff = ChronoUnit.DAYS.between(now.toLocalDate(), there.toLocalDate())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        AppText(text = city)
                        AppText(text = there.format(DateTimeFormatter.ofPattern("EEE, d MMM")), fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (dayDiff != 0L) {
                            AppText(
                                text = if (dayDiff > 0) "+1 day  " else "−1 day  ",
                                fontSize = 13.sp,
                            )
                        }
                        AppTextLarge(text = there.format(fmt))
                    }
                }
            }
        }
    }
}
