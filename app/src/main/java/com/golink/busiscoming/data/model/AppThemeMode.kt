package com.golink.busiscoming.data.model

import androidx.appcompat.app.AppCompatDelegate

enum class AppThemeMode(
    val storedValue: String,
    val nightMode: Int
) {
    SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
    DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        fun fromStoredValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.storedValue == value?.trim() } ?: SYSTEM
    }
}
