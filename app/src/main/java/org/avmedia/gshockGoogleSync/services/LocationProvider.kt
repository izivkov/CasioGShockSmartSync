package org.avmedia.gshockGoogleSync.services

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.annotation.RequiresPermission

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
}
