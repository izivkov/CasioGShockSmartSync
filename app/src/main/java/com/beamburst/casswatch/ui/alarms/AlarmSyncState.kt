package com.beamburst.casswatch.ui.alarms

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmSyncState @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val _lastSync = MutableStateFlow<AlarmSyncStorage.SyncRecord?>(
        AlarmSyncStorage.loadSyncRecord(context)    // restore across app restarts
    )
    val lastSync: StateFlow<AlarmSyncStorage.SyncRecord?> = _lastSync.asStateFlow()

    fun update(record: AlarmSyncStorage.SyncRecord) {
        _lastSync.value = record
        AlarmSyncStorage.saveSyncRecord(context, record)
    }
}
