package com.golink.busiscoming.data.local

import android.content.Context
import com.golink.busiscoming.data.model.AppThemeMode

class AppThemePreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCE_FILE,
        Context.MODE_PRIVATE
    )

    fun getMode(): AppThemeMode =
        AppThemeMode.fromStoredValue(preferences.getString(KEY_APP_THEME_MODE, null))

    fun setMode(mode: AppThemeMode) {
        preferences.edit().putString(KEY_APP_THEME_MODE, mode.storedValue).apply()
    }

    companion object {
        private const val PREFERENCE_FILE = "bus_is_coming_appearance"
        private const val KEY_APP_THEME_MODE = "app_theme_mode"
    }
}
