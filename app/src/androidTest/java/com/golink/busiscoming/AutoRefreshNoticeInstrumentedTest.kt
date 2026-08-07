package com.golink.busiscoming

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.local.AutoRefreshNoticeStore
import com.golink.busiscoming.data.local.RouteAutoRefreshInterval
import com.golink.busiscoming.data.local.RouteAutoRefreshSettingsStore
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.ui.main.AutoRefreshNoticeController
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.SearchFragment
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoRefreshNoticeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var originalLanguage: AppLanguageChoice
    private lateinit var originalTheme: AppThemeMode
    private lateinit var originalPreferences: Map<String, *>

    @Before
    fun prepare() {
        assumeTrue(InstrumentationRegistry.getArguments().getString(ARG_RUN_NOTICE) == "true")
        originalLanguage = AppLanguageRepository(context).getChoice()
        originalTheme = AppThemePreferenceStore(context).getMode()
        originalPreferences = preferences().all.toMap()
        preferences().edit().clear().commit()
        executeShell("wm size 1080x2400")
        executeShell("wm density 480")
    }

    @After
    fun restore() {
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
        AppThemePreferenceStore(context).setMode(originalTheme)
        AppCompatDelegate.setDefaultNightMode(originalTheme.nightMode)
        AppLanguageRepository(context).setChoice(originalLanguage)
        executeShell("settings put system font_scale 1.0")
        executeShell("wm size reset")
        executeShell("wm density reset")
    }

    @Test
    fun interruptionDoesNotCompleteAndSearchSettingsActionCompletesWithDeepFocus() {
        configure(AppLanguageChoice.TRADITIONAL_CHINESE, AppThemeMode.LIGHT, 1.0f)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> frequentController(activity).showAfterSuccessfulQuery(RouteAutoRefreshInterval.MINUTES_1) }
            waitForNotice(scenario, visible = true)
            scenario.onActivity(::assertNoticeStructure)
            assertFalse(AutoRefreshNoticeStore(context).isComplete())

            onView(withId(R.id.navigation_search)).perform(click())
            waitForNotice(scenario, visible = false)
            assertFalse(AutoRefreshNoticeStore(context).isComplete())

            scenario.onActivity { activity ->
                val search = activity.supportFragmentManager.fragments.filterIsInstance<SearchFragment>().single()
                searchController(search).showAfterSuccessfulQuery(RouteAutoRefreshInterval.MINUTES_1)
            }
            waitForNotice(scenario, visible = true)
            scenario.onActivity { activity ->
                requireNotNull(activeNotice(activity))
                    .findViewById<View>(R.id.autoRefreshNoticeSettings)
                    .performClick()
            }
            assertTrue(AutoRefreshNoticeStore(context).isComplete())
            waitForSettingsFocus(scenario)
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.settingsRoot).isShown)
                val row = activity.findViewById<View>(R.id.settingsAutoRefreshRow)
                assertTrue(row.hasFocus() || row.findFocus() != null)
            }
        }
    }

    @Test
    fun naturalCompletionPersistsAndPreventsReplay() {
        configure(AppLanguageChoice.ENGLISH, AppThemeMode.LIGHT, 1.0f)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> frequentController(activity).showAfterSuccessfulQuery(RouteAutoRefreshInterval.MINUTES_1) }
            waitForNotice(scenario, visible = true)
            waitForNotice(scenario, visible = false, timeoutMillis = 8_000L)
            assertTrue(AutoRefreshNoticeStore(context).isComplete())

            scenario.onActivity { activity -> frequentController(activity).showAfterSuccessfulQuery(RouteAutoRefreshInterval.MINUTES_1) }
            waitForNotice(scenario, visible = false)
        }
    }

    @Test
    fun noticeFitsThreeLanguagesThemesAndLargeTextWithoutEllipsis() {
        val languages = listOf(
            AppLanguageChoice.TRADITIONAL_CHINESE,
            AppLanguageChoice.SIMPLIFIED_CHINESE,
            AppLanguageChoice.ENGLISH
        )
        val themes = listOf(AppThemeMode.LIGHT, AppThemeMode.DARK)
        languages.forEach { language ->
            themes.forEach { theme ->
                preferences().edit().clear().commit()
                configure(language, theme, 2.0f)
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        frequentController(activity).showAfterSuccessfulQuery(RouteAutoRefreshInterval.MINUTES_10)
                    }
                    waitForNotice(scenario, visible = true)
                    SystemClock.sleep(250L)
                    scenario.onActivity { activity ->
                        assertNoticeStructure(activity)
                        val card = activity.findViewById<MaterialCardView>(R.id.autoRefreshNotice)
                        assertNoEllipsis(card)
                        assertEquals(
                            LinearLayout.VERTICAL,
                            card.findViewById<LinearLayout>(R.id.autoRefreshNoticeBody).orientation
                        )
                        saveScreenshot(
                            activity,
                            "auto-refresh-notice-${language.name.lowercase()}-${theme.name.lowercase()}-2.0.png"
                        )
                    }
                }
            }
        }
    }

    private fun assertNoticeStructure(activity: MainActivity) {
        val card = activity.findViewById<MaterialCardView>(R.id.autoRefreshNotice)
        assertTrue(card.isShown)
        assertEquals(dp(activity, 14f), card.radius, 1f)
        assertEquals(dp(activity, 1f).toInt(), card.strokeWidth)
        val settings = card.findViewById<View>(R.id.autoRefreshNoticeSettings)
        assertTrue(settings.isClickable)
        assertTrue(settings.height >= dp(activity, 48f))
    }

    private fun frequentController(activity: MainActivity): AutoRefreshNoticeController =
        requireNotNull(
            activity.javaClass.getDeclaredField("autoRefreshNoticeController").apply {
                isAccessible = true
            }.get(activity) as? AutoRefreshNoticeController
        )

    private fun searchController(fragment: SearchFragment): AutoRefreshNoticeController =
        requireNotNull(
            fragment.javaClass.getDeclaredField("autoRefreshNoticeController").apply {
                isAccessible = true
            }.get(fragment) as? AutoRefreshNoticeController
        )

    private fun waitForNotice(
        scenario: ActivityScenario<MainActivity>,
        visible: Boolean,
        timeoutMillis: Long = 4_000L
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            var shown = false
            scenario.onActivity { activity ->
                shown = activeNotice(activity)?.isShown == true
            }
            if (shown == visible) return
            SystemClock.sleep(100L)
        }
        scenario.onActivity { activity ->
            assertEquals(visible, activeNotice(activity)?.isShown == true)
        }
    }

    private fun waitForSettingsFocus(
        scenario: ActivityScenario<MainActivity>,
        timeoutMillis: Long = 2_000L
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            var focused = false
            scenario.onActivity { activity ->
                val row = activity.findViewById<View>(R.id.settingsAutoRefreshRow)
                focused = row.hasFocus() || row.findFocus() != null
            }
            if (focused) return
            SystemClock.sleep(50L)
        }
    }

    private fun activeNotice(activity: MainActivity): View? {
        val activeRoot = listOf(
            activity.findViewById<ViewGroup>(R.id.frequentRoutesRoot),
            activity.findViewById<ViewGroup>(R.id.searchRoot)
        ).firstOrNull(View::isShown)
        return activeRoot?.findViewById(R.id.autoRefreshNotice)
    }

    private fun assertNoEllipsis(view: View) {
        if (view is TextView && view.layout != null) {
            for (line in 0 until view.layout.lineCount) {
                assertEquals("Unexpected ellipsis in '${view.text}'", 0, view.layout.getEllipsisCount(line))
            }
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) assertNoEllipsis(view.getChildAt(index))
    }

    private fun configure(language: AppLanguageChoice, theme: AppThemeMode, fontScale: Float) {
        executeShell("settings put system font_scale $fontScale")
        AppThemePreferenceStore(context).setMode(theme)
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        AppLanguageRepository(context).setChoice(language)
        executeShell("cmd locale set-app-locales ${context.packageName} --locales ${language.localeTag()}")
    }

    private fun saveScreenshot(activity: MainActivity, name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val output = File(requireNotNull(activity.getExternalFilesDir(null)), name)
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun dp(activity: MainActivity, value: Float): Float =
        value * activity.resources.displayMetrics.density

    private fun preferences() = context.getSharedPreferences(
        RouteAutoRefreshSettingsStore.PREFERENCE_FILE,
        Context.MODE_PRIVATE
    )

    private fun executeShell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
        }
    }

    private fun AppLanguageChoice.localeTag(): String = when (this) {
        AppLanguageChoice.FOLLOW_SYSTEM -> ""
        AppLanguageChoice.TRADITIONAL_CHINESE -> "zh-Hant"
        AppLanguageChoice.SIMPLIFIED_CHINESE -> "zh-Hans"
        AppLanguageChoice.ENGLISH -> "en"
    }

    private companion object {
        const val ARG_RUN_NOTICE = "runAutoRefreshNotice"
    }
}
