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

        suspend fun requestTitle(keyTitle: String): Any {
            IO.request(keyTitle)
            return state.deferredResultTitle?.await() ?: JSONObject()
        }

        suspend fun requestTime(keyTime: String): Any {
            IO.request(keyTime)
            return state.deferredResultTime?.await() ?: JSONObject()
        }

        fun combineJSONObjects(obj1: JSONObject, obj2: JSONObject): JSONObject =
            JSONObject(obj1.toString()).apply {
                obj2.keys().forEach { key -> put(key, obj2.get(key)) }
            }

        suspend fun waitForEvent(eventNumber: Int) {
            val titleVal = CachedIO.request("30$eventNumber") { key ->
                requestTitle(key) as JSONObject
            }
            val timeVal = CachedIO.request("31$eventNumber") { key ->
                requestTime(key) as JSONObject
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
        val decoded = ReminderDecoder.reminderTimeToJson(data + 2)
        state.deferredResultTime?.complete(decoded)
    }

    fun onReceivedTitle(data: String) {
        val decoded = ReminderDecoder.reminderTitleToJson(data)
        state.deferredResultTitle?.complete(decoded)
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
        ReminderEncoder.reminderTitleFromJson(reminderJson)
            .let { title ->
                Utils.byteArrayOfInts(
                    CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TITLE.code,
                    reminderNumber
                ) + title
            }
            .let { IO.writeCmd(GetSetMode.SET, it) }
    }

    private fun processReminderTime(reminderNumber: Int, reminderJson: JSONObject) {
        IntArray(0)
            .plus(CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TIME.code)
            .plus(reminderNumber)
            .plus(ReminderEncoder.reminderTimeFromJson(reminderJson))
            .let(Utils::byteArrayOfIntArray)
            .let { IO.writeCmd(GetSetMode.SET, it) }
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

    object ReminderDecoder {
        /* Reminders
            31 01 05 01 01 01 01 01 01 02 00 // set frequency
                  00 00 00 00 00 00 00 00 00 - not set
                  02 22 03 31 22 05 01 00 00
        */

        fun reminderTimeToJson(reminderStr: String): JSONObject {

            val intArr = Utils.toIntArray(reminderStr)
            if (intArr[3] == 0xFF) {
                // 0XFF indicates end of reminders
                return JSONObject("{\"end\": \"\"}")
            }

            val reminderAll: IntArray = Utils.toIntArray(reminderStr).toIntArray()

            // Remove the first 2 chars:
            // 0x31 05 <--- 00 23 02 21 23 02 21 00 00
            val reminder = reminderAll.sliceArray(IntRange(2, reminderAll.lastIndex))

            val reminderJson = JSONObject()

            val timePeriod = decodeTimePeriod(reminder[0])
            reminderJson.put("enabled", timePeriod.first)
            reminderJson.put("repeatPeriod", timePeriod.second)

            val timeDetailMap = decodeTimeDetail(reminder)

            reminderJson.put("startDate", timeDetailMap["startDate"])
            reminderJson.put("endDate", timeDetailMap["endDate"])

            @Suppress("UNCHECKED_CAST")
            reminderJson.put(
                "daysOfWeek",
                convertArrayListToJSONArray(timeDetailMap["daysOfWeek"] as ArrayList<String>)
            )

            return JSONObject("{\"time\": $reminderJson}")
        }

        private fun convertArrayListToJSONArray(arrayList: ArrayList<String>): JSONArray {
            val jsonArray = JSONArray()
            for (item in arrayList) {
                jsonArray.put(item)
            }
            return jsonArray
        }

        private fun decodeTimePeriod(timePeriod: Int): Pair<Boolean?, String?> {
            var enabled: Boolean = false

            if (timePeriod and ReminderMasks.ENABLED_MASK == ReminderMasks.ENABLED_MASK) {
                enabled = true
            }

            val repeatPeriod = when {
                timePeriod and ReminderMasks.WEEKLY_MASK == ReminderMasks.WEEKLY_MASK -> "WEEKLY"
                timePeriod and ReminderMasks.MONTHLY_MASK == ReminderMasks.MONTHLY_MASK -> "MONTHLY"
                timePeriod and ReminderMasks.YEARLY_MASK == ReminderMasks.YEARLY_MASK -> "YEARLY"
                else -> "NEVER"
            }
            return Pair(enabled, repeatPeriod)
        }

        private fun decodeTimeDetail(timeDetail: IntArray): Map<String, Any> {
            val result = HashMap<String, Any>()

            //                  00 23 02 21 23 02 21 00 00
            // start from here:    ^
            // so, skip 1
            result["startDate"] =
                decodeDate(timeDetail.sliceArray(IntRange(1, timeDetail.lastIndex)))

            //                  00 23 02 21 23 02 21 00 00
            // start from here:             ^
            // so, skip 4
            result["endDate"] = decodeDate(timeDetail.sliceArray(IntRange(4, timeDetail.lastIndex)))

            val dayOfWeek = timeDetail[7]
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

        private fun decodeDate(timeDetail: IntArray): JSONObject {
            // take the last 2 digits only result = {HashMap@17729}  size = 3
            val date = JSONObject()

            try {
                date.put("year", decToHex(timeDetail[0]) + 2000)
                date.put("month", intToMonthStr(decToHex(timeDetail[1])))
                date.put("day", decToHex(timeDetail[2]))
            } catch (e: NumberFormatException) {
                Timber.e("", "Could not handle time: $timeDetail")
            }

            return date
        }

        private fun decToHex(dec: Int): Int {
            return dec.toString(16).toInt()
        }

        private fun intToMonthStr(monthInt: Int): String {
            when (monthInt) {
                1 -> return "JANUARY"
                2 -> return "FEBRUARY"
                3 -> return "MARCH"
                4 -> return "APRIL"
                5 -> return "MAY"
                6 -> return "JUNE"
                7 -> return "JULY"
                8 -> return "AUGUST"
                9 -> return "SEPTEMBER"
                10 -> return "OCTOBER"
                11 -> return "NOVEMBER"
                12 -> return "DECEMBER"
            }

            return ""
        }

        fun reminderTitleToJson(titleByte: String): JSONObject {
            val intArr = Utils.toIntArray(titleByte)
            if (intArr[2] == 0xFF) {
                // 0XFF indicates end of reminders
                return JSONObject("{\"end\": \"\"}")
            }
            val reminderJson = JSONObject()
            reminderJson.put("title", Utils.toAsciiString(titleByte, 2))
            return reminderJson
        }
    }

    object ReminderEncoder {
        /* Reminders
            31 01 05 01 01 01 01 01 01 02 00 // set frequency
                  00 00 00 00 00 00 00 00 00 - not set
                  02 22 03 31 22 05 01 00 00
        */

        fun reminderTimeFromJson(reminderJson: JSONObject): IntArray {

            val enabled = reminderJson.getBooleanSafe("enabled")
            val repeatPeriod = reminderJson.getStringSafe("repeatPeriod")
            val startDate = reminderJson.getJSONObjectSafe("startDate")
            val endDate = reminderJson.getJSONObjectSafe("endDate")
            val daysOfWeek = reminderJson.getJSONArraySafe("daysOfWeek")

            var reminderCmd: IntArray = IntArray(0)

            reminderCmd += createTimePeriod(enabled, repeatPeriod)
            reminderCmd += createTimeDetail(repeatPeriod, startDate, endDate, daysOfWeek)

            return reminderCmd
        }

        private fun createTimeDetail(
            repeatPeriod: String?,
            startDate: JSONObject?,
            endDate: JSONObject?,
            daysOfWeek: JSONArray?
        ): IntArray {
            val timeDetail = IntArray(8)

            when (repeatPeriod) {
                "NEVER" -> {
                    // Once only, Jun 3
                    // 31 01 01 22 06 03 22 06 03 00 00
                    // 31 04 09 22 06 02 22 06 02 00 00

                    encodeDate(timeDetail, startDate, endDate)
                }

                "WEEKLY" -> {
                    /*
                05 01 01 01 01 01 01 20 00 - every friday
                05 01 01 01 01 01 01 19 00
                05 01 01 01 01 01 01 02 00 - evey monday
                00011001 - Sun, Wed, Thu
                */
                    encodeDate(timeDetail, startDate, endDate)

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
                    timeDetail[7] = 0
                }

                "MONTHLY" -> {
                    // Monthly, may 3
                    // 31 05 11 22 05 03 22 05 03 00 00
                    encodeDate(timeDetail, startDate, endDate)
                }

                "YEARLY" -> {
                    /*
                09 22 05 02 22 05 02 00 00 - once every year May 2
                03 22 03 31 22 05 01 00 00 - Period: mar 21 to may 1
                02 22 03 31 22 05 01 00 00 - same but disabled
                01 22 04 01 22 04 01 00 00 - only once, apr 1
                01 22 04 18 22 04 18 00 00 - once only, apr 18
                 */
                    encodeDate(timeDetail, startDate, endDate)
                }

                else -> {
                    Timber.d("Cannot handle Repeat Period: $repeatPeriod!!!")
                }
            }
            return timeDetail
        }

        private fun encodeDate(timeDetail: IntArray, startDate: JSONObject?, endDate: JSONObject?) {
            // take the last 2 digits only
            timeDetail[0] = decToHex(startDate!!.getInt("year").rem(2000))
            timeDetail[1] = decToHex(monthStrToInt(startDate.getString("month")))
            timeDetail[2] = decToHex(startDate.getInt("day"))

            timeDetail[3] = decToHex(endDate!!.getInt("year").rem(2000)) // get the last 2 gits only
            timeDetail[4] = decToHex(monthStrToInt(endDate.getString("month")))
            timeDetail[5] = decToHex(endDate.getInt("day"))

            timeDetail[6] = 0
            timeDetail[7] = 0
        }

        private fun decToHex(dateField: Int): Int {
            // Values are stored in hex numbers, that look like decimals, i.e.
            // 22 04 18 represents 2022, Apr 18. So, we must convert the decimal
            // numbers from the JSON to hex (i.e 0x22 0x04 0x18).
            return Integer.parseInt(dateField.toString(), 16)
        }

        private fun monthStrToInt(monthStr: String): Int {
            when (monthStr) {
                "JANUARY" -> return 1
                "FEBRUARY" -> return 2
                "MARCH" -> return 3
                "APRIL" -> return 4
                "MAY" -> return 5
                "JUNE" -> return 6
                "JULY" -> return 7
                "AUGUST" -> return 8
                "SEPTEMBER" -> return 9
                "OCTOBER" -> return 10
                "NOVEMBER" -> return 11
                "DECEMBER" -> return 12
            }

            return -1
        }

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

        fun reminderTitleFromJson(reminderJson: JSONObject): ByteArray {
            val titleStr: String = reminderJson.get("title") as String
            return Utils.toByteArray(titleStr, 18)
        }
    }
}