package com.golink.busiscoming

import android.content.Intent
import android.view.View
import android.view.MotionEvent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailWalkingKind
import com.golink.busiscoming.data.model.RouteDetailWalkingSegment
import com.golink.busiscoming.ui.main.RouteDetailActivity
import com.golink.busiscoming.ui.main.RouteDetailAdapter
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import com.golink.busiscoming.ui.main.RouteDetailRuntime
import com.golink.busiscoming.ui.main.RouteDetailUiItem
import com.golink.busiscoming.ui.main.RouteMapPresentation
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import org.hamcrest.CoreMatchers.any
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.golink.busiscoming.data.repository.RouteGeometryDataSource
import com.golink.busiscoming.data.repository.RouteGeometryLoadHandle
import com.golink.busiscoming.data.repository.RouteGeometryRequest
import com.golink.busiscoming.data.model.RouteGeometrySegment
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class RouteDetailActivityTest {
    @Before
    fun setUpRuntime() {
        RouteDetailRuntime.repositoryFactory = {
            object : com.golink.busiscoming.data.repository.RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail = detail()
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
    }

    @After
    fun resetRuntime() {
        RouteDetailRuntime.reset()
    }

    @Test
    fun mapBackgroundAndPersistentSummarySheetAppearImmediately() {
        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use {
            onView(withId(R.id.routeDetailMap)).check(matches(isDisplayed()))
            onView(withId(R.id.routeDetailSheet)).check(matches(isDisplayed()))
            onView(withId(R.id.routeDetailSheetHandle)).check(matches(isDisplayed()))
            onView(withId(R.id.routeDetailFloatingBack)).check(matches(isDisplayed()))
            onView(withId(R.id.routeDetailList)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun toolbarNavigateUpFinishesTheFullScreenPage() {
        val scenario = ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery()))
        try {
            onView(withId(R.id.routeDetailFloatingBack)).perform(click())
            waitForDestroyed(scenario)

            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun summarySwipeExpandsDirectlyToFullAndRecreationRestoresIt() {
        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
            scenario.onActivity { activity ->
                val target = activity.findViewById<RecyclerView>(R.id.routeDetailList)
                val now = SystemClock.uptimeMillis()
                target.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, target.width / 2f, target.height - 8f, 0))
                target.dispatchTouchEvent(MotionEvent.obtain(now, now + 120L, MotionEvent.ACTION_UP, target.width / 2f, 8f, 0))
            }
            waitForSheetState(scenario, BottomSheetBehavior.STATE_EXPANDED)
            onView(withId(R.id.routeDetailToolbar)).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                assertEquals(
                    BottomSheetBehavior.STATE_EXPANDED,
                    BottomSheetBehavior.from(activity.findViewById(R.id.routeDetailSheet)).state
                )
            }

            scenario.recreate()
            onView(withId(R.id.routeDetailToolbar)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun systemBackExitsDirectlyFromHalfDetent() {
        val scenario = ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery()))
        try {
            scenario.onActivity { activity ->
                BottomSheetBehavior.from<android.view.View>(activity.findViewById(R.id.routeDetailSheet)).state =
                    BottomSheetBehavior.STATE_HALF_EXPANDED
            }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitForDestroyed(scenario)
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun unavailableBaseMapForcesFullTextDetails() {
        RouteDetailRuntime.mapsAvailabilityChecker = { false }
        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use {
            onView(withId(R.id.routeDetailMapError)).check(matches(isDisplayed()))
            onView(withId(R.id.routeDetailSheetMapError)).check(matches(isDisplayed()))
            onView(withId(R.id.routeDetailToolbar)).check(matches(isDisplayed()))
            onView(withText("上車站")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun markerAndTimelineSelectionStayLinkedAcrossSheetDetents() {
        val latestPresentation = AtomicReference<RouteMapPresentation>()
        RouteDetailRuntime.presentationObserver = latestPresentation::set
        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
            waitForTimelineItems(scenario)
            val boardingMarkerId = requireNotNull(latestPresentation.get()).markers.first {
                it.timelineStopIds.isNotEmpty()
            }.stableId

            scenario.onActivity { activity ->
                activity.javaClass.getDeclaredMethod("onMapMarkerSelected", String::class.java).apply {
                    isAccessible = true
                }.invoke(activity, boardingMarkerId)
            }
            waitForSheetState(scenario, BottomSheetBehavior.STATE_HALF_EXPANDED)
            assertTrue(requireNotNull(latestPresentation.get()).markers.single { it.stableId == boardingMarkerId }.selected)

            scenario.onActivity { activity ->
                BottomSheetBehavior.from<View>(activity.findViewById(R.id.routeDetailSheet)).state =
                    BottomSheetBehavior.STATE_EXPANDED
            }
            onView(withText("上車站")).perform(clickClickableParent())
            waitForSheetState(scenario, BottomSheetBehavior.STATE_HALF_EXPANDED)
            assertTrue(requireNotNull(latestPresentation.get()).markers.single { it.stableId == boardingMarkerId }.selected)
        }
    }

    @Test
    fun etaRefreshStopsInBackgroundAndRefreshesStaleValueOnReturn() {
        val etaCalls = AtomicInteger()
        RouteDetailRuntime.etaResolver = {
            etaCalls.incrementAndGet()
            WaitTimeState.Available(7)
        }
        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
            waitForValue(etaCalls, 1)
            scenario.onActivity { activity ->
                activity.javaClass.getDeclaredField("lastEtaSuccessMillis").apply {
                    isAccessible = true
                }.set(activity, 0L)
            }

            scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(150L)
            assertEquals(1, etaCalls.get())
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForValue(etaCalls, 2)
        }
    }

    @Test
    fun summaryAndRecoverableMissingMetadataStateSurviveRecreation() {
        val route = BusRouteOption(
            routeName = "N118",
            routeSegments = listOf("N118"),
            priceHkd = 17.8,
            durationMinutes = 13,
            arrivalMinutes = 4,
            transferCount = 0,
            walkingDistanceMeters = 262,
            waitTimeState = WaitTimeState.Available(4)
        )
        val intent = Intent(ApplicationProvider.getApplicationContext(), RouteDetailActivity::class.java)
            .putExtras(RouteDetailLaunchArgs.fromRoute(route).toBundle())

        ActivityScenario.launch<RouteDetailActivity>(intent).use { scenario ->
            onView(withId(R.id.routeDetailFloatingBack)).check(matches(isDisplayed()))
            onView(withText("N118")).check(matches(isDisplayed()))
            onView(withText(R.string.route_detail_unavailable)).check(matches(isDisplayed()))
            onView(withId(R.id.routeDetailSheetHandle)).perform(click())

            scenario.recreate()

            onView(withText("N118")).check(matches(isDisplayed()))
            onView(withText(R.string.route_detail_unavailable)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun successfulLoadRetryAndExpandedStateRestorationUseTheFlatTimeline() {
        val shouldFail = AtomicBoolean(true)
        val loadCalls = AtomicInteger(0)
        RouteDetailRuntime.repositoryFactory = {
            object : com.golink.busiscoming.data.repository.RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail {
                    loadCalls.incrementAndGet()
                    if (shouldFail.get()) error("request fails until retry")
                    return detail()
                }
            }
        }
        RouteDetailRuntime.etaResolver = { WaitTimeState.Available(6) }
        RouteDetailRuntime.mapsAvailabilityChecker = { false }
        try {
            val retryRoute = routeWithDetailQuery().copy(firstLegEtaQuery = null)
            ActivityScenario.launch<RouteDetailActivity>(intent(retryRoute)).use { scenario ->
                Thread.sleep(250)
                onView(withText(R.string.route_detail_unavailable)).check(matches(isDisplayed()))
                shouldFail.set(false)
                scenario.onActivity { activity ->
                    val retry = findViewWithContentDescription(
                        activity.window.decorView,
                        activity.getString(R.string.route_detail_retry)
                    )
                    assertTrue(requireNotNull(retry).performClick())
                }
                waitForValue(loadCalls, 2)
                waitForTimelineItems(scenario)
                onView(withText("上車站")).check(matches(isDisplayed()))
                onView(withText("1 個途經站")).perform(clickClickableParent())
                onView(withText("1 個途經站 · 收起")).check(matches(withEffectiveVisibility(VISIBLE)))

                scenario.recreate()
                waitForExpandedViaToggle(scenario)
            }
        } finally {
            RouteDetailRuntime.reset()
        }
    }

    private fun intent(route: BusRouteOption): Intent {
        return Intent(ApplicationProvider.getApplicationContext(), RouteDetailActivity::class.java)
            .putExtras(RouteDetailLaunchArgs.fromRoute(route).toBundle())
    }

    private fun waitForSheetState(scenario: ActivityScenario<RouteDetailActivity>, expectedState: Int) {
        val deadline = SystemClock.uptimeMillis() + 3_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var matches = false
            scenario.onActivity { activity ->
                matches = BottomSheetBehavior.from<android.view.View>(activity.findViewById(R.id.routeDetailSheet)).state == expectedState
            }
            if (matches) return
            Thread.sleep(50)
        }
        scenario.onActivity { activity ->
            assertEquals(expectedState, BottomSheetBehavior.from<android.view.View>(activity.findViewById(R.id.routeDetailSheet)).state)
        }
    }

    private fun waitForTimelineItems(scenario: ActivityScenario<RouteDetailActivity>) {
        val deadline = SystemClock.uptimeMillis() + 3_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var loaded = false
            scenario.onActivity { activity ->
                loaded = activity.findViewById<RecyclerView>(R.id.routeDetailList).adapter?.itemCount?.let { it > 2 } == true
            }
            if (loaded) return
            Thread.sleep(50)
        }
    }

    private fun waitForExpandedViaToggle(scenario: ActivityScenario<RouteDetailActivity>) {
        val deadline = SystemClock.uptimeMillis() + 3_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var restored = false
            scenario.onActivity { activity ->
                val adapter = activity.findViewById<RecyclerView>(R.id.routeDetailList).adapter as? RouteDetailAdapter
                restored = adapter?.currentList?.any {
                    it is RouteDetailUiItem.ViaToggle && it.legIndex == 0 && it.expanded
                } == true
            }
            if (restored) return
            Thread.sleep(50)
        }
        scenario.onActivity { activity ->
            val adapter = activity.findViewById<RecyclerView>(R.id.routeDetailList).adapter as RouteDetailAdapter
            assertTrue(adapter.currentList.any {
                it is RouteDetailUiItem.ViaToggle && it.legIndex == 0 && it.expanded
            })
        }
    }

    private fun waitForValue(value: AtomicInteger, expected: Int) {
        val deadline = SystemClock.uptimeMillis() + 3_000L
        while (SystemClock.uptimeMillis() < deadline && value.get() < expected) Thread.sleep(50)
        assertEquals(expected, value.get())
    }

    private fun waitForDestroyed(scenario: ActivityScenario<RouteDetailActivity>) {
        val deadline = SystemClock.uptimeMillis() + 3_000L
        while (SystemClock.uptimeMillis() < deadline && scenario.state != Lifecycle.State.DESTROYED) Thread.sleep(50)
    }

    private fun findViewWithContentDescription(root: View, description: String): View? {
        if (root.contentDescription?.toString() == description) return root
        if (root !is android.view.ViewGroup) return null
        for (index in 0 until root.childCount) {
            findViewWithContentDescription(root.getChildAt(index), description)?.let { return it }
        }
        return null
    }

    private fun routeWithDetailQuery(): BusRouteOption {
        val leg = P2pRouteLeg("CTB", "N118-TOS-1", "N118", 5, 7, "O", "outbound")
        return BusRouteOption(
            routeName = "N118",
            routeSegments = listOf("N118"),
            priceHkd = 17.8,
            durationMinutes = 13,
            arrivalMinutes = 4,
            transferCount = 0,
            walkingDistanceMeters = 262,
            waitTimeState = WaitTimeState.Available(4),
            firstLegEtaQuery = FirstLegEtaQuery("CTB", leg.routeVariant, leg.route, 5, 7, "O", "outbound", "raw", "0"),
            routeDetailQuery = P2pRouteDetailQuery("raw", "02:04|*|13", "0", "0", P2pRoutePlan("raw", "0", listOf(leg)))
        )
    }

    private fun detail(): RouteDetail {
        val board = stop("上車站", 5, RouteDetailStopRole.BOARDING)
        val via = stop("途經站", 6, RouteDetailStopRole.VIA)
        val alight = stop("下車站", 7, RouteDetailStopRole.ALIGHTING)
        return RouteDetail(
            routeName = "N118",
            priceHkd = 17.8,
            durationMinutes = 13,
            walkingDistanceMeters = 262,
            legs = listOf(RouteDetailLeg("N118", "N118-TOS-1", "長沙灣", board, listOf(via), alight, 17.8, "02:01", "02:04")),
            originWalking = RouteDetailWalkingSegment(RouteDetailWalkingKind.ORIGIN, 236),
            destinationWalking = RouteDetailWalkingSegment(RouteDetailWalkingKind.DESTINATION, 26),
            plannedDepartureTime = "01:51",
            plannedArrivalTime = "02:04",
            originName = "起點",
            destinationName = "終點"
        )
    }

    private fun stop(name: String, sequence: Int, role: RouteDetailStopRole) = RouteDetailStop(
        name, name, sequence.toString(), sequence, 22.0, 114.0, "N118-TOS-1", role
    )

    private fun clickClickableParent(): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = any(View::class.java)
        override fun getDescription(): String = "click the nearest clickable timeline row"
        override fun perform(uiController: UiController, view: View) {
            var target: View? = view
            while (target != null && !target.isClickable) target = target.parent as? View
            checkNotNull(target).performClick()
            uiController.loopMainThreadUntilIdle()
        }
    }

}
