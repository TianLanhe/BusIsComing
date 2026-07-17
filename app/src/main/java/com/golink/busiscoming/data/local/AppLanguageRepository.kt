package com.golink.busiscoming.data.local

import android.content.Context
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.localization.AppLanguagePolicy
import com.golink.busiscoming.data.localization.LanguageSnapshot
import com.golink.busiscoming.data.localization.LanguageVersionTracker
import java.util.Locale

class AppLanguageRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(
        PREFERENCE_FILE,
        Context.MODE_PRIVATE
    )

    fun getChoice(): AppLanguageChoice =
        AppLanguageChoice.fromStoredValue(preferences.getString(KEY_APP_LANGUAGE_CHOICE, null))

    fun snapshot(): LanguageSnapshot {
        val choice = getChoice()
        val effective = AppLanguagePolicy.resolve(choice, systemLocale())
        return LanguageSnapshot.create(
            choice,
            effective,
            LANGUAGE_VERSION_TRACKER.versionFor(choice, effective)
        )
    }

    fun setChoice(choice: AppLanguageChoice): LanguageSnapshot {
        if (choice != getChoice()) {
            preferences.edit().putString(KEY_APP_LANGUAGE_CHOICE, choice.storedValue).apply()
        }
        applyChoice(choice)
        return snapshot()
    }

    fun applyStoredChoice() {
        applyChoice(getChoice())
    }

    private fun applyChoice(choice: AppLanguageChoice) {
        val locales = when (choice) {
            AppLanguageChoice.FOLLOW_SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguageChoice.TRADITIONAL_CHINESE ->
                LocaleListCompat.forLanguageTags("zh-Hant-HK")
            AppLanguageChoice.SIMPLIFIED_CHINESE ->
                LocaleListCompat.forLanguageTags("zh-Hans-CN")
            AppLanguageChoice.ENGLISH -> LocaleListCompat.forLanguageTags("en")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun systemLocale(): Locale {
        val configuration = Resources.getSystem().configuration
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            configuration.locale
        }
    }

    companion object {
        private const val PREFERENCE_FILE = "bus_is_coming_language"
        private const val KEY_APP_LANGUAGE_CHOICE = "app_language_choice"
        private val LANGUAGE_VERSION_TRACKER = LanguageVersionTracker()
    }
}
