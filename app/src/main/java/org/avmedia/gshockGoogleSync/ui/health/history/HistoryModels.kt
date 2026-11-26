package org.avmedia.gshockGoogleSync.ui.health.history

import java.time.LocalDate
import java.time.YearMonth
import org.avmedia.gshockGoogleSync.history.DayRow
import org.avmedia.gshockGoogleSync.history.HourRow
import org.avmedia.gshockGoogleSync.history.NightRow

enum class HistoryRange(val days: Int, val label: String) { WEEK(7, "Week"), MONTH(30, "Month") }

/** What the history views show. [selected] is the day or night opened from the calendar. */
data class HistoryUi(
    val days: List<DayRow> = emptyList(),
    val nights: List<NightRow> = emptyList(),
    val range: HistoryRange = HistoryRange.WEEK,
    val today: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.from(today),
    val selected: LocalDate? = null,
    val selectedHours: List<HourRow> = emptyList(),
    /** Plain words after an export, empty when nothing happened. */
    val exportNote: String = "",
)

class HistoryEvents(
    val onRange: (HistoryRange) -> Unit = {},
    val onMonth: (Int) -> Unit = {},
    val onSelect: (LocalDate?) -> Unit = {},
    val onExportCsv: () -> Unit = {},
    val onExportJson: () -> Unit = {},
)
