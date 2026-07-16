package com.golink.busiscoming

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.repository.RouteConfigRepository
import com.golink.busiscoming.ui.main.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopLevelNavigationInstrumentedTest {
    @Test
    fun topLevelDestinationsSwitchWithoutLeavingTheMainHost() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.frequentRoutesRoot)).check(matches(isDisplayed()))
            onView(withId(R.id.navigation_settings)).perform(click())
            onView(withId(R.id.settingsRoot)).check(matches(isDisplayed()))
            onView(withId(R.id.settingsBackButton)).check(doesNotExist())
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
    fun firstRunSearchActionAndSecondaryPagesReturnToTheirOwnerDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = RouteConfigRepository(context)
        repository.getAll().forEach { repository.delete(it.id) }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.emptySearchButton)).perform(click())
            onView(withId(R.id.searchRoot)).check(matches(isDisplayed()))

            onView(withId(R.id.navigation_settings)).perform(click())
            onView(withId(R.id.settingsAboutRow)).perform(click())
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
}
