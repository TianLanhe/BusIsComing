package com.example.busiscoming.data.location

import android.content.Context

class LocationPermissionStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun isAutoRequestDenied(): Boolean {
        return preferences.getBoolean(KEY_AUTO_REQUEST_DENIED, false)
    }

    fun setAutoRequestDenied(denied: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_REQUEST_DENIED, denied).apply()
    }

    companion object {
        private const val PREFS_NAME = "location_permission_state"
        private const val KEY_AUTO_REQUEST_DENIED = "auto_request_denied"
    }
}
