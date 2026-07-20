package com.golink.busiscoming

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.ui.main.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrequentRoutesScrollInstrumentedTest {
    @Test
    fun longResultsScrollQueryControlsAwayWhileSortAndSummaryRemainVisible() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var initialQueryTop = 0
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.emptyRouteState).visibility = View.GONE
                activity.findViewById<View>(R.id.queryControls).visibility = View.VISIBLE
                activity.findViewById<View>(R.id.resultSection).visibility = View.VISIBLE
                invokeShowInitialRoutes(activity, routes(30))
                initialQueryTop = screenTop(activity.findViewById(R.id.collapsingQueryControls))
            }

            onView(withId(R.id.frequentRoutesRoot)).perform(swipeUp(), swipeUp())

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
}
