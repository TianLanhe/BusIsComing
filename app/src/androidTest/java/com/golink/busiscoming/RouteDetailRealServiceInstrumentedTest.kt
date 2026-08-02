package com.golink.busiscoming

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.ui.main.RouteDetailActivity
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import com.golink.busiscoming.ui.main.RouteDetailRuntime
import com.golink.busiscoming.ui.main.RouteMapLineKind
import com.golink.busiscoming.ui.main.RouteMapPresentation
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class RouteDetailRealServiceInstrumentedTest {
    @After
    fun resetRuntime() = RouteDetailRuntime.reset()

    @Test
    fun realMultiLegCitybusGeometryAndGoogleMapRenderTogether() {
        assumeTrue(InstrumentationRegistry.getArguments().getString(ARG_RUN_REAL) == "true")
        val rawInfo = "2|*|CTB||82X-ISR-1||6||9||O|*|CTB||102-MEF-1||12||15||O|*|"
        val legs = listOf(
            P2pRouteLeg("CTB", "82X-ISR-1", "82X", 6, 9, "O", "outbound"),
            P2pRouteLeg("CTB", "102-MEF-1", "102", 12, 15, "O", "outbound")
        )
        val route = BusRouteOption(
            routeName = "82X → 102",
            routeSegments = listOf("82X", "102"),
            priceHkd = 20.0,
            durationMinutes = 35,
            arrivalMinutes = 35,
            transferCount = 1,
            walkingDistanceMeters = 250,
            waitTimeState = WaitTimeState.Loading,
            routeDetailQuery = P2pRouteDetailQuery(
                rawInfo,
                "02:04|*|35",
                "0",
                "0",
                P2pRoutePlan(rawInfo, "0", legs)
            )
        )
        val launchArgs = RouteDetailLaunchArgs.fromRoute(
            route,
            Place("柴灣", 22.2649, 114.2416),
            Place("北角", 22.2900, 114.1963)
        )
        val intent = Intent(ApplicationProvider.getApplicationContext(), RouteDetailActivity::class.java)
            .putExtras(launchArgs.toBundle())
        val latestPresentation = AtomicReference<RouteMapPresentation>()
        RouteDetailRuntime.presentationObserver = latestPresentation::set

        ActivityScenario.launch<RouteDetailActivity>(intent).use { scenario ->
            waitForRealContent(scenario, latestPresentation)
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.routeDetailList)
                assertTrue(requireNotNull(list.adapter).itemCount > 4)
                assertTrue(activity.findViewById<View>(R.id.routeDetailMapLegend).visibility == View.VISIBLE)
                assertTrue(findViewWithTag(activity.window.decorView, "GoogleWatermark")?.visibility == View.VISIBLE)
                assertTrue(latestPresentation.get().lines.count { it.kind == RouteMapLineKind.BUS } == 2)

                val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                val output = File(requireNotNull(activity.getExternalFilesDir(null)), SCREENSHOT_NAME)
                FileOutputStream(output).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            if (InstrumentationRegistry.getArguments().getString(ARG_HOLD_FOR_INSPECTION) == "true") {
                Thread.sleep(30_000L)
            }
        }
    }

    private fun waitForRealContent(
        scenario: ActivityScenario<RouteDetailActivity>,
        latestPresentation: AtomicReference<RouteMapPresentation>
    ) {
        val deadline = SystemClock.uptimeMillis() + 20_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.findViewById<RecyclerView>(R.id.routeDetailList).adapter?.itemCount?.let { it > 4 } == true &&
                    findViewWithTag(activity.window.decorView, "GoogleWatermark")?.visibility == View.VISIBLE &&
                    latestPresentation.get()?.lines?.count { it.kind == RouteMapLineKind.BUS } == 2
            }
            if (ready) return
            Thread.sleep(250)
        }
    }

    private fun findViewWithTag(root: View, tag: String): View? {
        if (root.tag?.toString() == tag) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) findViewWithTag(root.getChildAt(index), tag)?.let { return it }
        return null
    }

    private companion object {
        const val ARG_RUN_REAL = "runRealRouteMap"
        const val ARG_HOLD_FOR_INSPECTION = "holdRealRouteMap"
        const val SCREENSHOT_NAME = "route-map-real.png"
    }
}
