package org.avmedia.gshockGoogleSync.services

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import androidx.compose.ui.text.intl.Locale
import kotlin.text.uppercase

object LocationProvider {
    sealed interface LocationResult {
        data class Success(val location: Location) : LocationResult
        data object NoProvider : LocationResult
        data object NoLocation : LocationResult
    }

    data class Location(
        val latitude: Double,
        val longitude: Double
    ) {
        init {
            require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
            require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
        }

        companion object {
            fun fromAndroidLocation(location: android.location.Location): Location =
                Location(location.latitude, location.longitude)
        }
    }

    @SuppressLint("MissingPermission")
    fun getLocation(context: Context): Location? =
        runCatching {
            when (val result = getLocationResult(context)) {
                is LocationResult.Success -> result.location
                else -> null
            }
        }.getOrNull()

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun getLocationResult(context: Context): LocationResult {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return LocationResult.NoProvider

        return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?.let { LocationResult.Success(Location.fromAndroidLocation(it)) }
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?.let { LocationResult.Success(Location.fromAndroidLocation(it)) }
            ?: LocationResult.NoLocation
    }

    /**
     * Provides a reliable, offline-first country code using a hybrid strategy.
     *
     * 1. Tries to get the country from the SIM card's mobile network (fast, offline).
     * 2. If that fails, it tries to use the Geocoder with the physical location (may use network).
     * 3. As a last resort, it falls back to the user's device language/region setting.
     *
     * @param context The application context.
     * @return A two-letter ISO country code (e.g., "US", "DE", "IN") or null if undetermined.
     */
    @SuppressLint("MissingPermission")
    fun getCountryCode(context: Context): String? {
        var countryCode: String?

        // --- STRATEGY 1: TRY OFFLINE NETWORK DETECTION FIRST (FAST & RELIABLE) ---
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        countryCode = telephonyManager?.networkCountryIso?.uppercase(java.util.Locale.US)
        if (!countryCode.isNullOrBlank()) {
            return countryCode
        }

        // --- STRATEGY 2: IF THAT FAILS, TRY GEOCODER (MAY USE NETWORK) ---
        // Requires location permission, which the calling code should have.
        getLocation(context)?.let { location ->
            try {
                val geocoder = Geocoder(context, java.util.Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                countryCode = addresses?.firstOrNull()?.countryCode?.uppercase(java.util.Locale.US)
                if (!countryCode.isNullOrBlank()) {
                    return countryCode
                }
            } catch (e: Exception) {
                // Geocoder failed, proceed to the next strategy.
            }
        }

        // --- STRATEGY 3: AS A LAST RESORT, FALL BACK TO PHONE'S LOCALE SETTING ---
        countryCode = java.util.Locale.getDefault().country.uppercase(java.util.Locale.US)
        if (!countryCode.isBlank()) {
            return countryCode
        }

        return null // Return null if all strategies fail.
    }
}
