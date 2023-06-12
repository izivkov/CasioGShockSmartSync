package org.avmedia.gshockapi.apiIO

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Event
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.casio.*
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

object EventsIO {

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun request(eventNumber: Int): Event {
        return ApiIO.request(eventNumber.toString(), ::getEventFromWatch) as Event
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getEventFromWatch(eventNumber: String): Event {
        CasioIO.request("30${eventNumber}") // reminder title
        CasioIO.request("31${eventNumber}") // reminder time

        var deferredResult = CompletableDeferred<Event>()
        ApiIO.resultQueue.enqueue(
            ResultQueue.KeyedResult(
                "310${eventNumber}", deferredResult as CompletableDeferred<Any>
            )
        )

        var title = ""
        ApiIO.subscribe("REMINDERS") { keyedData ->
            val data = keyedData.getString("value")
            val key = keyedData.getString("key")

            val reminderJson = JSONObject(data)
            when (reminderJson.keys().next()) {
                "title" -> {
                    title = reminderJson["title"] as String
                }
                "time" -> {
                    reminderJson.put("title", title)
                    val event = Event(reminderJson)
                    ApiIO.resultQueue.dequeue(key)?.complete(event)
                }
            }
        }
        return deferredResult.await()
    }

    fun set(events: ArrayList<Event>) {

        if (events.isEmpty()) {
            Timber.d("Events model not initialised! Cannot set reminders")
            return
        }

        @Synchronized
        fun toJson(events: ArrayList<Event>): String {
            val gson = Gson()
            return gson.toJson(events)
        }

        fun getSelectedEvents(events: ArrayList<Event>): String {
            val selectedEvents = events.filter { it.selected } as ArrayList<Event>
            return toJson(selectedEvents)
        }

        Connection.sendMessage("{action: \"SET_REMINDERS\", value: ${getSelectedEvents(events)} }")
    }

    fun toJson(data: String): JSONObject {
        val reminderJson = JSONObject()
        val value = ReminderDecoder.reminderTimeToJson(data + 2)
        reminderJson.put(
            "REMINDERS",
            JSONObject().put("key", ApiIO.createKey(data)).put("value", value)
        )
        return reminderJson
    }

    fun toJsonTitle(data: String): JSONObject {
        return JSONObject().put(
            "REMINDERS",
            JSONObject().put("key", ApiIO.createKey(data))
                .put("value", ReminderDecoder.reminderTitleToJson(data))
        )
    }

    fun sendToWatchSet(message:String) {
        val remindersJsonArr: JSONArray = JSONObject(message).get("value") as JSONArray
        (0 until remindersJsonArr.length()).forEachIndexed { index, element ->
            val reminderJson = remindersJsonArr.getJSONObject(element)
            val title = ReminderEncoder.reminderTitleFromJson(reminderJson)
            WatchFactory.watch.writeCmd(
                0x000e, Utils.byteArrayOfInts(
                    CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TITLE.code, index + 1
                ) + title
            )

            var reminderTime = IntArray(0)
            reminderTime += CasioConstants.CHARACTERISTICS.CASIO_REMINDER_TIME.code
            reminderTime += index + 1
            reminderTime += ReminderEncoder.reminderTimeFromJson(reminderJson)
            WatchFactory.watch.writeCmd(0x000e, Utils.byteArrayOfIntArray(reminderTime))
        }

        Timber.i("Got reminders $remindersJsonArr")
    }
}