package org.avmedia.gshockGoogleSync.homeassistant

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
    /** False when the connected watch cannot show notifications; true while not known. */
    val notificationsSupported: Boolean = true,
    /** "HH:mm" the raw settings switch turns itself off, null while off. */
    val rawUntil: String? = null,
    /** The watch's name without "CASIO ", offered before the first registration; empty when no watch is connected. */
    val suggestedName: String = "",
    /** The name confirmed for this watch before its first registration, null while not confirmed. */
    val confirmedName: String? = null,
    /** This watch's name in Home Assistant (its registration); empty before it registered. */
    val name: String = "",
    /** This watch's model as it registers (typed for it, else from its registration); empty = from the watch name. */
    val model: String = "",
    /** The other watches registered in Home Assistant, by their names there. */
    val others: List<String> = emptyList(),
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
    val dash: HaDashUi = HaDashUi(),
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
    val onRawSwitch: (Boolean) -> Unit = {},
    val onCreateDashboard: () -> Unit = {},
    val onCopyYaml: () -> Unit = {},
    /** Confirms the name (and model) the watch registers with; only before its first registration. */
    val onConfirmName: (name: String, model: String) -> Unit = { _, _ -> },
)
