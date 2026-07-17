package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeStartupContractTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val applicationSource =
        File("src/main/java/com/golink/busiscoming/BusIsComingApplication.kt")
    private val storeSource =
        File("src/main/java/com/golink/busiscoming/data/local/AppThemePreferenceStore.kt")

    @Test
    fun manifestRegistersOneApplicationWithoutUiModeConfigChanges() {
        assertTrue(manifest.contains("android:name=\".BusIsComingApplication\""))
        assertFalse(manifest.contains("android:configChanges=\"uiMode"))
        assertFalse(manifest.contains("|uiMode"))
    }

    @Test
    fun applicationAppliesStoredThemeBeforeAnyActivityStarts() {
        assertTrue("Missing BusIsComingApplication", applicationSource.isFile)
        val source = applicationSource.readText()
        assertTrue(source.contains("AppThemePreferenceStore(this).getMode()"))
        assertTrue(source.contains("AppCompatDelegate.setDefaultNightMode"))
    }

    @Test
    fun themeStoreUsesAnIndependentPreferenceFileAndStableKey() {
        assertTrue("Missing AppThemePreferenceStore", storeSource.isFile)
        val source = storeSource.readText()
        assertTrue(source.contains("bus_is_coming_appearance"))
        assertTrue(source.contains("app_theme_mode"))
        assertTrue(source.contains("AppThemeMode.fromStoredValue"))
        assertTrue(source.contains("mode.storedValue"))
        assertFalse(source.contains("language"))
    }
}
