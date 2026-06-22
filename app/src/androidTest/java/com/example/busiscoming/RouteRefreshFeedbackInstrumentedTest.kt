package com.example.busiscoming

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class RouteRefreshFeedbackInstrumentedTest {
    @Test
    fun nonEmptyRefreshReplacesResultsShowsSuccessAndUnlocksAfterDelay() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                prepareResults(activity, routes("舊", 20))
                invoke(activity, "showRefreshLoadingState", arrayOf(Int::class.javaPrimitiveType!!), 31)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.resultRefreshOverlay).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.resultRefreshProgress).visibility)
                assertFalse(activity.findViewById<View>(R.id.queryButton).isEnabled)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            saveScreenshot("refresh-progress")

            scenario.onActivity { activity ->
                invoke(
                    activity,
                    "handleRefreshSuccess",
                    arrayOf(Int::class.javaPrimitiveType!!, List::class.java),
                    31,
                    routes("新", 5)
                )
                assertEquals(5, activity.findViewById<RecyclerView>(R.id.busRouteList).adapter?.itemCount)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.resultRefreshSuccess).visibility)
                assertTrue(activity.findViewById<TextView>(R.id.resultUpdatedAtText).text.isNotBlank())
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            saveScreenshot("refresh-success")
            Thread.sleep(650)
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.resultRefreshOverlay).visibility)
                assertTrue(activity.findViewById<View>(R.id.queryButton).isEnabled)
                assertEquals(5, activity.findViewById<RecyclerView>(R.id.busRouteList).adapter?.itemCount)
            }
        }
    }

    @Test
    fun emptyRefreshKeepsOldResultsUntilSuccessDelayFinishes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                prepareResults(activity, routes("舊", 3))
                invoke(activity, "showRefreshLoadingState", arrayOf(Int::class.javaPrimitiveType!!), 41)
                invoke(
                    activity,
                    "handleRefreshSuccess",
                    arrayOf(Int::class.javaPrimitiveType!!, List::class.java),
                    41,
                    emptyList<BusRouteOption>()
                )
                assertEquals(3, activity.findViewById<RecyclerView>(R.id.busRouteList).adapter?.itemCount)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.resultRefreshSuccess).visibility)
            }

            Thread.sleep(650)
            scenario.onActivity { activity ->
                assertEquals(0, activity.findViewById<RecyclerView>(R.id.busRouteList).adapter?.itemCount)
                assertEquals(
                    "暫無可用巴士路線",
                    activity.findViewById<TextView>(R.id.resultStatusTitle).text.toString()
                )
            }
        }
    }

    @Test
    fun failedRefreshKeepsResultsAndTimestampWhileRestoringIdleState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                prepareResults(activity, routes("保留", 8))
                val timestamp = activity.findViewById<TextView>(R.id.resultUpdatedAtText).text.toString()

                invoke(activity, "showRefreshLoadingState", arrayOf(Int::class.javaPrimitiveType!!), 51)
                invoke(
                    activity,
                    "handleRefreshFailure",
                    arrayOf(Int::class.javaPrimitiveType!!),
                    51
                )

                assertEquals(8, activity.findViewById<RecyclerView>(R.id.busRouteList).adapter?.itemCount)
                assertEquals(timestamp, activity.findViewById<TextView>(R.id.resultUpdatedAtText).text.toString())
                assertEquals(View.GONE, activity.findViewById<View>(R.id.resultRefreshOverlay).visibility)
                assertTrue(activity.findViewById<View>(R.id.queryButton).isEnabled)
            }
            Thread.sleep(100)
            saveScreenshot("refresh-failure-preserved")
        }
    }

    private fun prepareResults(activity: MainActivity, routes: List<BusRouteOption>) {
        activity.findViewById<View>(R.id.emptyRouteState).visibility = View.GONE
        activity.findViewById<View>(R.id.queryControls).visibility = View.VISIBLE
        activity.findViewById<View>(R.id.resultSection).visibility = View.VISIBLE
        invoke(activity, "showInitialRoutes", arrayOf(List::class.java), routes)
    }

    private fun routes(prefix: String, count: Int): List<BusRouteOption> {
        return (1..count).map { index ->
            BusRouteOption(
                routeName = "$prefix$index",
                routeSegments = listOf("$index"),
                priceHkd = index.toDouble(),
                durationMinutes = index + 10,
                arrivalMinutes = index,
                transferCount = 0,
                walkingDistanceMeters = index * 10
            )
        }
    }

    private fun invoke(
        target: Any,
        name: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any
    ) {
        target.javaClass.getDeclaredMethod(name, *parameterTypes).apply {
            isAccessible = true
        }.invoke(target, *args)
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
