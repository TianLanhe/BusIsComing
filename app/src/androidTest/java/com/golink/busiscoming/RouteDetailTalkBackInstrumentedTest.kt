package com.golink.busiscoming

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteGeometrySegment
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.RouteDetailRepository
import com.golink.busiscoming.data.repository.RouteGeometryDataSource
import com.golink.busiscoming.data.repository.RouteGeometryLoadHandle
import com.golink.busiscoming.data.repository.RouteGeometryRequest
import com.golink.busiscoming.ui.main.RouteDetailActivity
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import com.golink.busiscoming.ui.main.RouteDetailRuntime
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteDetailTalkBackInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Before
    fun setUpRuntime() {
        RouteDetailRuntime.repositoryFactory = {
            object : RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail =
                    DemoScreenshotFixtures.routeDetail()
            }
        }
        RouteDetailRuntime.etaResolver = { WaitTimeState.Available(6) }
        RouteDetailRuntime.geometryRepositoryFactory = {
            object : RouteGeometryDataSource {
                override fun loadGeometries(
                    requests: List<RouteGeometryRequest>,
                    onResult: (RouteGeometryRequest, Result<RouteGeometrySegment>) -> Unit
                ): RouteGeometryLoadHandle = RouteGeometryLoadHandle {}
            }
        }
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    @After
    fun resetRuntime() = RouteDetailRuntime.reset()

    @Test
    fun talkBackCanFocusAndOperateMapControlsSheetAndTextTimeline() {
        assumeTrue(InstrumentationRegistry.getArguments().getString(ARG_RUN_TALKBACK) == "true")
        ActivityScenario.launch<RouteDetailActivity>(intent()).use { scenario ->
            waitForNode(R.id.routeDetailSheetHandle)
            val back = waitForNode(R.id.routeDetailFloatingBack)
            val handle = waitForNode(R.id.routeDetailSheetHandle)
            val location = waitForNode(R.id.routeDetailLocation)
            val overview = waitForNode(R.id.routeDetailOverview)
            val map = waitForNode(R.id.routeDetailMap)

            assertClickableAndFocus(back, context.getString(R.string.route_detail_navigate_up))
            assertClickableAndFocus(handle, context.getString(R.string.route_detail_sheet_handle_summary))
            assertClickableAndFocus(location, context.getString(R.string.route_map_current_location))
            assertClickableAndFocus(overview, context.getString(R.string.route_map_overview))
            assertEquals(context.getString(R.string.route_map_content_description), map.contentDescription?.toString())
            assertTrue(
                "Summary semantics must contain the route without relying on the map",
                allAppNodes().any { it.contentDescription?.toString()?.contains("28X") == true }
            )

            assertTrue(handle.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            onView(withId(R.id.routeDetailToolbar)).check(matches(isDisplayed()))

            val expandedHandle = waitForNode(R.id.routeDetailSheetHandle)
            assertEquals(
                context.getString(R.string.route_detail_sheet_handle_full),
                expandedHandle.contentDescription?.toString()
            )
            assertTrue(
                "Expanded timeline must expose a boarding stop to TalkBack",
                allAppNodes().any { nodeText(it).contains("上車") }
            )
            assertTrue(
                "Expanded timeline must expose an alighting stop to TalkBack",
                allAppNodes().any { nodeText(it).contains("下車") }
            )
            saveScreenshot(scenario, "route-detail-talkback-full.png")
        }
    }

    private fun intent(): Intent = Intent(context, RouteDetailActivity::class.java).putExtras(
        RouteDetailLaunchArgs.fromRoute(DemoScreenshotFixtures.primaryRoute()).toBundle()
    )

    private fun assertClickableAndFocus(node: AccessibilityNodeInfo, expectedDescription: String) {
        assertEquals(expectedDescription, node.contentDescription?.toString())
        assertTrue(
            "Node '$expectedDescription' must expose ACTION_CLICK",
            node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
        )
        assertTrue("Node '$expectedDescription' must be visible to TalkBack", node.isVisibleToUser)
        assertTrue("Node '$expectedDescription' must be clickable", node.isClickable)
    }

    private fun waitForNode(id: Int): AccessibilityNodeInfo =
        waitForNode("${context.packageName}:id/${context.resources.getResourceEntryName(id)}")

    private fun waitForNode(viewId: String): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        waitUntil("accessibility node $viewId") {
            result = findNode(viewId)
            result != null
        }
        return requireNotNull(result)
    }

    private fun findNode(viewId: String?): AccessibilityNodeInfo? {
        if (viewId == null) return null
        return appRoots().firstNotNullOfOrNull { root ->
            root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull()
        }
    }

    private fun allAppNodes(): List<AccessibilityNodeInfo> = buildList {
        fun visit(node: AccessibilityNodeInfo) {
            add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        appRoots().forEach(::visit)
    }

    private fun appRoots(): List<AccessibilityNodeInfo> =
        instrumentation.uiAutomation.windows.mapNotNull { it.root }.filter {
            it.packageName?.toString() == context.packageName
        }

    private fun nodeText(node: AccessibilityNodeInfo): String =
        listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" ")

    private fun saveScreenshot(scenario: ActivityScenario<RouteDetailActivity>, name: String) {
        scenario.onActivity { activity ->
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            val output = File(requireNotNull(activity.getExternalFilesDir(null)), name)
            FileOutputStream(output).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun waitUntil(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(100L)
        }
        assertTrue("Timed out waiting for $description", condition())
    }

    private companion object {
        const val ARG_RUN_TALKBACK = "runRouteDetailTalkBack"
    }
}
