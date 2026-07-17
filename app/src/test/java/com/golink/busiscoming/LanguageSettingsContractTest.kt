package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageSettingsContractTest {
    private val source =
        File("src/main/java/com/golink/busiscoming/ui/main/SettingsFragment.kt").readText()

    @Test
    fun settingsOffersFourImmediateLanguageChoicesWithSelfNames() {
        assertTrue(source.contains("AppLanguageChoice.FOLLOW_SYSTEM"))
        assertTrue(source.contains("AppLanguageChoice.TRADITIONAL_CHINESE"))
        assertTrue(source.contains("AppLanguageChoice.SIMPLIFIED_CHINESE"))
        assertTrue(source.contains("AppLanguageChoice.ENGLISH"))
        assertTrue(source.contains("R.string.language_traditional_self"))
        assertTrue(source.contains("R.string.language_simplified_self"))
        assertTrue(source.contains("R.string.language_english_self"))
        assertTrue(source.contains("setSingleChoiceItems"))
        assertTrue(source.contains("languageRepository.setChoice(selectedChoice)"))
        assertFalse(source.contains("unsupported_language_switch"))
    }

    @Test
    fun followSystemSummaryIncludesTheResolvedLanguage() {
        assertTrue(source.contains("R.string.language_follow_system_with_actual"))
        assertTrue(source.contains("settingsLanguageValue"))
        assertTrue(source.contains("snapshot.effectiveLanguage"))
    }
}
