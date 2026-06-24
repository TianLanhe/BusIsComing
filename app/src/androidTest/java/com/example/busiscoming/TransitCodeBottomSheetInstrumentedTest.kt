package com.example.busiscoming

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.busiscoming.data.local.RouteConfigDbHelper
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.ui.main.MainActivity
import com.example.busiscoming.ui.main.TransitCodeBottomSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransitCodeBottomSheetInstrumentedTest {
    @Before
    fun clearRoutes() {
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
    }

    @Test
    fun clickingMainTransitCodeShowsExperimentalBottomSheet() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.transitCodeButton).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val sheet = transitCodeSheet(activity)
                assertTrue(sheet.isShowing())
                assertTrue(sheet.textSnapshot().contains("實驗性乘車碼入口"))
                assertTrue(sheet.textSnapshot().contains("逐條嘗試微信或支付寶候選入口；不會自動嘗試下一條。"))
            }
        }
    }

    @Test
    fun bottomSheetListsThreeWechatAndFourAlipayTargets() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.transitCodeButton).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val labels = transitCodeSheet(activity).textSnapshot()
                listOf(
                    "微信",
                    "微信 jumpWxa",
                    "微信明文 Scheme",
                    "微信首頁 path",
                    "支付寶",
                    "支付寶 appId",
                    "支付寶 saId",
                    "支付寶 H5 render",
                    "支付寶 ds 包裝"
                ).forEach { label ->
                    assertTrue("Missing label: $label", labels.contains(label))
                }
            }
        }
    }

    @Test
    fun openingAndClosingBottomSheetKeepsDisplayedRouteResults() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var before: ResultSnapshot
            scenario.onActivity { activity ->
                prepareResults(activity, routes("保留", 4))
                before = snapshot(activity)
            }

            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.transitCodeButton).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val sheet = transitCodeSheet(activity)
                assertTrue(sheet.isShowing())
                sheet.dispose()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                assertEquals(before, snapshot(activity))
            }
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

    private fun snapshot(activity: MainActivity): ResultSnapshot {
        return ResultSnapshot(
            emptyStateVisibility = activity.findViewById<View>(R.id.emptyRouteState).visibility,
            queryControlsVisibility = activity.findViewById<View>(R.id.queryControls).visibility,
            resultSectionVisibility = activity.findViewById<View>(R.id.resultSection).visibility,
            resultListVisibility = activity.findViewById<View>(R.id.resultListContainer).visibility,
            sortControlsVisibility = activity.findViewById<View>(R.id.sortControls).visibility,
            routeCount = activity.findViewById<RecyclerView>(R.id.busRouteList).adapter?.itemCount ?: -1,
            summary = activity.findViewById<TextView>(R.id.resultSummaryText).text.toString(),
            updatedAt = activity.findViewById<TextView>(R.id.resultUpdatedAtText).text.toString()
        )
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

    private fun transitCodeSheet(activity: MainActivity): TransitCodeBottomSheet {
        return activity.javaClass.getDeclaredField("transitCodeBottomSheet").apply {
            isAccessible = true
        }.get(activity) as TransitCodeBottomSheet
    }

    private data class ResultSnapshot(
        val emptyStateVisibility: Int,
        val queryControlsVisibility: Int,
        val resultSectionVisibility: Int,
        val resultListVisibility: Int,
        val sortControlsVisibility: Int,
        val routeCount: Int,
        val summary: String,
        val updatedAt: String
    )
}
