package com.beamburst.casswatch.ui.alarms

import com.google.gson.Gson
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSyncStorageTest {
    private val gson = Gson()

    @Test
    fun `StoredAlarm without firedAt field deserializes with firedAt null`() {
        val oldJson = """[{"hour":6,"minute":30,"enabled":true,"hasHourlyChime":false,"name":"Wake-up"}]"""
        val type = object : com.google.gson.reflect.TypeToken<List<AlarmSyncStorage.StoredAlarm>>() {}.type
        val stored: List<AlarmSyncStorage.StoredAlarm> = gson.fromJson(oldJson, type)
        assertNull(stored[0].firedAt)
    }

    @Test
    fun `StoredAlarm with firedAt deserializes correctly`() {
        val json = """[{"hour":6,"minute":30,"enabled":true,"hasHourlyChime":false,"name":null,"firedAt":1234567890}]"""
        val type = object : com.google.gson.reflect.TypeToken<List<AlarmSyncStorage.StoredAlarm>>() {}.type
        val stored: List<AlarmSyncStorage.StoredAlarm> = gson.fromJson(json, type)
        assertEquals(1234567890L, stored[0].firedAt)
    }

    @Test
    fun `SyncRecord serialization round-trips correctly`() {
        val record = AlarmSyncStorage.SyncRecord(
            syncedAt = 9999L,
            sentAlarmHashes = listOf("6:30:ALL:true", "9:15:MON,FRI:false")
        )
        val json = gson.toJson(record)
        val loaded = gson.fromJson(json, AlarmSyncStorage.SyncRecord::class.java)
        assertEquals(9999L, loaded.syncedAt)
        assertEquals(2, loaded.sentAlarmHashes.size)
        assertTrue("6:30:ALL:true" in loaded.sentAlarmHashes)
        assertTrue("9:15:MON,FRI:false" in loaded.sentAlarmHashes)
    }
}
