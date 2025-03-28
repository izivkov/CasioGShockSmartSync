package org.avmedia.gshockGoogleSync.services

import android.annotation.SuppressLint
import android.content.Context

// KeepAliveManager.kt
class KeepAliveManager private constructor(private val context: Context) {
    private val preferences = KeepAlivePreferences(context)

    fun enable() {
        preferences.isEnabled = true
        KeepAliveService.startService(context)
    }

    fun disable() {
        preferences.isEnabled = false
        KeepAliveService.stopService(context)
    }

    fun isEnabled(): Boolean = preferences.isEnabled

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: KeepAliveManager? = null

        fun getInstance(context: Context): KeepAliveManager {
            return instance ?: synchronized(this) {
                instance ?: KeepAliveManager(context).also { instance = it }
            }
        }
    }
}
