package com.golink.busiscoming

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.P2pRouteRecoveryContext
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.local.RouteAutoRefreshInterval
import com.golink.busiscoming.data.local.RouteAutoRefreshSettingsStore
import com.golink.busiscoming.ui.main.ForegroundAutoRefreshController
import com.golink.busiscoming.ui.main.ForegroundAutoRefreshState
import com.golink.busiscoming.ui.main.RouteDetailActivity
import com.golink.busiscoming.ui.main.RouteDetailAdapter
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import com.golink.busiscoming.ui.main.RouteDetailRuntime
import com.golink.busiscoming.ui.main.RouteDetailUiItem
import com.golink.busiscoming.ui.main.GoogleRouteMapRenderer
import com.golink.busiscoming.ui.main.RouteMapCoordinate
import com.golink.busiscoming.ui.main.RouteMapLineKind
import com.golink.busiscoming.ui.main.RouteMapPresentation
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import com.golink.busiscoming.data.repository.PedestrianRouteRuntime
import com.golink.busiscoming.data.repository.PedestrianRuntimeDiagnosticEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class RouteDetailRealServiceInstrumentedTest {
    private lateinit var pedestrianRuntime: PedestrianRouteRuntime
    private val pedestrianEvents = CopyOnWriteArrayList<PedestrianRuntimeDiagnosticEvent>()

    @Before
    fun setUpPedestrianDiagnostics() {
        pedestrianEvents.clear()
        pedestrianRuntime = PedestrianRouteRuntime(diagnosticObserver = pedestrianEvents::add)
        RouteDetailRuntime.pedestrianRuntime = pedestrianRuntime
    }

    @After
    fun resetRuntime() {
        pedestrianRuntime.close()
        RouteDetailRuntime.reset()
    }

    @Test
    fun realSingleLegCitybusRendersEveryStopRoadGeometryWalksAndGoogleBaseMap() {
        assumeTrue(InstrumentationRegistry.getArguments().getString(ARG_RUN_REAL) == "true")
        val rawInfo = "1|*|CTB||780-CEF-1||6||17||O|*|"
        val leg = P2pRouteLeg("CTB", "780-CEF-1", "780", 6, 17, "O", "outbound")
        val route = BusRouteOption(
            routeName = "780",
            routeSegments = listOf("780"),
            priceHkd = 7.2,
            durationMinutes = 35,
            arrivalMinutes = 35,
            transferCount = 0,
            walkingDistanceMeters = 180,
            waitTimeState = WaitTimeState.Loading,
            routeDetailQuery = P2pRouteDetailQuery(
                rawInfo,
                "02:04|*|35",
                "0",
                "0",
                P2pRoutePlan(rawInfo, "0", listOf(leg))
            )
        )

        validateRealRoute(
            route = route,
            origin = Place("柴灣", 22.2624, 114.2344),
            destination = Place("中環碼頭", 22.2842, 114.1567),
            expectedBusLines = 1,
            minimumStops = 12,
            screenshotName = SINGLE_SCREENSHOT_NAME
        )
    }

    @Test
    fun realMultiLegCitybusRendersEveryStopRoadGeometryWalksAndGoogleBaseMap() {
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
        validateRealRoute(
            route = route,
            origin = Place("柴灣", 22.2649, 114.2416),
            destination = Place("北角", 22.2900, 114.1963),
            expectedBusLines = 2,
            minimumStops = 8,
            screenshotName = MULTI_SCREENSHOT_NAME
        )
    }

    @Test
    fun realN118GeometryAlignsWithGoogleRoadAtHighZoom() {
        assumeTrue(InstrumentationRegistry.getArguments().getString(ARG_RUN_REAL) == "true")
        val rawInfo = "1|*|CTB||N118-TOS-1||5||9||O|*|"
        val leg = P2pRouteLeg("CTB", "N118-TOS-1", "N118", 5, 9, "O", "outbound")
        val route = BusRouteOption(
            routeName = "N118",
            routeSegments = listOf("N118"),
            priceHkd = 17.8,
            durationMinutes = 12,
            arrivalMinutes = 12,
            transferCount = 0,
            walkingDistanceMeters = 273,
            waitTimeState = WaitTimeState.Loading,
            routeDetailQuery = P2pRouteDetailQuery(
                rawInfo,
                "03:12|*|12",
                "0",
                "0",
                P2pRoutePlan(rawInfo, "0", listOf(leg))
            )
        )

        validateRealRoute(
            route = route,
            origin = Place("樂軒臺", 22.264980642091, 114.24170198053),
            destination = Place("興華邨豐興樓", 22.262516262091, 114.23426978053),
            expectedBusLines = 1,
            minimumStops = 5,
            screenshotName = N118_HIGH_ZOOM_SCREENSHOT_NAME,
            expectedBusLineStart = RouteMapCoordinate(22.264897461791, 114.24161529313),
            expectedBusLineEnd = RouteMapCoordinate(22.262470011791, 114.23424341313),
            inspectionFocus = RouteMapCoordinate(22.26302, 114.23795),
            inspectionZoom = 18.5f,
            minimumWalkingLines = 0
        )
    }

    @Test
    fun realDetailCompletesTwoOneMinuteCitybusAndEtaCyclesAndResumesWhenOverdue() {
        assumeTrue(InstrumentationRegistry.getArguments().getString(ARG_RUN_REAL_AUTO) == "true")
        val rawInfo = "1|*|CTB||780-CEF-1||6||17||O|*|"
        val leg = P2pRouteLeg("CTB", "780-CEF-1", "780", 6, 17, "O", "outbound")
        val origin = Place("柴灣", 22.2624, 114.2344)
        val destination = Place("中環碼頭", 22.2842, 114.1567)
        val route = BusRouteOption(
            routeName = "780",
            routeSegments = listOf("780"),
            priceHkd = 7.2,
            durationMinutes = 35,
            arrivalMinutes = 35,
            transferCount = 0,
            walkingDistanceMeters = 180,
            waitTimeState = WaitTimeState.Loading,
            firstLegEtaQuery = FirstLegEtaQuery(
                company = leg.company,
                routeVariant = leg.routeVariant,
                route = leg.route,
                boardingSeq = leg.boardingSeq,
                alightingSeq = leg.alightingSeq,
                bound = leg.bound,
                directionPath = requireNotNull(leg.directionPath),
                rawInfo = rawInfo,
                lang = "0"
            ),
            routeDetailQuery = P2pRouteDetailQuery(
                rawInfo = rawInfo,
                generalInfo = "02:04|*|35",
                listId = "0",
                lang = "0",
                plan = P2pRoutePlan(rawInfo, "0", listOf(leg)),
                recoveryContext = P2pRouteRecoveryContext(
                    origin.latitude,
                    origin.longitude,
                    destination.latitude,
                    destination.longitude,
                    "T"
                )
            )
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = RouteAutoRefreshSettingsStore(context)
        val originalInterval = settings.getInterval()
        settings.setInterval(RouteAutoRefreshInterval.MINUTES_1)
        val intent = Intent(ApplicationProvider.getApplicationContext(), RouteDetailActivity::class.java)
            .putExtras(RouteDetailLaunchArgs.fromRoute(route, origin, destination).toBundle())

        try {
            ActivityScenario.launch<RouteDetailActivity>(intent).use { scenario ->
                waitForDetailAutoState(scenario, ForegroundAutoRefreshState.Waiting::class.java, 45_000L)
                val initialDetailGeneration = readIntField(scenario, "detailGeneration")
                val initialEtaGeneration = readIntField(scenario, "etaGeneration")

                waitForDetailCycle(
                    scenario = scenario,
                    minimumDetailGeneration = initialDetailGeneration + 1,
                    minimumEtaGeneration = initialEtaGeneration + 1,
                    timeoutMillis = 90_000L
                )
                assertStableRealDetail(scenario)

                scenario.moveToState(Lifecycle.State.CREATED)
                waitForDetailAutoState(scenario, ForegroundAutoRefreshState.Paused::class.java)
                val pausedDetailGeneration = readIntField(scenario, "detailGeneration")
                val pausedEtaGeneration = readIntField(scenario, "etaGeneration")
                Thread.sleep(65_000L)
                assertEquals(pausedDetailGeneration, readIntField(scenario, "detailGeneration"))
                assertEquals(pausedEtaGeneration, readIntField(scenario, "etaGeneration"))

                scenario.moveToState(Lifecycle.State.RESUMED)
                waitForDetailCycle(
                    scenario = scenario,
                    minimumDetailGeneration = pausedDetailGeneration + 1,
                    minimumEtaGeneration = pausedEtaGeneration + 1,
                    timeoutMillis = 45_000L
                )
                assertStableRealDetail(scenario)
                saveActivityScreenshot(scenario, REAL_AUTO_SCREENSHOT_NAME)
            }
        } finally {
            settings.setInterval(originalInterval)
        }
    }

    private fun validateRealRoute(
        route: BusRouteOption,
        origin: Place,
        destination: Place,
        expectedBusLines: Int,
        minimumStops: Int,
        screenshotName: String,
        expectedBusLineStart: RouteMapCoordinate? = null,
        expectedBusLineEnd: RouteMapCoordinate? = null,
        inspectionFocus: RouteMapCoordinate? = null,
        inspectionZoom: Float = 16f,
        minimumWalkingLines: Int = 2
    ) {
        val routeWithRecoveryContext = route.copy(
            routeDetailQuery = route.routeDetailQuery?.copy(
                recoveryContext = P2pRouteRecoveryContext(
                    originLatitude = origin.latitude,
                    originLongitude = origin.longitude,
                    destinationLatitude = destination.latitude,
                    destinationLongitude = destination.longitude,
                    searchMode = "T"
                )
            )
        )
        val launchArgs = RouteDetailLaunchArgs.fromRoute(routeWithRecoveryContext, origin, destination)
        val intent = Intent(ApplicationProvider.getApplicationContext(), RouteDetailActivity::class.java)
            .putExtras(launchArgs.toBundle())
        val latestPresentation = AtomicReference<RouteMapPresentation>()
        RouteDetailRuntime.presentationObserver = latestPresentation::set

        ActivityScenario.launch<RouteDetailActivity>(intent).use { scenario ->
            waitForRealContent(scenario, latestPresentation, expectedBusLines, minimumWalkingLines)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(500L)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.routeDetailList)
                assertTrue(requireNotNull(list.adapter).itemCount > 4)
                assertEquals(
                    0,
                    activity.resources.getIdentifier("routeDetailMapLegend", "id", activity.packageName)
                )
                assertTrue(findViewWithTag(activity.window.decorView, "GoogleWatermark")?.visibility == View.VISIBLE)
                assertTrue(readBooleanField(activity, "baseMapLoaded"))

                val detail = readDetail(activity)
                val summary = (list.adapter as RouteDetailAdapter).currentList
                    .filterIsInstance<RouteDetailUiItem.Summary>()
                    .single()
                assertEquals(detail.durationMinutes, summary.durationMinutes)
                assertEquals(detail.plannedArrivalTime, summary.plannedArrivalTime)
                assertEquals(detail.priceHkd, summary.priceHkd, 0.0)
                assertEquals(readWaitTimeState(activity), summary.firstLegEta)
                val expectedTimelineStops = detail.legs.sumOf { leg -> leg.viaStops.size + 2 }
                val presentation = requireNotNull(latestPresentation.get())
                val renderedTimelineStops = presentation.markers.flatMap { it.timelineStopIds }.distinct()
                assertTrue(expectedTimelineStops >= minimumStops)
                assertEquals(expectedTimelineStops, renderedTimelineStops.size)
                assertEquals(expectedBusLines, presentation.lines.count { it.kind == RouteMapLineKind.BUS })
                assertTrue(
                    presentation.lines.filter { it.kind == RouteMapLineKind.BUS }.all { it.points.size > 2 }
                )
                assertTrue(
                    "Expected at least $minimumWalkingLines real CSDI walking paths; events=$pedestrianEvents",
                    presentation.lines.count { it.kind == RouteMapLineKind.WALKING } >= minimumWalkingLines
                )
                assertCsdiAttributionSafety(activity, expectedVisible = minimumWalkingLines > 0)
                val busLine = presentation.lines.singleOrNull { it.kind == RouteMapLineKind.BUS }
                expectedBusLineStart?.let { expected ->
                    assertNotNull(busLine)
                    assertEquals(expected.latitude, busLine!!.points.first().latitude, 0.000000000001)
                    assertEquals(expected.longitude, busLine.points.first().longitude, 0.000000000001)
                }
                expectedBusLineEnd?.let { expected ->
                    assertNotNull(busLine)
                    assertEquals(expected.latitude, busLine!!.points.last().latitude, 0.000000000001)
                    assertEquals(expected.longitude, busLine.points.last().longitude, 0.000000000001)
                }
                inspectionFocus?.let { coordinate ->
                    readRenderer(activity).focusCoordinate(coordinate, inspectionZoom)
                }
            }
            if (inspectionFocus != null) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                Thread.sleep(1_500L)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
            scenario.onActivity { activity ->
                val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                val output = File(requireNotNull(activity.getExternalFilesDir(null)), screenshotName)
                FileOutputStream(output).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            if (InstrumentationRegistry.getArguments().getString(ARG_HOLD_FOR_INSPECTION) == "true") {
                val holdMillis = InstrumentationRegistry.getArguments()
                    .getString(ARG_HOLD_MILLIS)
                    ?.toLongOrNull()
                    ?.coerceIn(1_000L, 180_000L)
                    ?: 30_000L
                Thread.sleep(holdMillis)
            }
        }
    }

    private fun assertCsdiAttributionSafety(
        activity: RouteDetailActivity,
        expectedVisible: Boolean
    ) {
        val attribution = activity.findViewById<View>(R.id.routeDetailCsdiAttribution)
        assertEquals(expectedVisible, attribution.visibility == View.VISIBLE)
        if (!expectedVisible) return
        assertTrue(!attribution.contentDescription.isNullOrBlank())
        val attributionBounds = Rect()
        assertTrue(attribution.getGlobalVisibleRect(attributionBounds))
        listOf(
            activity.findViewById<View>(R.id.routeDetailFloatingBack),
            activity.findViewById(R.id.routeDetailMapControls),
            activity.findViewById(R.id.routeDetailSheet),
            requireNotNull(findViewWithTag(activity.window.decorView, "GoogleWatermark"))
        ).filter { it.visibility == View.VISIBLE }.forEach { protectedView ->
            val protectedBounds = Rect()
            assertTrue(protectedView.getGlobalVisibleRect(protectedBounds))
            assertTrue(
                "CSDI attribution overlaps protected map UI id=${protectedView.id}",
                !Rect.intersects(attributionBounds, protectedBounds)
            )
        }
    }

    private fun waitForRealContent(
        scenario: ActivityScenario<RouteDetailActivity>,
        latestPresentation: AtomicReference<RouteMapPresentation>,
        expectedBusLines: Int,
        minimumWalkingLines: Int
    ) {
        val deadline = SystemClock.uptimeMillis() + 30_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.findViewById<RecyclerView>(R.id.routeDetailList).adapter?.itemCount?.let { it > 4 } == true &&
                    findViewWithTag(activity.window.decorView, "GoogleWatermark")?.visibility == View.VISIBLE &&
                    readBooleanField(activity, "baseMapLoaded") &&
                    latestPresentation.get()?.lines?.count { it.kind == RouteMapLineKind.BUS } == expectedBusLines &&
                    latestPresentation.get()?.lines?.count { it.kind == RouteMapLineKind.WALKING }
                        ?.let { it >= minimumWalkingLines } == true
            }
            if (ready) return
            Thread.sleep(250)
        }
        scenario.onActivity { activity ->
            assertTrue("Google base map did not finish loading", readBooleanField(activity, "baseMapLoaded"))
            assertNotNull("Real Citybus presentation was not produced", latestPresentation.get())
            assertEquals(
                expectedBusLines,
                latestPresentation.get()?.lines?.count { it.kind == RouteMapLineKind.BUS }
            )
        }
    }

    private fun readBooleanField(activity: RouteDetailActivity, name: String): Boolean =
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.getBoolean(activity)

    private fun readIntField(
        scenario: ActivityScenario<RouteDetailActivity>,
        name: String
    ): Int {
        var value = 0
        scenario.onActivity { activity ->
            value = activity.javaClass.getDeclaredField(name)
                .apply { isAccessible = true }
                .getInt(activity)
        }
        return value
    }

    private fun readAutoRefreshState(
        scenario: ActivityScenario<RouteDetailActivity>
    ): ForegroundAutoRefreshState {
        var state: ForegroundAutoRefreshState? = null
        scenario.onActivity { activity ->
            val controller = activity.javaClass.getDeclaredField("detailAutoRefreshController")
                .apply { isAccessible = true }
                .get(activity) as ForegroundAutoRefreshController
            state = controller.state
        }
        return requireNotNull(state)
    }

    private fun waitForDetailAutoState(
        scenario: ActivityScenario<RouteDetailActivity>,
        stateClass: Class<out ForegroundAutoRefreshState>,
        timeoutMillis: Long = 10_000L
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (stateClass.isInstance(readAutoRefreshState(scenario))) return
            Thread.sleep(100L)
        }
        assertTrue(stateClass.isInstance(readAutoRefreshState(scenario)))
    }

    private fun waitForDetailCycle(
        scenario: ActivityScenario<RouteDetailActivity>,
        minimumDetailGeneration: Int,
        minimumEtaGeneration: Int,
        timeoutMillis: Long
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (
                readIntField(scenario, "detailGeneration") >= minimumDetailGeneration &&
                readIntField(scenario, "etaGeneration") >= minimumEtaGeneration &&
                readAutoRefreshState(scenario) is ForegroundAutoRefreshState.Waiting
            ) {
                return
            }
            Thread.sleep(200L)
        }
        assertTrue(readIntField(scenario, "detailGeneration") >= minimumDetailGeneration)
        assertTrue(readIntField(scenario, "etaGeneration") >= minimumEtaGeneration)
        assertTrue(readAutoRefreshState(scenario) is ForegroundAutoRefreshState.Waiting)
    }

    private fun assertStableRealDetail(scenario: ActivityScenario<RouteDetailActivity>) {
        scenario.onActivity { activity ->
            val detail = readDetail(activity)
            assertEquals("780", detail.routeName)
            assertTrue(detail.legs.isNotEmpty())
            assertTrue(readWaitTimeState(activity) !is WaitTimeState.Loading)
            val summary = (activity.findViewById<RecyclerView>(R.id.routeDetailList).adapter as RouteDetailAdapter)
                .currentList.filterIsInstance<RouteDetailUiItem.Summary>().single()
            assertEquals(detail.durationMinutes, summary.durationMinutes)
            assertEquals(detail.plannedArrivalTime, summary.plannedArrivalTime)
            assertEquals(detail.priceHkd, summary.priceHkd, 0.0)
            assertEquals(readWaitTimeState(activity), summary.firstLegEta)
        }
    }

    private fun saveActivityScreenshot(
        scenario: ActivityScenario<RouteDetailActivity>,
        name: String
    ) {
        scenario.onActivity { activity ->
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val output = File(requireNotNull(activity.getExternalFilesDir(null)), name)
            FileOutputStream(output).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun readDetail(activity: RouteDetailActivity): com.golink.busiscoming.data.model.RouteDetail =
        requireNotNull(
            activity.javaClass.getDeclaredField("detail").apply { isAccessible = true }.get(activity)
                as? com.golink.busiscoming.data.model.RouteDetail
        )

    private fun readWaitTimeState(activity: RouteDetailActivity): WaitTimeState =
        requireNotNull(
            activity.javaClass.getDeclaredField("waitTimeState").apply {
                isAccessible = true
            }.get(activity) as? WaitTimeState
        )

    private fun readRenderer(activity: RouteDetailActivity): GoogleRouteMapRenderer =
        requireNotNull(
            activity.javaClass.getDeclaredField("renderer").apply { isAccessible = true }.get(activity)
                as? GoogleRouteMapRenderer
        )

    private fun findViewWithTag(root: View, tag: String): View? {
        if (root.tag?.toString() == tag) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) findViewWithTag(root.getChildAt(index), tag)?.let { return it }
        return null
    }

    private companion object {
        const val ARG_RUN_REAL = "runRealRouteMap"
        const val ARG_RUN_REAL_AUTO = "runRealAutoRefresh"
        const val ARG_HOLD_FOR_INSPECTION = "holdRealRouteMap"
        const val ARG_HOLD_MILLIS = "holdRealRouteMapMillis"
        const val SINGLE_SCREENSHOT_NAME = "route-map-real-single.png"
        const val MULTI_SCREENSHOT_NAME = "route-map-real-multi.png"
        const val N118_HIGH_ZOOM_SCREENSHOT_NAME = "route-map-real-n118-high-zoom.png"
        const val REAL_AUTO_SCREENSHOT_NAME = "route-detail-real-auto-two-cycles.png"
    }
}
