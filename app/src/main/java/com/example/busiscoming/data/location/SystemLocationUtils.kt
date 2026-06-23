package com.example.busiscoming.data.location

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings

object SystemLocationUtils {
    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(LocationManager::class.java)
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    fun settingsIntent(): Intent {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }
}
