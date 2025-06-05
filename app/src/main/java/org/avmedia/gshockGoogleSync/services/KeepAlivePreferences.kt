package org.avmedia.gshockGoogleSync.services

import android.content.Context
import androidx.core.content.edit

class KeepAlivePreferences(context: Context) {
    private data class PreferenceState(
        val isEnabled: Boolean
    )

    private val preferences = context.applicationContext
        .getSharedPreferences("keep_alive_prefs", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = getPreferenceState().isEnabled
        set(value) = updatePreferenceState(PreferenceState(value))

    private fun getPreferenceState(): PreferenceState =
        PreferenceState(
            isEnabled = preferences.getBoolean("is_enabled", false)
        )

    private fun updatePreferenceState(state: PreferenceState) {
        preferences.edit {
            putBoolean("is_enabled", state.isEnabled)
        }
    }
}
