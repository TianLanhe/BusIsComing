package com.golink.busiscoming

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.MonitorSettingsBottomSheet
import com.golink.busiscoming.ui.main.MonitorSettingsResult
import com.golink.busiscoming.ui.main.MonitorWalkingInputs
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonitorSettingsBottomSheetInstrumentedTest {
    @Test
    fun startActionStaysVisibleAndInvokedOnCompactScreen() {
        val result = AtomicReference<MonitorSettingsResult?>()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var sheet: MonitorSettingsBottomSheet
            scenario.onActivity { activity ->
                sheet = MonitorSettingsBottomSheet(
                    context = activity,
                    onStart = { selection -> result.set(selection) }
                )
                sheet.show(
                    inputs = MonitorWalkingInputs(
                        interfaceDistanceMeters = 280,
                        straightLineDistanceMeters = 230
                    )
                )
            }

            onView(withText(R.string.monitor_start))
                .inRoot(isDialog())
                .check(matches(isCompletelyDisplayed()))
                .perform(click())

            assertNotNull(result.get())
            assertEquals(4, result.get()?.walkingMinutes)
            assertEquals(true, result.get()?.voiceEnabled)

            scenario.onActivity {
                sheet.dispose()
            }
        }
    }
}
