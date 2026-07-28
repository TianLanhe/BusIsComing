package com.golink.busiscoming

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.RoutePinGestureHitTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutePinGestureHitInstrumentedTest {
    @Test
    fun etaAndFortyEightDpMonitorTargetsAreExcludedButRouteBodyIsNot() {
        var card: View? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.mainRoot)
                card = LayoutInflater.from(activity).inflate(R.layout.item_bus_route, root, false)
                root.addView(card)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                val routeCard = requireNotNull(card)
                val eta = routeCard.findViewById<View>(R.id.busEtaTextColumn)
                val monitor = routeCard.findViewById<View>(R.id.busMonitorButton)
                val routeName = routeCard.findViewById<View>(R.id.busRouteNameText)

                assertTrue(RoutePinGestureHitTest.isExcluded(routeCard, centerX(eta), centerY(eta)))
                assertTrue(
                    RoutePinGestureHitTest.isExcluded(
                        routeCard,
                        centerX(monitor),
                        centerY(monitor)
                    )
                )
                assertFalse(
                    RoutePinGestureHitTest.isExcluded(
                        routeCard,
                        centerX(routeName),
                        centerY(routeName)
                    )
                )
                assertEqualsDp(48, monitor.width)
                assertEqualsDp(48, monitor.height)
            }
        }
    }

    private fun centerX(view: View): Float {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[0] + view.width / 2f
    }

    private fun centerY(view: View): Float {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[1] + view.height / 2f
    }

    private fun assertEqualsDp(expectedDp: Int, actualPx: Int) {
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        assertTrue(actualPx == (expectedDp * density).toInt())
    }
}
