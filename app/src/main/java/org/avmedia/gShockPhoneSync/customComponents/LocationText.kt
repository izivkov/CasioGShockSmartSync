/*
 * Created by Ivo Zivkov (izivkov@gmail.com) on 2022-03-30, 12:06 a.m.
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 2022-03-29, 6:24 p.m.
 */

package org.avmedia.gShockPhoneSync.customComponents

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.AttributeSet
//import com.google.android.gms.location.FusedLocationProviderClient
//import com.google.android.gms.location.LocationCallback
//import com.google.android.gms.location.LocationRequest
//import com.google.android.gms.location.LocationServices
import org.avmedia.gShockPhoneSync.utils.ProgressEvents
import timber.log.Timber
import java.util.*


class LocationText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : com.google.android.material.textview.MaterialTextView(context, attrs, defStyleAttr) {

    // lateinit var mFusedLocationClient: FusedLocationProviderClient

    private object LastLocation {
        var cachedLocation: String = ""
    }

    init {
        text = LastLocation.cachedLocation
        if (text.isEmpty()) {
            // mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            createAppEventsSubscription()
        }
    }

    private fun createAppEventsSubscription() {
        ProgressEvents.subscriber.start(
            this.javaClass.simpleName,

            {
                when (it) {
                    ProgressEvents.Events.AllPermissionsAccepted -> {
                        // getLastLocation()
                    }
                }
            },
            { throwable -> Timber.d("Got error on subscribe: $throwable") })
    }

    /*
    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        if (isLocationEnabled()) {
            mFusedLocationClient.lastLocation.addOnCompleteListener(context as Activity) { task ->
                try {
                    var location: Location? = task.result
                    if (location == null) {
                        // requestNewLocationData()
                    } else {
                        val geoCoder = Geocoder(context, Locale.getDefault())
                        val addresses: List<Address> =
                            geoCoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (addresses.isNotEmpty() && addresses[0].locality != null) {
                            text = addresses[0].locality
                            LastLocation.cachedLocation = addresses[0].locality
                        }
                    }
                } catch (e: Exception) {
                    text = ""
                }
            }
        }
    }
     */

    /*
    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        var mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 0
        mLocationRequest.fastestInterval = 0
        mLocationRequest.numUpdates = 1

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context as Activity)
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()!!
        )
    }
     */

//    private val mLocationCallback = object : LocationCallback() {
//        fun callBack() {
//            Timber.d("mLocationCallback called...")
//        }
//    }
//
//    private fun isLocationEnabled(): Boolean {
//        var locationManager: LocationManager =
//            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
//        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
//            LocationManager.NETWORK_PROVIDER
//        )
//    }
}
