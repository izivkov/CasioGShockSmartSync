package org.avmedia.gshockGoogleSync.services

import android.content.Context
import androidx.core.content.edit

// KeepAlivePreferences.kt
class KeepAlivePreferences(private val context: Context) {
    private val preferences = context.getSharedPreferences("keep_alive_prefs", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = preferences.getBoolean("is_enabled", false)
        set(value) = preferences.edit() { putBoolean("is_enabled", value) }
}