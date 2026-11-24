package org.avmedia.gshockGoogleSync.watchvalues

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import org.avmedia.gshockGoogleSync.services.LocationProvider

/** The phone's location as the plugins get it ([org.avmedia.smartsync.plugin.api.DataUse.PHONE_LOCATION]): coarse, last known. */
object PhoneLocation {
    /** Two decimals: about 1 km. */
    fun round(v: Double): Double = Math.round(v * 100.0) / 100.0

    /** Null without the location permission (nothing is read then) or without a last known fix. */
    fun coarse(context: Context): Pair<Double, Double>? {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return coarse(granted) { LocationProvider.getLocation(context)?.let { it.latitude to it.longitude } }
    }

    fun coarse(granted: Boolean, read: () -> Pair<Double, Double>?): Pair<Double, Double>? =
        read()?.let { (lat, lon) -> round(lat) to round(lon) }
}
