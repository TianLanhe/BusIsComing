package com.golink.busiscoming

import androidx.appcompat.app.AppCompatDelegate
import com.golink.busiscoming.data.model.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun modesExposeStableStorageValuesAndNightModeMappings() {
        assertEquals("system", AppThemeMode.SYSTEM.storedValue)
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppThemeMode.SYSTEM.nightMode)
        assertEquals("light", AppThemeMode.LIGHT.storedValue)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppThemeMode.LIGHT.nightMode)
        assertEquals("dark", AppThemeMode.DARK.storedValue)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppThemeMode.DARK.nightMode)
    }

    @Test
    fun missingBlankOrUnknownStorageValuesFallBackToSystem() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStoredValue(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStoredValue(""))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStoredValue("  "))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStoredValue("amoled"))
    }

    @Test
    fun knownStorageValuesRoundTrip() {
        AppThemeMode.entries.forEach { mode ->
            assertEquals(mode, AppThemeMode.fromStoredValue(mode.storedValue))
        }
    }
}
