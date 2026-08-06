package com.golink.busiscoming

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.golink.busiscoming.data.model.BusRouteOption
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
    fun startActionCanBeScrolledFullyIntoViewAndInvokedOnCompactScreen() {
        val result = AtomicReference<MonitorSettingsResult?>()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var sheet: MonitorSettingsBottomSheet
            scenario.onActivity { activity ->
                sheet = MonitorSettingsBottomSheet(activity) { selection ->
                    result.set(selection)
                }
                sheet.show(
                    route = route(),
                    inputs = MonitorWalkingInputs(
                        interfaceDistanceMeters = 280,
                        straightLineDistanceMeters = 230
                    )
                )
            }

            onView(withText(R.string.monitor_start))
                .inRoot(isDialog())
                .perform(scrollTo())
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

    private fun route(): BusRouteOption {
        return BusRouteOption(
            routeName = "測試行程",
            routeSegments = listOf("T1"),
            priceHkd = 8.0,
            durationMinutes = 24,
            arrivalMinutes = 7,
            transferCount = 0,
            walkingDistanceMeters = 280
        )
    }
}
