package org.avmedia.gshockGoogleSync.ui.time

import AppText
import android.app.AlertDialog
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Battery as the DW-H5600 shows it: 0..4 bars. The watch only reports a raw level step (0x28
 * byte 1), and how steps map to bars is not documented, so the mapping is calibrated by the user
 * (tap the battery: "what does the watch show?"). Known so far (2026-11-17): level 5 = 3 bars.
 * Levels not calibrated yet are estimated from the nearest calibrated one, one bar per step.
 */
object BatteryBars {
    const val MAX_BARS = 4
    private const val PREFS = org.avmedia.gshockGoogleSync.h5600.H5600Prefs.NAME
    private const val KEY = "battery_bars_by_level" // "5:3,6:4"
    private val DEFAULT = mapOf(5 to 3)

    fun calibration(context: Context): Map<Int, Int> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.split(',')?.mapNotNull { p -> p.split(':').takeIf { it.size == 2 }?.let { (l, b) -> l.toIntOrNull()?.let { it to (b.toIntOrNull() ?: return@mapNotNull null) } } }
            ?.toMap()?.takeIf { it.isNotEmpty() } ?: DEFAULT

    fun save(context: Context, level: Int, bars: Int) {
        val map = calibration(context) + (level to bars.coerceIn(0, MAX_BARS))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, map.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }

    /** (bars, calibrated?) for a raw level. Pure, for tests. */
    fun barsFor(level: Int, calibration: Map<Int, Int>): Pair<Int, Boolean> {
        calibration[level]?.let { return it to true }
        val nearest = calibration.keys.minByOrNull { Math.abs(it - level) } ?: return (level - 2).coerceIn(0, MAX_BARS) to false
        return (calibration.getValue(nearest) + (level - nearest)).coerceIn(0, MAX_BARS) to false
    }
}

/** 4-bar battery for the DW-H5600; tap to tell the app what the watch shows for this level. */
@Composable
fun BatteryBarsView(rawLevel: Int) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) } // recompute after a calibration
    val (bars, sure) = remember(rawLevel, version) { BatteryBars.barsFor(rawLevel, BatteryBars.calibration(context)) }
    val fill = if (sure) Color(0xFF4CAF50) else Color(0xFF4CAF50).copy(alpha = 0.5f)
    val frame = Color(0xFF9E9E9E)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {
            val options = (BatteryBars.MAX_BARS downTo 0).map { "$it of ${BatteryBars.MAX_BARS} bars" }
            AlertDialog.Builder(context)
                .setTitle("Watch battery level $rawLevel: what does the watch show?")
                .setSingleChoiceItems(options.toTypedArray(), BatteryBars.MAX_BARS - bars) { d, i ->
                    BatteryBars.save(context, rawLevel, BatteryBars.MAX_BARS - i)
                    version++
                    d.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        },
    ) {
        Canvas(modifier = Modifier.width(60.dp).height(20.dp)) {
            val stroke = size.height * 0.08f
            val cap = size.width * 0.08f
            val bodyRight = size.width - cap
            drawRoundRect(frame, Offset(bodyRight, size.height * 0.25f), Size(cap, size.height * 0.5f), CornerRadius(size.height * 0.12f))
            drawRoundRect(frame, Offset(stroke / 2, stroke / 2), Size(bodyRight - stroke, size.height - stroke), CornerRadius(size.height * 0.12f), style = Stroke(stroke))
            val gap = stroke * 1.5f
            val inner = bodyRight - stroke * 2 - gap * (BatteryBars.MAX_BARS + 1)
            val w = inner / BatteryBars.MAX_BARS
            for (i in 0 until bars) {
                drawRect(fill, Offset(stroke + gap + i * (w + gap), stroke + gap), Size(w, size.height - 2 * (stroke + gap)))
            }
        }
        // No "3/4" text: the bars say it. An estimated level (not calibrated yet) is drawn lighter.
    }
}
