package org.avmedia.gshockGoogleSync.ui.time

import java.io.File
import org.avmedia.gshockGoogleSync.homeassistant.KeyValueStore
import org.avmedia.gshockGoogleSync.scratchpad.TimeSettingsStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The home screen draws its first frame from the stored values, marked as updating, without any watch request. */
class HomeSnapshotTest {
    private class Mem : KeyValueStore {
        val m = HashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
        override fun delete(key: String) { m.remove(key) }
    }

    @Test fun theFirstStateIsTheStoredOneMarkedAsUpdating() {
        val store = HomeSnapshotStore(Mem())
        val shown = TimeState(timer = 90, homeTime = "Tokyo", batteryLevel = 50, temperature = 21, watchName = "GBX-H5600",
            timeZoneOption = TimeSettingsStorage.TimeZoneOption.LOCAL_MEAN_TIME)
        store.save("AA:BB", HomeSnapshotStore.of(shown))
        val first = HomeSnapshotStore.firstState(store.load("AA:BB"))
        assertEquals(shown.copy(updating = true), first)
    }

    @Test fun noStoredValuesGiveTheDefaultsMarkedAsUpdating() {
        val store = HomeSnapshotStore(Mem())
        assertEquals(TimeState(updating = true), HomeSnapshotStore.firstState(store.load("AA:BB")))
        assertEquals(TimeState(updating = true), HomeSnapshotStore.firstState(store.load("")))
        store.save("AA:BB", HomeSnapshot(batteryLevel = 40))
        assertEquals(TimeState(updating = true), HomeSnapshotStore.firstState(store.load("CC:DD")))
    }

    @Test fun aBrokenStoredValueIsIgnored() {
        val mem = Mem().apply { m["home.snapshot.AA:BB"] = "not json" }
        assertEquals(TimeState(updating = true), HomeSnapshotStore.firstState(HomeSnapshotStore(mem).load("AA:BB")))
    }

    /** The view model's first state is built from the store only: no api call before the first frame. */
    @Test fun theViewModelsFirstStateMakesNoWatchRequest() {
        val src = File("src/main/java/org/avmedia/gshockGoogleSync/ui/time/TimeViewModel.kt").readText()
        val first = src.substringAfter("private val _state = MutableStateFlow(").substringBefore("val state = _state")
        assertTrue(first, "HomeSnapshotStore.firstState(" in first)
        assertTrue(first, !Regex("""\bapi\.""").containsMatchIn(first) && "timeSettingsStorage" !in first)
    }
}
