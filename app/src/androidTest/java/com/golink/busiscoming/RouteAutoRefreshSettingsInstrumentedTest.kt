package com.golink.busiscoming

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.local.RouteAutoRefreshInterval
import com.golink.busiscoming.data.local.RouteAutoRefreshSettingsStore
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.ui.main.MainActivity
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteAutoRefreshSettingsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var originalLanguage: AppLanguageChoice
    private lateinit var originalTheme: AppThemeMode
    private lateinit var originalPreferences: Map<String, *>

    @Before
    fun captureEnvironment() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString(ARG_RUN_SETTINGS_MATRIX) == "true"
        )
        originalLanguage = AppLanguageRepository(context).getChoice()
        originalTheme = AppThemePreferenceStore(context).getMode()
        originalPreferences = preferences().all.toMap()
        preferences().edit().clear().commit()
        executeShell("wm size 1080x2400")
        executeShell("wm density 480")
    }

    @After
    fun restoreEnvironment() {
        preferences().edit().clear().apply {
            originalPreferences.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                }
            }
        }.commit()
        executeShell("settings put system font_scale 1.0")
        executeShell("wm size reset")
        executeShell("wm density reset")
        AppThemePreferenceStore(context).setMode(originalTheme)
        AppCompatDelegate.setDefaultNightMode(originalTheme.nightMode)
        AppLanguageRepository(context).setChoice(originalLanguage)
    }

    @Test
    fun settingsRowFitsLanguageThemeFontMatrixAndDialogExposesSelectedSemantics() {
        val languages = listOf(
            AppLanguageChoice.TRADITIONAL_CHINESE,
            AppLanguageChoice.SIMPLIFIED_CHINESE,
            AppLanguageChoice.ENGLISH
        )
        val themes = listOf(AppThemeMode.LIGHT, AppThemeMode.DARK)
        val fontScales = listOf(1.0f, 1.3f, 2.0f)

        fontScales.forEach { fontScale ->
            executeShell("settings put system font_scale $fontScale")
            languages.forEach { language ->
                themes.forEach { theme ->
                    configure(language, theme)
                    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                        onView(withId(R.id.navigation_settings)).perform(click())
                        waitForSettings()
                        scenario.onActivity { activity ->
                            val row = activity.findViewById<View>(R.id.settingsAutoRefreshRow)
                            val value = activity.findViewById<TextView>(R.id.settingsAutoRefreshValue)
                            assertEquals(
                                activity.getString(R.string.auto_refresh_one_minute),
                                value.text.toString()
                            )
                            val minimum = (48f * activity.resources.displayMetrics.density).toInt()
                            assertTrue(row.height >= minimum)
                            assertTrue(row.isClickable)
                            assertTrue(row.isFocusable)
                            assertEquals(
                                activity.getString(
                                    R.string.settings_auto_refresh_value_accessibility,
                                    value.text
                                ),
                                row.contentDescription.toString()
                            )
                            val night = activity.resources.configuration.uiMode and
                                Configuration.UI_MODE_NIGHT_MASK
                            assertEquals(
                                if (theme == AppThemeMode.DARK) Configuration.UI_MODE_NIGHT_YES
                                else Configuration.UI_MODE_NIGHT_NO,
                                night
                            )
                            saveScreenshot(
                                activity,
                                "auto-refresh-settings-${language.name.lowercase()}-${theme.name.lowercase()}-$fontScale.png"
                            )
                            assertTrue(row.performClick())
                        }
                        instrumentation.waitForIdleSync()
                        onView(withText(R.string.auto_refresh_one_minute))
                            .inRoot(isDialog())
                            .check(matches(isChecked()))
                        pressBack()
                    }
                }
            }
        }
    }

    @Test
    fun everyDialogChoicePersistsAndReselectionSurvivesRecreation() {
        configure(AppLanguageChoice.ENGLISH, AppThemeMode.LIGHT)
        executeShell("settings put system font_scale 1.0")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_settings)).perform(click())
            waitForSettings()
            RouteAutoRefreshInterval.entries.forEachIndexed { index, interval ->
                onView(withId(R.id.settingsAutoRefreshRow)).perform(click())
                onView(withText(choiceLabel(index))).inRoot(isDialog()).perform(click())
                instrumentation.waitForIdleSync()
                assertEquals(interval, RouteAutoRefreshSettingsStore(context).getInterval())
                scenario.onActivity { activity ->
                    assertEquals(
                        activity.getString(choiceLabel(index)),
                        activity.findViewById<TextView>(R.id.settingsAutoRefreshValue).text.toString()
                    )
                }
            }
            onView(withId(R.id.settingsAutoRefreshRow)).perform(click())
            onView(withText(R.string.auto_refresh_ten_minutes)).inRoot(isDialog()).perform(click())
            scenario.recreate()
            waitForSettings()
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(R.string.auto_refresh_ten_minutes),
                    activity.findViewById<TextView>(R.id.settingsAutoRefreshValue).text.toString()
                )
                assertEquals(RouteAutoRefreshInterval.MINUTES_10, RouteAutoRefreshSettingsStore(activity).getInterval())
            }
        }
    }

    private fun configure(language: AppLanguageChoice, theme: AppThemeMode) {
        AppThemePreferenceStore(context).setMode(theme)
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        AppLanguageRepository(context).setChoice(language)
        executeShell("cmd locale set-app-locales ${context.packageName} --locales ${language.localeTag()}")
    }

    private fun choiceLabel(index: Int): Int = listOf(
        R.string.auto_refresh_off,
        R.string.auto_refresh_one_minute,
        R.string.auto_refresh_two_minutes,
        R.string.auto_refresh_five_minutes,
        R.string.auto_refresh_ten_minutes
    )[index]

    private fun waitForSettings() {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var shown = false
            try {
                onView(withId(R.id.settingsRoot)).check { view, error ->
                    if (error != null) throw error
                    shown = view.visibility == View.VISIBLE
                }
            } catch (_: Throwable) {
                shown = false
            }
            if (shown) return
            SystemClock.sleep(100L)
        }
        onView(withId(R.id.settingsRoot)).check { view, error ->
            if (error != null) throw error
            assertTrue(view.visibility == View.VISIBLE)
        }
    }

    private fun saveScreenshot(activity: MainActivity, name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val output = File(requireNotNull(activity.getExternalFilesDir(null)), name)
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun preferences() = context.getSharedPreferences(
        RouteAutoRefreshSettingsStore.PREFERENCE_FILE,
        Context.MODE_PRIVATE
    )

    private fun AppLanguageChoice.localeTag(): String = when (this) {
        AppLanguageChoice.FOLLOW_SYSTEM -> ""
        AppLanguageChoice.TRADITIONAL_CHINESE -> "zh-Hant"
        AppLanguageChoice.SIMPLIFIED_CHINESE -> "zh-Hans"
        AppLanguageChoice.ENGLISH -> "en"
    }

    private fun executeShell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
        }
    }

    private companion object {
        const val ARG_RUN_SETTINGS_MATRIX = "runRouteAutoRefreshSettings"
    }
}
