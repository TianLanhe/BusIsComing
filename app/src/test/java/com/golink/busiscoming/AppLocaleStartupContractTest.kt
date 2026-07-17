package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLocaleStartupContractTest {
    private val application = File("src/main/java/com/golink/busiscoming/BusIsComingApplication.kt").readText()
    private val repository = File("src/main/java/com/golink/busiscoming/data/local/AppLanguageRepository.kt")

    @Test
    fun theSingleApplicationCoordinatesThemeAndLocaleStartup() {
        assertTrue(application.contains("AppThemePreferenceStore(this).getMode()"))
        assertTrue(application.contains("AppLanguageRepository(this).applyStoredChoice()"))
        assertFalse(application.contains("class LocaleApplication"))
    }

    @Test
    fun languagePreferenceIsIndependentAndUsesAppCompatLocales() {
        assertTrue("Missing AppLanguageRepository", repository.isFile)
        val source = repository.readText()
        assertTrue(source.contains("bus_is_coming_language"))
        assertTrue(source.contains("app_language_choice"))
        assertTrue(source.contains("AppCompatDelegate.setApplicationLocales"))
        assertTrue(source.contains("LocaleListCompat.getEmptyLocaleList()"))
        assertFalse(source.contains("app_theme_mode"))
    }
}
