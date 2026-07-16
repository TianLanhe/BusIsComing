package com.golink.busiscoming

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
