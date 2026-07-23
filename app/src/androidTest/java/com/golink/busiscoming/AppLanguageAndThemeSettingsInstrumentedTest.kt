package com.golink.busiscoming

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.MenuItemCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.ui.main.MainActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguageAndThemeSettingsInstrumentedTest {
    private lateinit var context: Context
    private lateinit var originalLanguage: AppLanguageChoice
    private lateinit var originalTheme: AppThemeMode

    @get:Rule
    val locationPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @Before
    fun prepareKnownPreferences() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        originalLanguage = AppLanguageRepository(context).getChoice()
        originalTheme = AppThemePreferenceStore(context).getMode()
        AppThemePreferenceStore(context).setMode(AppThemeMode.LIGHT)
        AppCompatDelegate.setDefaultNightMode(AppThemeMode.LIGHT.nightMode)
        AppLanguageRepository(context).setChoice(AppLanguageChoice.TRADITIONAL_CHINESE)
    }

    @After
    fun restorePreferences() {
        AppThemePreferenceStore(context).setMode(originalTheme)
        AppCompatDelegate.setDefaultNightMode(originalTheme.nightMode)
        AppLanguageRepository(context).setChoice(originalLanguage)
    }

    @Test
    fun languageAndAppearanceRemainIndependentAcrossAppCompatRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_settings)).perform(click())
            waitForSettingsValues("淺色模式", "繁體中文")
            scenario.onActivity { activity ->
                assertBottomNavigationCopy(
                    activity,
                    journeys = "行程",
                    routes = "路線",
                    settings = "設定",
                    journeysDescription = "已儲存行程",
                    routesDescription = "搜尋巴士路線"
                )
            }

            onView(withId(R.id.settingsAppearanceRow)).perform(click())
            onView(withText("淺色模式")).inRoot(isDialog()).check(matches(isChecked()))
            onView(withText("深色模式")).inRoot(isDialog()).perform(click())

            waitUntil {
                AppThemePreferenceStore(context).getMode() == AppThemeMode.DARK &&
                    AppLanguageRepository(context).getChoice() == AppLanguageChoice.TRADITIONAL_CHINESE
            }
            waitForSettingsValues("深色模式", "繁體中文")
            scenario.onActivity { activity ->
                val nightMode = activity.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                assertEquals(Configuration.UI_MODE_NIGHT_YES, nightMode)
            }

            onView(withId(R.id.settingsLanguageRow)).perform(click())
            onView(withText("繁體中文")).inRoot(isDialog()).check(matches(isChecked()))
            onView(withText("简体中文")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText("简体中文")).inRoot(isDialog()).perform(click())

            waitUntil {
                AppLanguageRepository(context).getChoice() == AppLanguageChoice.SIMPLIFIED_CHINESE &&
                    AppThemePreferenceStore(context).getMode() == AppThemeMode.DARK
            }
            waitForSettingsValues("深色模式", "简体中文")
            scenario.onActivity { activity ->
                assertBottomNavigationCopy(
                    activity,
                    journeys = "行程",
                    routes = "路线",
                    settings = "设置",
                    journeysDescription = "已保存行程",
                    routesDescription = "搜索公交路线"
                )
            }
            scenario.onActivity { activity ->
                val nightMode = activity.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                assertEquals(Configuration.UI_MODE_NIGHT_YES, nightMode)
            }

            onView(withId(R.id.settingsAppearanceRow)).perform(click())
            onView(withText("深色模式")).inRoot(isDialog()).check(matches(isChecked()))
            onView(withText("浅色模式")).inRoot(isDialog()).perform(click())
            waitUntil {
                AppLanguageRepository(context).getChoice() == AppLanguageChoice.SIMPLIFIED_CHINESE &&
                    AppThemePreferenceStore(context).getMode() == AppThemeMode.LIGHT
            }
            waitForSettingsValues("浅色模式", "简体中文")

            onView(withId(R.id.settingsLanguageRow)).perform(click())
            onView(withText("English")).inRoot(isDialog()).perform(click())
            waitUntil {
                AppLanguageRepository(context).getChoice() == AppLanguageChoice.ENGLISH &&
                    AppThemePreferenceStore(context).getMode() == AppThemeMode.LIGHT
            }
            waitForSettingsValues("Light", "English")
            scenario.onActivity { activity ->
                assertBottomNavigationCopy(
                    activity,
                    journeys = "Journeys",
                    routes = "Routes",
                    settings = "Settings",
                    journeysDescription = "Saved journeys",
                    routesDescription = "Find bus routes"
                )
            }

            onView(withId(R.id.settingsAppearanceRow)).perform(click())
            onView(withText("Dark")).inRoot(isDialog()).perform(click())
            waitUntil {
                AppLanguageRepository(context).getChoice() == AppLanguageChoice.ENGLISH &&
                    AppThemePreferenceStore(context).getMode() == AppThemeMode.DARK
            }
            waitForSettingsValues("Dark", "English")

            lateinit var activityBeforeReselect: MainActivity
            scenario.onActivity { activityBeforeReselect = it }
            onView(withId(R.id.settingsAppearanceRow)).perform(click())
            onView(withText("Dark")).inRoot(isDialog()).check(matches(isChecked())).perform(click())
            onView(withId(R.id.settingsRoot)).check(matches(isDisplayed()))
            scenario.onActivity { activityAfterReselect ->
                assertSame(activityBeforeReselect, activityAfterReselect)
            }
        }
    }

    private fun assertBottomNavigationCopy(
        activity: MainActivity,
        journeys: String,
        routes: String,
        settings: String,
        journeysDescription: String,
        routesDescription: String
    ) {
        val menu = activity.findViewById<BottomNavigationView>(R.id.topLevelNav).menu
        val journeysItem = menu.findItem(R.id.navigation_frequent_routes)
        val routesItem = menu.findItem(R.id.navigation_search)
        val settingsItem = menu.findItem(R.id.navigation_settings)

        assertEquals(journeys, journeysItem.title.toString())
        assertEquals(journeysDescription, MenuItemCompat.getContentDescription(journeysItem).toString())
        assertEquals(routes, routesItem.title.toString())
        assertEquals(routesDescription, MenuItemCompat.getContentDescription(routesItem).toString())
        assertEquals(settings, settingsItem.title.toString())
        assertEquals(settings, MenuItemCompat.getContentDescription(settingsItem).toString())
    }

    private fun waitForSettingsValues(appearance: String, language: String) {
        waitUntil {
            try {
                onView(withId(R.id.settingsRoot)).check(matches(isDisplayed()))
                onView(withId(R.id.settingsAppearanceValue)).check(matches(withText(appearance)))
                onView(withId(R.id.settingsLanguageValue)).check(matches(withText(language)))
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun waitUntil(timeoutMillis: Long = 8_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue("Condition did not become true within ${timeoutMillis}ms", condition())
    }
}
