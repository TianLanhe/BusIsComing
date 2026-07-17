package com.golink.busiscoming

import com.golink.busiscoming.data.localization.AppLanguage
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.localization.AppLanguagePolicy
import com.golink.busiscoming.data.localization.LanguageVersionTracker
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguagePolicyTest {
    @Test
    fun explicitChoicesAlwaysWinOverSystemLocale() {
        assertEquals(
            AppLanguage.TRADITIONAL_CHINESE,
            AppLanguagePolicy.resolve(AppLanguageChoice.TRADITIONAL_CHINESE, Locale.ENGLISH)
        )
        assertEquals(
            AppLanguage.SIMPLIFIED_CHINESE,
            AppLanguagePolicy.resolve(AppLanguageChoice.SIMPLIFIED_CHINESE, Locale.UK)
        )
        assertEquals(
            AppLanguage.ENGLISH,
            AppLanguagePolicy.resolve(AppLanguageChoice.ENGLISH, Locale.TRADITIONAL_CHINESE)
        )
    }

    @Test
    fun followSystemRecognizesEnglishChineseScriptsAndRegions() {
        listOf("en-US", "en-GB", "en-AU").forEach { tag ->
            assertEquals(AppLanguage.ENGLISH, resolveSystem(tag))
        }
        listOf("zh-Hant-HK", "zh-HK", "zh-MO", "zh-TW").forEach { tag ->
            assertEquals(AppLanguage.TRADITIONAL_CHINESE, resolveSystem(tag))
        }
        listOf("zh-Hans-CN", "zh-CN", "zh-SG").forEach { tag ->
            assertEquals(AppLanguage.SIMPLIFIED_CHINESE, resolveSystem(tag))
        }
    }

    @Test
    fun bareChineseAndUnsupportedSystemLanguagesFallBackToTraditionalChinese() {
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, resolveSystem("zh"))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, resolveSystem("ja-JP"))
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, resolveSystem("fr-FR"))
    }

    @Test
    fun choicesUseStableStorageValuesAndUnknownValuesFollowSystem() {
        assertEquals("system", AppLanguageChoice.FOLLOW_SYSTEM.storedValue)
        assertEquals("zh-Hant", AppLanguageChoice.TRADITIONAL_CHINESE.storedValue)
        assertEquals("zh-Hans", AppLanguageChoice.SIMPLIFIED_CHINESE.storedValue)
        assertEquals("en", AppLanguageChoice.ENGLISH.storedValue)
        assertEquals(AppLanguageChoice.FOLLOW_SYSTEM, AppLanguageChoice.fromStoredValue(null))
        assertEquals(AppLanguageChoice.FOLLOW_SYSTEM, AppLanguageChoice.fromStoredValue("unknown"))
    }

    @Test
    fun languageVersionAdvancesWhenChoiceOrResolvedSystemLanguageChanges() {
        val tracker = LanguageVersionTracker()

        assertEquals(
            1L,
            tracker.versionFor(AppLanguageChoice.FOLLOW_SYSTEM, AppLanguage.TRADITIONAL_CHINESE)
        )
        assertEquals(
            1L,
            tracker.versionFor(AppLanguageChoice.FOLLOW_SYSTEM, AppLanguage.TRADITIONAL_CHINESE)
        )
        assertEquals(
            2L,
            tracker.versionFor(AppLanguageChoice.FOLLOW_SYSTEM, AppLanguage.ENGLISH)
        )
        assertEquals(
            3L,
            tracker.versionFor(AppLanguageChoice.ENGLISH, AppLanguage.ENGLISH)
        )
        assertEquals(
            3L,
            tracker.versionFor(AppLanguageChoice.ENGLISH, AppLanguage.ENGLISH)
        )
    }

    private fun resolveSystem(tag: String): AppLanguage =
        AppLanguagePolicy.resolve(AppLanguageChoice.FOLLOW_SYSTEM, Locale.forLanguageTag(tag))
}
