package com.golink.busiscoming.data.localization

import java.util.Locale
import android.content.Context
import com.golink.busiscoming.data.local.AppLanguageRepository

enum class AppLanguageChoice(val storedValue: String) {
    FOLLOW_SYSTEM("system"),
    TRADITIONAL_CHINESE("zh-Hant"),
    SIMPLIFIED_CHINESE("zh-Hans"),
    ENGLISH("en");

    companion object {
        fun fromStoredValue(value: String?): AppLanguageChoice =
            entries.firstOrNull { it.storedValue == value?.trim() } ?: FOLLOW_SYSTEM
    }
}

enum class AppLanguage(val localeTag: String) {
    TRADITIONAL_CHINESE("zh-Hant-HK"),
    SIMPLIFIED_CHINESE("zh-Hans-CN"),
    ENGLISH("en")
}

object AppLanguagePolicy {
    fun resolve(choice: AppLanguageChoice, systemLocale: Locale): AppLanguage = when (choice) {
        AppLanguageChoice.TRADITIONAL_CHINESE -> AppLanguage.TRADITIONAL_CHINESE
        AppLanguageChoice.SIMPLIFIED_CHINESE -> AppLanguage.SIMPLIFIED_CHINESE
        AppLanguageChoice.ENGLISH -> AppLanguage.ENGLISH
        AppLanguageChoice.FOLLOW_SYSTEM -> resolveSystemLocale(systemLocale)
    }

    private fun resolveSystemLocale(locale: Locale): AppLanguage {
        return when (locale.language.lowercase(Locale.ROOT)) {
            "en" -> AppLanguage.ENGLISH
            "zh" -> when {
                locale.script.equals("Hans", ignoreCase = true) -> AppLanguage.SIMPLIFIED_CHINESE
                locale.script.equals("Hant", ignoreCase = true) -> AppLanguage.TRADITIONAL_CHINESE
                locale.country.uppercase(Locale.ROOT) in setOf("CN", "SG") ->
                    AppLanguage.SIMPLIFIED_CHINESE
                else -> AppLanguage.TRADITIONAL_CHINESE
            }
            else -> AppLanguage.TRADITIONAL_CHINESE
        }
    }
}

enum class TtsLanguageFamily {
    TRADITIONAL_CHINESE,
    SIMPLIFIED_CHINESE,
    ENGLISH
}

class LanguageVersionTracker(initialVersion: Long = 1L) {
    private var version = initialVersion
    private var lastChoice: AppLanguageChoice? = null
    private var lastEffectiveLanguage: AppLanguage? = null

    @Synchronized
    fun versionFor(
        choice: AppLanguageChoice,
        effectiveLanguage: AppLanguage
    ): Long {
        if (lastChoice == null) {
            lastChoice = choice
            lastEffectiveLanguage = effectiveLanguage
            return version
        }
        if (choice != lastChoice || effectiveLanguage != lastEffectiveLanguage) {
            version += 1L
            lastChoice = choice
            lastEffectiveLanguage = effectiveLanguage
        }
        return version
    }
}

data class LanguageSnapshot(
    val choice: AppLanguageChoice,
    val effectiveLanguage: AppLanguage,
    val localeTag: String,
    val citybusLanguage: String,
    val googleLanguageCode: String,
    val googleRegionCode: String,
    val dataGovFieldOrder: List<String>,
    val ttsLanguageFamily: TtsLanguageFamily,
    val websitePath: String,
    val privacyPath: String,
    val version: Long
) {
    companion object {
        fun create(
            choice: AppLanguageChoice,
            effectiveLanguage: AppLanguage,
            version: Long
        ): LanguageSnapshot = when (effectiveLanguage) {
            AppLanguage.TRADITIONAL_CHINESE -> LanguageSnapshot(
                choice = choice,
                effectiveLanguage = effectiveLanguage,
                localeTag = effectiveLanguage.localeTag,
                citybusLanguage = "0",
                googleLanguageCode = "zh-Hant",
                googleRegionCode = "HK",
                dataGovFieldOrder = listOf("tc", "sc", "en"),
                ttsLanguageFamily = TtsLanguageFamily.TRADITIONAL_CHINESE,
                websitePath = "/zh-hant/",
                privacyPath = "/zh-hant/privacy/",
                version = version
            )
            AppLanguage.SIMPLIFIED_CHINESE -> LanguageSnapshot(
                choice = choice,
                effectiveLanguage = effectiveLanguage,
                localeTag = effectiveLanguage.localeTag,
                citybusLanguage = "2",
                googleLanguageCode = "zh-Hans",
                googleRegionCode = "HK",
                dataGovFieldOrder = listOf("sc", "tc", "en"),
                ttsLanguageFamily = TtsLanguageFamily.SIMPLIFIED_CHINESE,
                websitePath = "/zh-hans/",
                privacyPath = "/zh-hans/privacy/",
                version = version
            )
            AppLanguage.ENGLISH -> LanguageSnapshot(
                choice = choice,
                effectiveLanguage = effectiveLanguage,
                localeTag = effectiveLanguage.localeTag,
                citybusLanguage = "1",
                googleLanguageCode = "en",
                googleRegionCode = "HK",
                dataGovFieldOrder = listOf("en", "tc", "sc"),
                ttsLanguageFamily = TtsLanguageFamily.ENGLISH,
                websitePath = "/en/",
                privacyPath = "/en/privacy/",
                version = version
            )
        }
    }
}

object AppLanguageRuntime {
    @Volatile
    private var repository: AppLanguageRepository? = null

    fun initialize(context: Context) {
        repository = AppLanguageRepository(context.applicationContext)
    }

    fun snapshot(): LanguageSnapshot = repository?.snapshot() ?: LanguageSnapshot.create(
        choice = AppLanguageChoice.FOLLOW_SYSTEM,
        effectiveLanguage = AppLanguage.TRADITIONAL_CHINESE,
        version = 1L
    )
}
