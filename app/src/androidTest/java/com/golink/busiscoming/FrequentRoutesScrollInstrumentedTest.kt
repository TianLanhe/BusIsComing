package com.golink.busiscoming

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.ui.main.MainActivity
import com.google.android.material.appbar.AppBarLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matcher

@RunWith(AndroidJUnit4::class)
class FrequentRoutesScrollInstrumentedTest {
    @Test
    fun onlyLongResultsScrollQueryControlsAwayWhileTopAndEmptyContentStayFixed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var initialQueryTop = 0
            scenario.onActivity { activity ->
                val appBar = activity.findViewById<AppBarLayout>(R.id.frequentAppBar)
                appBar.setExpanded(true, false)
            }
            waitUntil {
                var expanded = false
                scenario.onActivity { activity ->
                    expanded = activity.findViewById<View>(R.id.frequentAppBar).top == 0
                }
                expanded
            }
            scenario.onActivity { activity ->
                initialQueryTop = screenTop(activity.findViewById(R.id.collapsingQueryControls))
            }
            onView(withId(R.id.emptyRouteState)).perform(swipeUp())
            scenario.onActivity { activity ->
                assertEquals(
                    initialQueryTop,
                    screenTop(activity.findViewById(R.id.collapsingQueryControls))
                )
            }

            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.emptyRouteState).visibility = View.GONE
                activity.findViewById<View>(R.id.queryControls).visibility = View.VISIBLE
                activity.findViewById<View>(R.id.resultSection).visibility = View.VISIBLE
                invokeShowInitialRoutes(activity, routes(30))
                activity.findViewById<AppBarLayout>(R.id.frequentAppBar)
                    .setExpanded(true, false)
            }
            waitUntil {
                var expanded = false
                scenario.onActivity { activity ->
                    expanded = activity.findViewById<View>(R.id.frequentAppBar).top == 0
                }
                expanded
            }
            scenario.onActivity { activity ->
                initialQueryTop = screenTop(activity.findViewById(R.id.collapsingQueryControls))
            }

            onView(withId(R.id.collapsingQueryControls)).perform(swipeUp())
            scenario.onActivity { activity ->
                assertEquals(
                    initialQueryTop,
                    screenTop(activity.findViewById(R.id.collapsingQueryControls))
                )
            }

            onView(withId(R.id.busRouteList)).perform(
                swipeUpVisibleArea(),
                swipeUpVisibleArea()
            )

            scenario.onActivity { activity ->
                val queryTop = screenTop(activity.findViewById(R.id.collapsingQueryControls))
                val sticky = activity.findViewById<View>(R.id.stickyResultControls)
                val root = activity.findViewById<View>(R.id.frequentRoutesRoot)
                val list = activity.findViewById<RecyclerView>(R.id.busRouteList)

                assertTrue(queryTop < initialQueryTop)
                assertTrue(sticky.visibility == View.VISIBLE)
                assertTrue(screenTop(sticky) >= screenTop(root))
                assertTrue(list.computeVerticalScrollOffset() > 0)
            }
        }
    }

    private fun invokeShowInitialRoutes(activity: MainActivity, routes: List<BusRouteOption>) {
        activity.javaClass.getDeclaredMethod("showInitialRoutes", List::class.java).apply {
            isAccessible = true
        }.invoke(activity, routes)
    }

    private fun routes(count: Int): List<BusRouteOption> = (1..count).map { index ->
        BusRouteOption(
            routeName = "R$index",
            routeSegments = listOf("R$index"),
            priceHkd = 8.0 + index,
            durationMinutes = 20 + index,
            arrivalMinutes = index,
            transferCount = 0,
            walkingDistanceMeters = 100 + index,
            stopPreview = RouteCardStopPreview(
                boardingStopName = "Long boarding station $index",
                alightingStopName = "Long destination station $index"
            ),
            resultId = "scroll-$index"
        )
    }

    private fun screenTop(view: View): Int {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[1]
    }

    private fun swipeUpVisibleArea(): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isDisplayed()

        override fun getDescription(): String = "swipe up within the visible result area"

        override fun perform(uiController: UiController, view: View) {
            GeneralSwipeAction(
                Swipe.FAST,
                visibleCoordinates(0.8f),
                visibleCoordinates(0.2f),
                Press.FINGER
            ).perform(uiController, view)
        }
    }

    private fun visibleCoordinates(verticalFraction: Float): CoordinatesProvider =
        CoordinatesProvider { view ->
            val visibleBounds = Rect()
            check(view.getGlobalVisibleRect(visibleBounds))
            floatArrayOf(
                visibleBounds.exactCenterX(),
                visibleBounds.top + visibleBounds.height() * verticalFraction
            )
        }

    private fun waitUntil(timeoutMillis: Long = 10_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        assertTrue("Timed out waiting for condition", condition())
    }
}
