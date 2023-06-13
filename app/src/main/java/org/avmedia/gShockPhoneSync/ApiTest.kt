package org.avmedia.gShockPhoneSync

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.runBlocking
import org.avmedia.gShockPhoneSync.MainActivity.Companion.api
import org.avmedia.gShockPhoneSync.ui.alarms.AlarmsModel
import org.avmedia.gShockPhoneSync.ui.events.CalendarEvents
import org.avmedia.gShockPhoneSync.ui.events.EventsModel
import org.avmedia.gshockapi.Alarm
import org.avmedia.gshockapi.Settings
import org.avmedia.gshockapi.apiIO.CasioIO

class ApiTest {
    fun run(context: Context) {

        runBlocking {
            waitForConnectionCached(context)
            // api().init ()

            println("Button pressed: ${api().getPressedButton()}")

            println("Name returned: ${api().getWatchName()}")

            println("Battery Level: ${api().getBatteryLevel()}")
            println("Timer: ${api().getTimer()}")
            println("App Info: ${api().getAppInfo()}")

            println("Home Time: ${api().getHomeTime()}")

            getDTSState()
            getWorldCities()
            getRTSForWorldCities()

            api().setTime()

            val alarms = api().getAlarms()
            var model = AlarmsModel
            model.alarms.clear()
            model.alarms.addAll(alarms)
            println("Alarm model: ${model.toJson()}")

            model.alarms[0] = Alarm(6, 46, enabled = true, hasHourlyChime = false)
            model.alarms[4] = Alarm(9, 25, enabled = false)
            api().setAlarms(model.alarms)

            handleReminders()

            handleSettings()

            println("--------------- END ------------------")
        }
    }

    private suspend fun getRTSForWorldCities() {
        println("World DTS City 0: ${api().getDSTForWorldCities(0)}")
        println("World DTS City 1: ${api().getDSTForWorldCities(1)}")
        println("World DTS City 2: ${api().getDSTForWorldCities(2)}")
        println("World DTS City 3: ${api().getDSTForWorldCities(3)}")
        println("World DTS City 4: ${api().getDSTForWorldCities(4)}")
        println("World DTS City 5: ${api().getDSTForWorldCities(5)}")
    }

    private suspend fun getWorldCities() {
        println("World City 0: ${api().getWorldCities(0)}")
        println("World City 1: ${api().getWorldCities(1)}")
        println("World City 2: ${api().getWorldCities(2)}")
        println("World City 3: ${api().getWorldCities(3)}")
        println("World City 4: ${api().getWorldCities(4)}")
        println("World City 5: ${api().getWorldCities(5)}")
    }

    private suspend fun getDTSState() {
        println("TDS STATE ZERO: ${api().getDSTWatchState(CasioIO.DTS_STATE.ZERO)}")
        println("TDS STATE TWO: ${api().getDSTWatchState(CasioIO.DTS_STATE.TWO)}")
        println("TDS STATE FOUR: ${api().getDSTWatchState(CasioIO.DTS_STATE.FOUR)}")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun handleReminders() {
        var eventsModel = EventsModel
        eventsModel.clear()

        eventsModel.events.addAll(api().getEventsFromWatch())
        println("Event model from Watch: $eventsModel")

        eventsModel.events.addAll(CalendarEvents.getEventsFromCalendar(MainActivity.applicationContext()))
        println("Event model from Google Calendar: $eventsModel")

        api().setEvents(CalendarEvents.getEventsFromCalendar(MainActivity.applicationContext()))
    }

    private suspend fun handleSettings() {
        val settings: Settings = api().getSettings()
        settings.dateFormat = "MM:DD"
        api().setSettings(settings)
    }

    private suspend fun handleTimer() {
        var timerValue = api().getTimer()
        api().setTimer(timerValue)
    }

    private suspend fun waitForConnectionCached(context: Context) {
        api().waitForConnection()
    }
}