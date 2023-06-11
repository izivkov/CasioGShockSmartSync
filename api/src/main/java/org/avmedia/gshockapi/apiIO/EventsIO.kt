package org.avmedia.gshockapi.apiIO

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.Event
import org.avmedia.gshockapi.ble.Connection
import org.avmedia.gshockapi.casio.BluetoothWatch
import org.avmedia.gshockapi.utils.Utils
import org.json.JSONObject
import timber.log.Timber
import java.util.ArrayList

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

}