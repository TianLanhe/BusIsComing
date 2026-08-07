package com.golink.busiscoming

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.update.GooglePlayRatingRuntime
import com.golink.busiscoming.data.update.PlayStoreAvailability
import com.golink.busiscoming.data.update.PlayStoreAvailabilityDetector
import com.golink.busiscoming.ui.main.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GooglePlayRatingDeviceStateInstrumentedTest {
    @After
    fun resetRuntime() = GooglePlayRatingRuntime.reset()

    @Test
    fun productionDetectorAndRecoveryDialogMatchCurrentPlayPackageState() {
        val expectedName = InstrumentationRegistry.getArguments().getString(ARG_EXPECTED_STATE)
        assumeTrue(!expectedName.isNullOrBlank())
        val expected = PlayStoreAvailability.valueOf(requireNotNull(expectedName))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        GooglePlayRatingRuntime.reset()

        assertEquals(expected, PlayStoreAvailabilityDetector(context).detect())
        if (expected == PlayStoreAvailability.AVAILABLE) return

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navigation_settings)).perform(click())
            onView(withId(R.id.settingsRatingRow)).perform(scrollTo(), click())
            onView(withText(expected.titleRes())).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.update_action_cancel)).inRoot(isDialog()).perform(click())
        }
    }

    private fun PlayStoreAvailability.titleRes(): Int = when (this) {
        PlayStoreAvailability.AVAILABLE -> error("Available has no recovery dialog")
        PlayStoreAvailability.DISABLED -> R.string.rating_play_disabled_title
        PlayStoreAvailability.MISSING -> R.string.rating_play_missing_title
        PlayStoreAvailability.UNUSABLE -> R.string.rating_play_unusable_title
    }

    private companion object {
        const val ARG_EXPECTED_STATE = "expectedPlayStoreAvailability"
    }
}
