package com.example.busiscoming

import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.P2pRouteDetailQuery
import com.example.busiscoming.data.model.P2pRoutePlan
import com.example.busiscoming.data.model.RouteDetail
import com.example.busiscoming.data.model.RouteDetailLeg
import com.example.busiscoming.data.model.RouteDetailStop
import com.example.busiscoming.data.model.RouteDetailStopRole
import com.example.busiscoming.data.repository.RouteDetailRepository
import com.example.busiscoming.ui.main.MainActivity
import com.example.busiscoming.ui.main.RouteDetailBottomSheet
import org.hamcrest.CoreMatchers.containsString
import androidx.test.espresso.NoMatchingViewException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class RouteDetailBottomSheetInstrumentedTest {
    @Test
    fun expandedLongDetailScrollsDownAndReturnsToTopWithoutClosingSheet() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var sheet: RouteDetailBottomSheet
            scenario.onActivity { activity ->
                sheet = RouteDetailBottomSheet(
                    activity = activity,
                    repository = object : RouteDetailRepository {
                        override fun loadRouteDetail(route: BusRouteOption): RouteDetail {
                            return longDetail()
                        }
                    }
                )
                sheet.show(route())
            }

            waitUntil {
                try {
                    onView(withId(R.id.routeDetailScroll)).check(matches(isDisplayed()))
                    true
                } catch (_: NoMatchingViewException) {
                    false
                } catch (_: AssertionError) {
                    false
                }
            }
            onView(withText(containsString("途經 40 個站"))).perform(click())
            onView(withId(R.id.routeDetailScroll)).check(matches(isDisplayed()))
            saveScreenshot("route-detail-expanded")
            onView(withId(R.id.routeDetailScroll)).perform(swipeUp(), swipeUp())

            var scrolledY = 0
            onView(withId(R.id.routeDetailScroll)).check { view, _ ->
                val scroll = view as NestedScrollView
                scrolledY = scroll.scrollY
                assertTrue(scroll.canScrollVertically(-1))
            }
            assertTrue(scrolledY > 0)

            onView(withId(R.id.routeDetailScroll)).perform(swipeDown())
            onView(withId(R.id.routeDetailScroll)).check(matches(isDisplayed()))
            onView(withId(R.id.routeDetailScroll)).check { view, _ ->
                val scroll = view as NestedScrollView
                assertTrue(scroll.scrollY < scrolledY)
                scroll.scrollTo(0, 0)
                assertEquals(0, scroll.scrollY)
            }

            scenario.onActivity {
                sheet.dispose()
            }
        }
    }

    private fun waitUntil(timeoutMillis: Long = 3_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("Timed out waiting for route detail", condition())
    }

    private fun route(): BusRouteOption {
        return BusRouteOption(
            routeName = "測試路線",
            routeSegments = listOf("8X"),
            priceHkd = 8.1,
            durationMinutes = 45,
            arrivalMinutes = 5,
            transferCount = 0,
            walkingDistanceMeters = 120,
            routeDetailQuery = P2pRouteDetailQuery(
                rawInfo = "test",
                generalInfo = "",
                listId = "1",
                lang = "0",
                plan = P2pRoutePlan(legs = emptyList())
            )
        )
    }

    private fun longDetail(): RouteDetail {
        return RouteDetail(
            routeName = "測試路線",
            priceHkd = 8.1,
            durationMinutes = 45,
            walkingDistanceMeters = 120,
            legs = listOf(
                RouteDetailLeg(
                    route = "8X",
                    routeVariant = "8X",
                    directionText = "往終點方向",
                    boardingStop = stop("起點", 1, RouteDetailStopRole.BOARDING),
                    viaStops = (2..41).map { sequence ->
                        stop("途經站 $sequence", sequence, RouteDetailStopRole.VIA)
                    },
                    alightingStop = stop("終點", 42, RouteDetailStopRole.ALIGHTING)
                )
            )
        )
    }

    private fun stop(
        name: String,
        sequence: Int,
        role: RouteDetailStopRole
    ): RouteDetailStop {
        return RouteDetailStop(
            rawName = name,
            displayName = name,
            stopId = sequence.toString(),
            sequence = sequence,
            latitude = 22.3,
            longitude = 114.2,
            routeVariant = "8X",
            role = role
        )
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val command = instrumentation.uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/BusIsComing-$name.png"
        )
        FileInputStream(command.fileDescriptor).use { input ->
            input.readBytes()
        }
    }
}
