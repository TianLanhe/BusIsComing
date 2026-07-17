package com.golink.busiscoming

import com.golink.busiscoming.data.localization.AppLanguage
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.localization.LanguageSnapshot
import com.golink.busiscoming.data.localization.TtsLanguageFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageSnapshotTest {
    @Test
    fun providerMappingsAreCompleteForEverySupportedLanguage() {
        assertMapping(
            language = AppLanguage.TRADITIONAL_CHINESE,
            localeTag = "zh-Hant-HK",
            citybus = "0",
            google = "zh-Hant",
            fields = listOf("tc", "sc", "en"),
            tts = TtsLanguageFamily.TRADITIONAL_CHINESE,
            websitePath = "/zh-hant/",
            privacyPath = "/zh-hant/privacy/"
        )
        assertMapping(
            language = AppLanguage.SIMPLIFIED_CHINESE,
            localeTag = "zh-Hans-CN",
            citybus = "2",
            google = "zh-Hans",
            fields = listOf("sc", "tc", "en"),
            tts = TtsLanguageFamily.SIMPLIFIED_CHINESE,
            websitePath = "/zh-hans/",
            privacyPath = "/zh-hans/privacy/"
        )
        assertMapping(
            language = AppLanguage.ENGLISH,
            localeTag = "en",
            citybus = "1",
            google = "en",
            fields = listOf("en", "tc", "sc"),
            tts = TtsLanguageFamily.ENGLISH,
            websitePath = "/en/",
            privacyPath = "/en/privacy/"
        )
    }

    private fun assertMapping(
        language: AppLanguage,
        localeTag: String,
        citybus: String,
        google: String,
        fields: List<String>,
        tts: TtsLanguageFamily,
        websitePath: String,
        privacyPath: String
    ) {
        val snapshot = LanguageSnapshot.create(
            choice = AppLanguageChoice.FOLLOW_SYSTEM,
            effectiveLanguage = language,
            version = 9L
        )
        assertEquals(localeTag, snapshot.localeTag)
        assertEquals(citybus, snapshot.citybusLanguage)
        assertEquals(google, snapshot.googleLanguageCode)
        assertEquals("HK", snapshot.googleRegionCode)
        assertEquals(fields, snapshot.dataGovFieldOrder)
        assertEquals(tts, snapshot.ttsLanguageFamily)
        assertEquals(websitePath, snapshot.websitePath)
        assertEquals(privacyPath, snapshot.privacyPath)
        assertEquals(9L, snapshot.version)
    }
}
