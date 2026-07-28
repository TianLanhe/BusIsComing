package com.golink.busiscoming

import android.Manifest
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.repository.RouteConfigRepository
import com.golink.busiscoming.ui.main.MainActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.FileInputStream
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
            onView(withId(R.id.placePairOriginInput)).perform(replaceText("未提交起點"))
            onView(withId(R.id.navigation_settings)).perform(click())
            onView(withId(R.id.navigation_search)).perform(click())
            onView(withId(R.id.placePairOriginInput)).check(matches(withText("未提交起點")))

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
    fun imeCoversBottomNavigationWithoutMovingItAndRestoresItsSelectedDestination() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            var navigationBoundsBeforeIme: android.graphics.Rect? = null
            scenario.onActivity { activity ->
                navigationBoundsBeforeIme = screenBounds(
                    activity.findViewById<BottomNavigationView>(R.id.topLevelNav)
                )
            }

            onView(withId(R.id.placePairOriginInput)).perform(click())
            waitForImeVisibility(scenario, visible = true)
            scenario.onActivity { activity ->
                val navigation = activity.findViewById<BottomNavigationView>(R.id.topLevelNav)
                assertEquals(navigationBoundsBeforeIme, screenBounds(navigation))
                assertEquals(R.id.navigation_search, navigation.selectedItemId)
                assertFalse(navigation.isEnabled)
                assertFalse(navigation.isClickable)
                assertEquals(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                    navigation.importantForAccessibility
                )
            }

            onView(withId(R.id.placePairOriginInput)).perform(closeSoftKeyboard())
            waitForImeVisibility(scenario, visible = false)
            scenario.onActivity { activity ->
                val navigation = activity.findViewById<BottomNavigationView>(R.id.topLevelNav)
                assertEquals(navigationBoundsBeforeIme, screenBounds(navigation))
                assertEquals(R.id.navigation_search, navigation.selectedItemId)
                assertTrue(navigation.isEnabled)
                assertTrue(navigation.isClickable)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, navigation.importantForAccessibility)
            }
        }
    }

    @Test
    fun activeIndicatorAndLabelRemainSeparatedAcrossSupportedFontScales() {
        try {
            listOf("1.0", "1.3", "2.0").forEach { fontScale ->
                runShell("settings put system font_scale $fontScale")
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        assertNavigationState(activity, R.id.navigation_frequent_routes)
                    }
                }
            }
        } finally {
            runShell("settings put system font_scale 1.0")
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
        assertEquals(dp(activity, 24), navigation.itemIconSize)
        val checkedItems = (0 until navigation.menu.size())
            .map { navigation.menu.getItem(it) }
            .filter { it.isChecked }
        assertEquals(1, checkedItems.size)
        assertEquals(selectedItemId, checkedItems.single().itemId)

        val menuView = navigation.getChildAt(0) as android.view.ViewGroup
        val widths = (0 until menuView.childCount).map { menuView.getChildAt(it).measuredWidth }
        assertEquals(3, widths.size)
        assertTrue(widths.all { it == widths.first() })

        val selectedIndex = checkedItems.single().itemId.let { checkedId ->
            (0 until navigation.menu.size()).first { navigation.menu.getItem(it).itemId == checkedId }
        }
        val selectedItem = menuView.getChildAt(selectedIndex)
        val indicator = selectedItem.findViewById<android.view.View>(
            com.google.android.material.R.id.navigation_bar_item_active_indicator_view
        )
        val labelGroup = selectedItem.findViewById<android.view.View>(
            com.google.android.material.R.id.navigation_bar_item_labels_group
        )
        val indicatorBounds = screenBounds(indicator)
        val labelBounds = screenBounds(labelGroup)
        assertTrue(
            "Selected label must remain visibly below the active indicator",
            labelBounds.top - indicatorBounds.bottom >= dp(activity, 4)
        )
        (0 until menuView.childCount).forEach { index ->
            val item = menuView.getChildAt(index)
            val visibleLabelId = if (item.isSelected) {
                com.google.android.material.R.id.navigation_bar_item_large_label_view
            } else {
                com.google.android.material.R.id.navigation_bar_item_small_label_view
            }
            val visibleLabel = item.findViewById<android.widget.TextView>(visibleLabelId)
            val itemBounds = screenBounds(item)
            val visibleLabelBounds = screenBounds(visibleLabel)
            assertTrue(
                "Navigation label glyphs must retain bottom safety space inside their item: " +
                    "item=$itemBounds label=$visibleLabelBounds height=${visibleLabel.height} " +
                    "baseline=${visibleLabel.baseline} metrics=${visibleLabel.paint.fontMetricsInt}",
                visibleLabelBounds.bottom <= itemBounds.bottom - dp(activity, 2)
            )
        }
        assertTrue(
            "Selected label must remain inside the navigation bar",
            labelBounds.bottom <= screenBounds(navigation).bottom
        )
    }

    private fun screenBounds(view: android.view.View): android.graphics.Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return android.graphics.Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height
        )
    }

    private fun waitForImeVisibility(
        scenario: ActivityScenario<MainActivity>,
        visible: Boolean
    ) {
        val deadline = System.currentTimeMillis() + 10_000L
        while (System.currentTimeMillis() < deadline) {
            var currentVisibility: Boolean? = null
            scenario.onActivity { activity ->
                val navigation = activity.findViewById<BottomNavigationView>(R.id.topLevelNav)
                currentVisibility = ViewCompat.getRootWindowInsets(navigation)
                    ?.isVisible(WindowInsetsCompat.Type.ime())
            }
            if (currentVisibility == visible) return
            Thread.sleep(50)
        }
        assertTrue("Timed out waiting for IME visibility=$visible", false)
    }

    private fun dp(activity: MainActivity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density).roundToInt()
    }

    private fun runShell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).use { input ->
            input.readBytes().decodeToString()
        }
    }
}
