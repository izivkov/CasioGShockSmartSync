package org.avmedia.gShockSmartSyncCompose.services

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager

object LocationProvider {

    class Location(
        val latitude: Double,
        val longitude: Double
    ) {
        init {
            require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
            require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
        }
    }

    @SuppressLint("MissingPermission")
    fun getLocation(context: Context): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Get last known location from GPS_PROVIDER
        val lastLocationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

        // If lastLocationGPS is null, try NETWORK_PROVIDER
        return if (lastLocationGPS != null) {
            Location(lastLocationGPS.latitude, lastLocationGPS.longitude)
        } else {
            val lastLocationNetwork =
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastLocationNetwork != null) {
                Location(lastLocationNetwork.latitude, lastLocationNetwork.longitude)
            } else {
                null
            }
        }
    }
}
