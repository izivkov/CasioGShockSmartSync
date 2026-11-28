package org.avmedia.gshockGoogleSync.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages explicit user permission switches for always-connected watches (DW-H5600, GBX-H5600, etc.).
 * - Default state: OFF.
 * - When ON: BLE advertisements from the watch are allowed to connect.
 * - Upon disconnection: state automatically resets to OFF.
 */
object AlwaysConnectedAllow {
    private val _enabledAddresses = MutableStateFlow<Set<String>>(emptySet())
    val enabledAddresses: StateFlow<Set<String>> = _enabledAddresses.asStateFlow()

    fun isEnabled(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        return _enabledAddresses.value.contains(address.uppercase())
    }

    fun setEnabled(address: String, enabled: Boolean) {
        val sanitized = address.uppercase()
        _enabledAddresses.update { current ->
            if (enabled) current + sanitized else current - sanitized
        }
    }

    fun turnOff(address: String? = null) {
        if (address == null) {
            _enabledAddresses.value = emptySet()
        } else {
            val sanitized = address.uppercase()
            _enabledAddresses.update { current -> current - sanitized }
        }
    }
}
