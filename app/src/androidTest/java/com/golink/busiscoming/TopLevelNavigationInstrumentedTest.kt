package com.golink.busiscoming

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.repository.RouteConfigRepository
import com.golink.busiscoming.ui.main.MainActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class TopLevelNavigationInstrumentedTest {
    @get:Rule
    val locationPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @Test
    fun topLevelDestinationsSwitchWithoutLeavingTheMainHost() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.frequentRoutesRoot)).check(matches(isDisplayed()))
            onView(withId(R.id.navigation_settings)).perform(click())
            onView(withId(R.id.settingsRoot)).check(matches(isDisplayed()))
            onView(withId(R.id.navigation_search)).perform(click())
            onView(withId(R.id.searchRoot)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun tabSwitchKeepsSearchDraftAndRecreationKeepsSelectedDestination() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            onView(withId(R.id.searchOriginInput)).perform(replaceText("未提交起點"))
            onView(withId(R.id.navigation_settings)).perform(click())
            onView(withId(R.id.navigation_search)).perform(click())
            onView(withId(R.id.searchOriginInput)).check(matches(withText("未提交起點")))

            scenario.recreate()

            onView(withId(R.id.searchRoot)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun selectedBottomItemKeepsItsIndicatorAndStableMeasurementAcrossSwitches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNavigationState(activity, R.id.navigation_frequent_routes)
            }

            onView(withId(R.id.navigation_search)).perform(click())
            scenario.onActivity { activity ->
                assertNavigationState(activity, R.id.navigation_search)
            }

            onView(withId(R.id.navigation_settings)).perform(click())
            scenario.onActivity { activity ->
                assertNavigationState(activity, R.id.navigation_settings)
            }

            scenario.recreate()
            scenario.onActivity { activity ->
                assertNavigationState(activity, R.id.navigation_settings)
            }
        }
    }

    @Test
    fun firstRunKeepsSearchInBottomNavigationAndSecondaryPagesReturnToTheirOwnerDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = RouteConfigRepository(context)
        repository.getAll().forEach { repository.delete(it.id) }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.emptyAddRouteButton)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.navigation_search)).perform(click())
            onView(withId(R.id.searchRoot)).check(matches(isDisplayed()))

            onView(withId(R.id.navigation_settings)).perform(click())
            onView(withId(R.id.settingsAboutRow)).perform(scrollTo(), click())
            onView(withId(R.id.aboutRoot)).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.settingsRoot)).check(matches(isDisplayed()))
        }

        repository.insert(
            "次級頁返回測試",
            Place("起點", 22.3, 114.1),
            Place("終點", 22.4, 114.2)
        )
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.routeManageButton)).perform(click())
            onView(withId(R.id.routeManageRoot)).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.frequentRoutesRoot)).check(matches(isDisplayed()))
        }

        repository.getAll().forEach { repository.delete(it.id) }
        repository.close()
    }

    private fun assertNavigationState(activity: MainActivity, selectedItemId: Int) {
        val navigation = activity.findViewById<BottomNavigationView>(R.id.topLevelNav)
        assertEquals(selectedItemId, navigation.selectedItemId)
        assertTrue(navigation.isItemActiveIndicatorEnabled)
        assertEquals(dp(activity, 28), navigation.itemIconSize)
        val checkedItems = (0 until navigation.menu.size())
            .map { navigation.menu.getItem(it) }
            .filter { it.isChecked }
        assertEquals(1, checkedItems.size)
        assertEquals(selectedItemId, checkedItems.single().itemId)

        val menuView = navigation.getChildAt(0) as android.view.ViewGroup
        val widths = (0 until menuView.childCount).map { menuView.getChildAt(it).measuredWidth }
        assertEquals(3, widths.size)
        assertTrue(widths.all { it == widths.first() })
    }

    private fun dp(activity: MainActivity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density).roundToInt()
    }
}
