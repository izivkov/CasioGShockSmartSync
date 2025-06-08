package org.avmedia.gshockGoogleSync.services

import android.content.Context

class KeepAliveManager private constructor(context: Context) {
    private data class ServiceState(
        val isEnabled: Boolean
    )

    private val preferences = KeepAlivePreferences(context.applicationContext)
    private val appContext = context.applicationContext

    fun enable() = updateServiceState(enabled = true)
    fun disable() = updateServiceState(enabled = false)
    fun isEnabled(): Boolean = preferences.isEnabled

    private fun updateServiceState(enabled: Boolean) {
        ServiceState(enabled)
            .also { state -> updatePreferences(state) }
            .also { state -> updateService(state) }
    }

    private fun updatePreferences(state: ServiceState) {
        preferences.isEnabled = state.isEnabled
    }

    private fun updateService(state: ServiceState) = when {
        state.isEnabled -> KeepAliveService.startService(appContext)
        else -> KeepAliveService.stopService(appContext)
    }

    companion object {
        @Volatile
        private var instance: KeepAliveManager? = null

        fun getInstance(context: Context): KeepAliveManager =
            instance ?: synchronized(this) {
                instance ?: createManager(context).also { instance = it }
            }

        private fun createManager(context: Context): KeepAliveManager =
            KeepAliveManager(context)
    }
}
