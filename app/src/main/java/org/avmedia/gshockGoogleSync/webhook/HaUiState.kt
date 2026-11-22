package org.avmedia.gshockGoogleSync.webhook

/** The connection check: not run, running, good or failed ([detail] is for the "Details" expander). */
sealed interface HaCheckUi {
    data object Idle : HaCheckUi
    data object Checking : HaCheckUi
    data class Ok(val message: String) : HaCheckUi
    data class Error(val message: String, val detail: String = "") : HaCheckUi
}

/** "Find on my network": [found] fills while searching; [Done] with nothing found is the empty state. */
sealed interface HaFindUi {
    data object Idle : HaFindUi
    data class Searching(val found: List<HaFound>) : HaFindUi
    data class Done(val found: List<HaFound>) : HaFindUi
    data object Unavailable : HaFindUi
}

/** The "This watch" card. [lastText] is the technical last result, shown only under Details. */
data class HaWatchUi(
    val registered: Boolean = false,
    val firmware: String? = null,
    val lastUpdate: String? = null,
    val failed: Boolean = false,
    val lastText: String = "",
    val deviceUrl: String = "",
    val lookingUpDevice: Boolean = false,
)

data class HaGroupUi(val group: HaGroup, val on: Boolean, val rows: List<Pair<String, String>>)

/** The "What is sent" card. [sending] = a job waits to go out. */
data class HaSentUi(val groups: List<HaGroupUi> = emptyList(), val sending: Boolean = false, val failed: Boolean = false)

/** The "Watch button" card. */
data class HaActionsUi(
    val items: List<HaAction> = emptyList(),
    val chosen: HaAction? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val lastRun: String = "",
)

data class HaUi(
    val form: HaConfig = HaConfig(),
    val check: HaCheckUi = HaCheckUi.Idle,
    val find: HaFindUi = HaFindUi.Idle,
    val watch: HaWatchUi = HaWatchUi(),
    val sent: HaSentUi = HaSentUi(),
    val actions: HaActionsUi = HaActionsUi(),
) {
    val saved: Boolean get() = form.baseUrl.isNotBlank() && form.token.isNotBlank()
}

/** What the screen can ask for. All default to nothing, for the screenshot tests. */
class HaEvents(
    val onBack: () -> Unit = {},
    val onSave: (url: String, token: String, deviceName: String, model: String) -> Unit = { _, _, _, _ -> },
    val onForget: () -> Unit = {},
    val onFind: () -> Unit = {},
    val onGroup: (HaGroup, Boolean) -> Unit = { _, _ -> },
    val onChoose: (HaAction?) -> Unit = {},
    val onRefreshActions: () -> Unit = {},
)
