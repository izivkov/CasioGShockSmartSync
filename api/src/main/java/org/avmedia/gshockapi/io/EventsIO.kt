package org.avmedia.gshockapi.io

import CachedIO
import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Event
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.ble.GetSetMode
import org.avmedia.gshockapi.casio.CasioConstants
import org.avmedia.gshockapi.casio.ReminderMasks
import org.avmedia.gshockapi.utils.Utils
import org.avmedia.gshockapi.utils.Utils.getBooleanSafe
import org.avmedia.gshockapi.utils.Utils.getJSONArraySafe
import org.avmedia.gshockapi.utils.Utils.getJSONObjectSafe
import org.avmedia.gshockapi.utils.Utils.getStringSafe
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

// ============================================================================
// Pure Functional Core: Event/Reminder Encoding & Decoding
// ============================================================================

/**
 * Pure functional core for event/reminder processing.
 *
 * All methods are pure: no mutable state, no side effects.
 * Handles complex event encoding/decoding with dates, times, and repeat periods.
 */
@RequiresApi(Build.VERSION_CODES.O)
object EventsIOFunctional {
    /**
     * Pure decoder: Extracts reminder time data into JSON format.
     *
     * Protocol format for reminder time (command 0x31):
     * [0] - Time period byte (enable + repeat type flags)
     * [1-3] - Start date (year, month, day in hex-decimal format)
     * [4-6] - End date (year, month, day in hex-decimal format)
     * [7] - Days of week bitmask (for weekly reminders)
     * [8-9] - Padding (usually 0x00)
     *
     * Example: "31 05 01 01 01 01 01 01 01 02 00"
     * - 31 = command code
     * - 05 = enabled + weekly repeat
     * - 01 01 01 = start date (01/01/2001)
     * - 01 01 01 = end date (01/01/2001)
     * - 01 = days of week
     * - 02 00 = padding
     *
     * 0xFF in position [3] indicates end of reminders.
     */
    fun reminderTimeToJson(reminderStr: String): Result<JSONObject> = runCatching {
        val intArr = Utils.toIntArray(reminderStr).toIntArray()

        if (intArr.getOrNull(3) == 0xFF) {
            // 0xFF indicates end of reminders
            return@runCatching JSONObject("{\"end\": \"\"}")
        }

        val reminder = intArr.drop(2).toIntArray()
        val reminderJson = JSONObject()

        // Decode the time period byte (enable + repeat type)
        val timePeriod = decodeTimePeriod(reminder[0])
        reminderJson.put("enabled", timePeriod.first)
        reminderJson.put("repeatPeriod", timePeriod.second)

        // Decode date and day-of-week information
        val timeDetailMap = decodeTimeDetail(reminder)
        reminderJson.put("startDate", timeDetailMap["startDate"])
        reminderJson.put("endDate", timeDetailMap["endDate"])

        @Suppress("UNCHECKED_CAST")
        reminderJson.put(
            "daysOfWeek",
            convertArrayListToJSONArray(timeDetailMap["daysOfWeek"] as ArrayList<String>)
        )

        JSONObject("{\"time\": $reminderJson}")
    }

    /**
     * Pure decoder: Extracts reminder title data into JSON format.
     *
     * Protocol format for reminder title (command 0x30):
     * [0] = 0x30 (command code)
     * [1] = reminder number
     * [2:20] = ASCII title string (padded with 0x00)
     *
     * 0xFF in position [2] indicates end of reminders.
     *
     * Example: "30 01 4A 61 6E 75 61 72 79..." decodes to "January"
     */
    fun reminderTitleToJson(titleByte: String): Result<JSONObject> = runCatching {
        val intArr = Utils.toIntArray(titleByte)

        if (intArr.getOrNull(2) == 0xFF) {
            // 0xFF indicates end of reminders
            return@runCatching JSONObject("{\"end\": \"\"}")
        }

        val reminderJson = JSONObject()
        reminderJson.put("title", Utils.toAsciiString(titleByte, 2))

        JSONObject().put("title", reminderJson.getString("title"))
    }

    /**
     * Pure encoder: Converts JSON reminder time data to protocol bytes.
     *
     * Constructs the 10-byte time command from enabled/repeat period and date information.
     * Handles all repeat types: NEVER, WEEKLY, MONTHLY, YEARLY.
     * Encodes dates in hex-decimal format (e.g., year 22 = 0x22 = 2022).
     */
    fun reminderTimeFromJson(reminderJson: JSONObject): Result<IntArray> = runCatching {
        val enabled = reminderJson.getBooleanSafe("enabled")
        val repeatPeriod = reminderJson.getStringSafe("repeatPeriod")
        val startDate = reminderJson.getJSONObjectSafe("startDate")
        val endDate = reminderJson.getJSONObjectSafe("endDate")
        val daysOfWeek = reminderJson.getJSONArraySafe("daysOfWeek")

        var reminderCmd: IntArray = IntArray(0)

        reminderCmd += createTimePeriod(enabled, repeatPeriod)
        reminderCmd += createTimeDetail(repeatPeriod, startDate, endDate, daysOfWeek)

        reminderCmd
    }

    /**
     * Pure encoder: Converts JSON reminder title to protocol bytes.
     *
     * Pads title string to 18 bytes (standard title field size in watch protocol).
     * Shorter titles are null-padded; longer titles are truncated.
     */
    fun reminderTitleFromJson(reminderJson: JSONObject): Result<ByteArray> = runCatching {
        val titleStr: String = reminderJson.get("title") as String
        Utils.toByteArray(titleStr, 18)
    }

    // ========================================================================
    // Pure Helper Functions for Time Period Decoding
    // ========================================================================

    /**
     * Decodes the time period byte into enabled flag and repeat type.
     * Uses bit masks from ReminderMasks to extract flags.
     */
    private fun decodeTimePeriod(timePeriod: Int): Pair<Boolean, String> {
        val enabled = timePeriod and ReminderMasks.ENABLED_MASK == ReminderMasks.ENABLED_MASK

        val repeatPeriod = when {
            timePeriod and ReminderMasks.WEEKLY_MASK == ReminderMasks.WEEKLY_MASK -> "WEEKLY"
            timePeriod and ReminderMasks.MONTHLY_MASK == ReminderMasks.MONTHLY_MASK -> "MONTHLY"
            timePeriod and ReminderMasks.YEARLY_MASK == ReminderMasks.YEARLY_MASK -> "YEARLY"
            else -> "NEVER"
        }
        return Pair(enabled, repeatPeriod)
    }

    /**
     * Decodes time detail array into start date, end date, and days of week.
     *
     * Input array structure (after removing command code):
     * [0] - time period byte
     * [1-3] - start date (year, month, day)
     * [4-6] - end date (year, month, day)
     * [7] - days of week bitmask
     * [8-9] - padding
     */
    private fun decodeTimeDetail(timeDetail: IntArray): Map<String, Any> {
        val result = HashMap<String, Any>()

        // Extract start date (skip time period byte at [0], dates are at [1-3])
        result["startDate"] = decodeDate(timeDetail.drop(1).toIntArray())

        // Extract end date (skip to position [4])
        result["endDate"] = decodeDate(timeDetail.drop(4).toIntArray())

        // Extract days of week from position [7] using bitmask
        val dayOfWeek = timeDetail.getOrNull(7) ?: 0
        val daysOfWeek = ArrayList<String>()

        if (dayOfWeek and ReminderMasks.SUNDAY_MASK == ReminderMasks.SUNDAY_MASK) {
            daysOfWeek.add("SUNDAY")
        }
        if (dayOfWeek and ReminderMasks.MONDAY_MASK == ReminderMasks.MONDAY_MASK) {
            daysOfWeek.add("MONDAY")
        }
        if (dayOfWeek and ReminderMasks.TUESDAY_MASK == ReminderMasks.TUESDAY_MASK) {
            daysOfWeek.add("TUESDAY")
        }
        if (dayOfWeek and ReminderMasks.WEDNESDAY_MASK == ReminderMasks.WEDNESDAY_MASK) {
            daysOfWeek.add("WEDNESDAY")
        }
        if (dayOfWeek and ReminderMasks.THURSDAY_MASK == ReminderMasks.THURSDAY_MASK) {
            daysOfWeek.add("THURSDAY")
        }
        if (dayOfWeek and ReminderMasks.FRIDAY_MASK == ReminderMasks.FRIDAY_MASK) {
            daysOfWeek.add("FRIDAY")
        }
        if (dayOfWeek and ReminderMasks.SATURDAY_MASK == ReminderMasks.SATURDAY_MASK) {
            daysOfWeek.add("SATURDAY")
        }

        result["daysOfWeek"] = daysOfWeek
        return result
    }

    /**
     * Decodes a 3-byte date from BCD (binary-coded decimal) format.
     * Watch stores dates as hex values that look like decimals:
     * - Year 22 = 0x22 = 2022
     * - Month 03 = 0x03 = March
     * - Day 15 = 0x15 = 15th
     */
    private fun decodeDate(timeDetail: IntArray): JSONObject {
        val date = JSONObject()

        try {
            val year = decToHex(timeDetail.getOrNull(0) ?: 0) + 2000
            val month = intToMonthStr(decToHex(timeDetail.getOrNull(1) ?: 1))
            val day = decToHex(timeDetail.getOrNull(2) ?: 1)

            date.put("year", year)
            date.put("month", month)
            date.put("day", day)
        } catch (e: Exception) {
            Timber.e("Could not decode date: ${timeDetail.contentToString()}: ${e.message}")
        }

        return date
    }

    /**
     * Converts BCD (binary-coded decimal) byte to decimal.
     * Example: 0x22 as decimal integer 34 → toString(16) = "22" → toInt() = 22
     */
    private fun decToHex(dec: Int): Int {
        return dec.toString(16).toInt()
    }

    /**
     * Converts a decimal integer to BCD (binary-coded decimal) byte.
     * Inverse of decToHex: e.g. 22 → "22" → toInt(16) = 0x22 = 34
     */
    private fun hexToDec(hex: Int): Int {
        return hex.toString().toInt(16)
    }

    /**
     * Converts numeric month (1-12) to month name string.
     */
    private fun intToMonthStr(monthInt: Int): String {
        return when (monthInt) {
            1 -> "JANUARY"
            2 -> "FEBRUARY"
            3 -> "MARCH"
            4 -> "APRIL"
            5 -> "MAY"
            6 -> "JUNE"
            7 -> "JULY"
            8 -> "AUGUST"
            9 -> "SEPTEMBER"
            10 -> "OCTOBER"
            11 -> "NOVEMBER"
            12 -> "DECEMBER"
            else -> ""
        }
    }

    private fun convertArrayListToJSONArray(arrayList: ArrayList<String>): JSONArray {
        val jsonArray = JSONArray()
        for (item in arrayList) {
            jsonArray.put(item)
        }
        return jsonArray
    }

    // ========================================================================
    // Pure Helper Functions for Time Period Encoding
    // ========================================================================

    /**
     * Encodes enabled flag and repeat period into a single time period byte.
     * Uses bit-OR operations with ReminderMasks constants.
     */
    private fun createTimePeriod(enabled: Boolean?, repeatPeriod: String?): Int {
        var timePeriod: Int = 0

        if (enabled == true) {
            timePeriod = timePeriod or ReminderMasks.ENABLED_MASK
        }
        when (repeatPeriod) {
            "WEEKLY" -> timePeriod = timePeriod or ReminderMasks.WEEKLY_MASK
            "MONTHLY" -> timePeriod = timePeriod or ReminderMasks.MONTHLY_MASK
            "YEARLY" -> timePeriod = timePeriod or ReminderMasks.YEARLY_MASK
        }
        return timePeriod
    }

    /**
     * Encodes time detail array from repeat period and date information.
     *
     * Output array structure:
     * [0] - time period byte (already set by caller)
     * [1-3] - start date (year, month, day)
     * [4-6] - end date (year, month, day)
     * [7] - days of week bitmask (for weekly only)
     * [8-9] - padding (0x00)
     *
     * Handles all repeat types with appropriate date and day-of-week encoding.
     */
    private fun createTimeDetail(
        repeatPeriod: String?,
        startDate: JSONObject?,
        endDate: JSONObject?,
        daysOfWeek: JSONArray?
    ): IntArray {
        val timeDetail = IntArray(8)

        // Encode start and end dates for all repeat types
        encodeDate(timeDetail, startDate, endDate)

        // For weekly reminders, encode the days of week bitmask
        if (repeatPeriod == "WEEKLY") {
            var dayOfWeek = 0

            if (daysOfWeek != null) {
                (0 until daysOfWeek.length()).forEach { i ->
                    when (daysOfWeek.getString(i)) {
                        "SUNDAY" -> dayOfWeek = dayOfWeek or ReminderMasks.SUNDAY_MASK
                        "MONDAY" -> dayOfWeek = dayOfWeek or ReminderMasks.MONDAY_MASK
                        "TUESDAY" -> dayOfWeek = dayOfWeek or ReminderMasks.TUESDAY_MASK
                        "WEDNESDAY" -> dayOfWeek = dayOfWeek or ReminderMasks.WEDNESDAY_MASK
                        "THURSDAY" -> dayOfWeek = dayOfWeek or ReminderMasks.THURSDAY_MASK
                        "FRIDAY" -> dayOfWeek = dayOfWeek or ReminderMasks.FRIDAY_MASK
                        "SATURDAY" -> dayOfWeek = dayOfWeek or ReminderMasks.SATURDAY_MASK
                    }
                }
            }
            timeDetail[6] = dayOfWeek
        }

        return timeDetail
    }

    /**
     * Encodes dates into BCD format for watch protocol.
     * Watch stores dates as "hex-decimals": year 2022 becomes 0x22, month 5 becomes 0x05.
     * Uses hexToDec() which is the inverse of decToHex() used during decode.
     *
     * Example: year 2022 -> rem(2000) = 22 -> hexToDec(22) -> "22".toInt(16) = 0x22 = 34
     */
    private fun encodeDate(timeDetail: IntArray, startDate: JSONObject?, endDate: JSONObject?) {
        // Encode start date (positions 0-2)
        timeDetail[0] = hexToDec(startDate?.getInt("year")?.rem(2000) ?: 0)
        timeDetail[1] = hexToDec(monthStrToInt(startDate?.getString("month") ?: "JANUARY"))
        timeDetail[2] = hexToDec(startDate?.getInt("day") ?: 1)

        // Encode end date (positions 3-5)
        timeDetail[3] = hexToDec(endDate?.getInt("year")?.rem(2000) ?: 0)
        timeDetail[4] = hexToDec(monthStrToInt(endDate?.getString("month") ?: "JANUARY"))
        timeDetail[5] = hexToDec(endDate?.getInt("day") ?: 1)

        // Padding
        timeDetail[6] = 0
        timeDetail[7] = 0
    }

    /**
     * Converts month name string to numeric month (1-12).
     */
    private fun monthStrToInt(monthStr: String): Int {
        return when (monthStr) {
            "JANUARY" -> 1
            "FEBRUARY" -> 2
            "MARCH" -> 3
            "APRIL" -> 4
            "MAY" -> 5
            "JUNE" -> 6
            "JULY" -> 7
            "AUGUST" -> 8
            "SEPTEMBER" -> 9
            "OCTOBER" -> 10
            "NOVEMBER" -> 11
            "DECEMBER" -> 12
            else -> -1
        }
    }
}

// ============================================================================
// Imperative Shell: Side Effects & State Management
// ============================================================================

/**
 * Events/Reminders IO handler with state management.
 *
 * Manages reading and writing reminder/event data to the watch.
 * Reminders consist of two separate parts:
 * 1. Title data (command 0x30) - ASCII title string
 * 2. Time data (command 0x31) - date, repeat period, days of week
 *
 * These are fetched separately and combined into a single Event object.
 * Uses pure functional core for encoding and decoding operations.
 */
@RequiresApi(Build.VERSION_CODES.O)
object EventsIO {
    private const val MAX_REMINDERS = 5
    private const val EMPTY_EVENT_JSON = """
        {
            "title": "",
            "time": {
                "daysOfWeek": [],
                "enabled": false,
                "startDate": {
                    "day": 1,
                    "month": "JANUARY",
                    "year": 2000
                },
                "endDate": {
                    "day": 1,
                    "month": "JANUARY",
                    "year": 2000
                },
                "incompatible": false,
                "repeatPeriod": "NEVER"
            }
        }
    """

    private data class State(
        val deferredResult: CompletableDeferred<Event>? = null,
        val deferredResultTitle: CompletableDeferred<JSONObject>? = null,
        val deferredResultTime: CompletableDeferred<JSONObject>? = null
    )

    private var state = State()

    @SuppressLint("DefaultLocale")
    suspend fun request(eventNumber: Int): Event {
        state = state.copy(
            deferredResult = CompletableDeferred(),
            deferredResultTitle = CompletableDeferred(),
            deferredResultTime = CompletableDeferred()
        )

        suspend fun requestTitle(keyTitle: String): JSONObject {
            IO.request(keyTitle)
            return state.deferredResultTitle?.await() ?: JSONObject()
        }

        suspend fun requestTime(keyTime: String): JSONObject {
            IO.request(keyTime)
            return state.deferredResultTime?.await() ?: JSONObject()
        }

        fun combineJSONObjects(obj1: JSONObject, obj2: JSONObject): JSONObject =
            JSONObject(obj1.toString()).apply {
                obj2.keys().forEach { key -> put(key, obj2.get(key)) }
            }

        suspend fun waitForEvent(eventNumber: Int) {
            val titleVal = CachedIO.request("30$eventNumber") { key ->
                requestTitle(key)
            }
            val timeVal = CachedIO.request("31$eventNumber") { key ->
                requestTime(key)
            }

            val eventJson = combineJSONObjects(titleVal, timeVal)
            state.deferredResult?.complete(Event(eventJson))
        }

        waitForEvent(eventNumber)
        return state.deferredResult?.await() ?: Event(JSONObject())
    }

    fun set(events: ArrayList<Event>) {
        if (events.isEmpty()) {
            Timber.d("Events model not initialised! Cannot set reminders")
            return
        }

        fun toJson(events: ArrayList<Event>): String =
            Gson().toJson(events)

        fun <T> appendAndTruncate(
            arr1: ArrayList<T>,
            arr2: ArrayList<T>,
            maxSize: Int
        ): ArrayList<T> =
            ArrayList<T>().apply {
                addAll(arr1)
                addAll(arr2)
                while (size > maxSize) removeAt(size - 1)
            }

        fun getEnabledEvents(events: ArrayList<Event>): ArrayList<Event> =
            events.filter { it.enabled } as ArrayList<Event>

        fun padToMax(currentEvents: ArrayList<Event>, maxReminders: Int): ArrayList<Event> =
            if (currentEvents.size >= maxReminders) {
                currentEvents
            } else {
                ArrayList<Event>(currentEvents).apply {
                    val emptyEvent = Event(JSONObject(EMPTY_EVENT_JSON))
                    for (i in 0 until (maxReminders - currentEvents.size)) {
                        add(emptyEvent)
                    }
                }
            }

        fun getEventsToSend(): ArrayList<Event> =
            padToMax(
                appendAndTruncate(
                    getEnabledEvents(events),
                    events.filter { !it.enabled } as ArrayList<Event>,
                    MAX_REMINDERS
                ),
                MAX_REMINDERS
            )

        Connection.sendMessage("{action: \"SET_REMINDERS\", value: ${toJson(getEventsToSend())} }")
    }

    fun onReceived(data: String) {
        // Use pure function to decode time data
        EventsIOFunctional.reminderTimeToJson(data)
            .fold(
                onSuccess = { timeJson ->
                    state.deferredResultTime?.complete(timeJson)
                },
                onFailure = { error ->
                    Timber.e("Failed to decode reminder time: ${error.message}")
                    state.deferredResultTime?.complete(JSONObject())
                }
            )
    }

    fun onReceivedTitle(data: String) {
        // Use pure function to decode title data
        EventsIOFunctional.reminderTitleToJson(data)
            .fold(
                onSuccess = { titleJson ->
                    state.deferredResultTitle?.complete(titleJson)
                },
                onFailure = { error ->
                    Timber.e("Failed to decode reminder title: ${error.message}")
                    state.deferredResultTitle?.complete(JSONObject())
                }
            )
    }

    fun sendToWatchSet(message: String) {
        (JSONObject(message).get("value") as JSONArray)
            .let { remindersJsonArr ->
                (0 until remindersJsonArr.length()).forEach { index ->
                    val reminderJson = remindersJsonArr.getJSONObject(index)
                    val reminderNumber = index + 1

                    CachedIO.set("30$reminderNumber") {
                        processReminderTitle(reminderNumber, reminderJson)
                    }
                    CachedIO.set("31$reminderNumber") {
                        processReminderTime(reminderNumber, reminderJson)
                    }
                }
            }
    }

    private fun processReminderTitle(reminderNumber: Int, reminderJson: JSONObject) {
        EventsIOFunctional.reminderTitleFromJson(reminderJson)
            .fold(
                onSuccess = { titleBytes ->
                    Utils.byteArrayOfInts(
                        CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TITLE.code,
                        reminderNumber
                    ) + titleBytes
                },
                onFailure = { error ->
                    Timber.e("Failed to encode reminder title: ${error.message}")
                    byteArrayOf()
                }
            )
            .let { titleCmd ->
                if (titleCmd.isNotEmpty()) {
                    IO.writeCmd(GetSetMode.SET, titleCmd)
                }
            }
    }

    private fun processReminderTime(reminderNumber: Int, reminderJson: JSONObject) {
        EventsIOFunctional.reminderTimeFromJson(reminderJson)
            .fold(
                onSuccess = { timeBytes ->
                    IntArray(0)
                        .plus(CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TIME.code)
                        .plus(reminderNumber)
                        .plus(timeBytes)
                        .let(Utils::byteArrayOfIntArray)
                },
                onFailure = { error ->
                    Timber.e("Failed to encode reminder time: ${error.message}")
                    byteArrayOf()
                }
            )
            .let { timeCmd ->
                if (timeCmd.isNotEmpty()) {
                    IO.writeCmd(GetSetMode.SET, timeCmd)
                }
            }
    }

    fun clearAll() {
        (1..5).forEach { index ->
            IO.writeCmd(
                GetSetMode.SET,
                Utils.byteArrayOfInts(
                    CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TITLE.code,
                    index
                ) + ByteArray(18)
            )
            CachedIO.remove("30$index")

            IO.writeCmd(
                GetSetMode.SET,
                Utils.byteArrayOfInts(
                    CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TIME.code,
                    index
                ) + ByteArray(9)
            )
            CachedIO.remove("31$index")
        }
    }
}
