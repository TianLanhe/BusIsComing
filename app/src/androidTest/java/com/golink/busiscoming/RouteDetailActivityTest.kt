package com.golink.busiscoming

import android.content.Intent
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.HorizontalScrollView
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
import androidx.test.platform.app.InstrumentationRegistry
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
import com.golink.busiscoming.data.model.RouteDetailTransfer
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteDetailWalkingKind
import com.golink.busiscoming.data.model.RouteDetailWalkingSegment
import com.golink.busiscoming.ui.main.RouteDetailActivity
import com.golink.busiscoming.ui.main.RouteDetailAdapter
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import com.golink.busiscoming.ui.main.RouteDetailRuntime
import com.golink.busiscoming.ui.main.RouteDetailUiItem
import com.golink.busiscoming.ui.main.RideStopCountState
import com.golink.busiscoming.ui.main.RouteDetailCameraPolicy
import com.golink.busiscoming.ui.main.RouteDetailCameraOwner
import com.golink.busiscoming.ui.main.RouteMapPresentation
import com.golink.busiscoming.ui.main.RouteMapMarkerRole
import com.golink.busiscoming.ui.main.RouteSummarySegmentKind
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import org.hamcrest.CoreMatchers.any
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.golink.busiscoming.data.repository.RouteGeometryDataSource
import com.golink.busiscoming.data.repository.RouteGeometryLoadHandle
import com.golink.busiscoming.data.repository.RouteGeometryRequest
import com.golink.busiscoming.data.model.RouteGeometryPoint
import com.golink.busiscoming.data.model.RouteGeometrySegment
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    fun mapControlsUseCenteredTwentyFourDpIcons() {
        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                val expectedButtonSize = (48f * density).toInt()
                val expectedIconSize = (24f * density).toInt()

                listOf(
                    R.id.routeDetailFloatingBack,
                    R.id.routeDetailLocation,
                    R.id.routeDetailOverview
                ).forEach { id ->
                    val button = activity.findViewById<MaterialButton>(id)
                    assertEquals(expectedButtonSize, button.width)
                    assertEquals(expectedButtonSize, button.height)
                    assertEquals(expectedIconSize, button.iconSize)
                    assertEquals(0, button.paddingLeft)
                    assertEquals(0, button.paddingTop)
                    assertEquals(0, button.paddingRight)
                    assertEquals(0, button.paddingBottom)
                    assertEquals(Gravity.CENTER, button.gravity and Gravity.CENTER)
                }
            }
        }
    }

    @Test
    fun compactSummaryKeepsFortyEightDpTouchHeightAndJourneyAccessibilityOrder() {
        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
            waitForTimelineItems(scenario)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.routeDetailList)
                val summaryRoot = requireNotNull(list.findViewHolderForAdapterPosition(0)).itemView as ViewGroup
                val content = summaryRoot.getChildAt(0) as ViewGroup
                val scroll = (0 until content.childCount)
                    .map(content::getChildAt)
                    .filterIsInstance<HorizontalScrollView>()
                    .single()
                val row = scroll.getChildAt(0) as ViewGroup
                val adapter = list.adapter as RouteDetailAdapter
                val summary = adapter.currentList.filterIsInstance<RouteDetailUiItem.Summary>().single()

                assertEquals(summary.segments.size, row.childCount)
                val visibleHeight = (30f * activity.resources.displayMetrics.density).toInt()
                for (index in 0 until row.childCount) {
                    val target = row.getChildAt(index)
                    assertEquals(visibleHeight, target.height)
                    assertTrue(target.isClickable)
                    assertTrue(target.isFocusable)
                    assertTrue(!target.contentDescription.isNullOrBlank())
                    if (index > 0) assertTrue(row.getChildAt(index - 1).right <= target.left)
                }

                val first = row.getChildAt(0)
                val bounds = Rect(0, 0, first.width, first.height)
                content.offsetDescendantRectToMyCoords(first, bounds)
                val minimumTouchHeight = (48f * activity.resources.displayMetrics.density).toInt()
                val expandedTop = bounds.top - (minimumTouchHeight - bounds.height()) / 2
                val eventY = (expandedTop + 1).toFloat()
                val eventX = bounds.centerX().toFloat()
                val delegate = requireNotNull(content.touchDelegate)
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, eventX, eventY, 0)
                val up = MotionEvent.obtain(downTime, downTime + 16L, MotionEvent.ACTION_UP, eventX, eventY, 0)
                try {
                    assertTrue(delegate.onTouchEvent(down))
                    assertTrue(delegate.onTouchEvent(up))
                } finally {
                    down.recycle()
                    up.recycle()
                }
            }
            waitForSheetState(scenario, BottomSheetBehavior.STATE_EXPANDED)
            assertFullScreenChrome(scenario)
        }
    }

    @Test
    fun mapCameraStartsOverHongKongBeforeRouteDataCompletes() {
        val release = CountDownLatch(1)
        RouteDetailRuntime.repositoryFactory = {
            object : com.golink.busiscoming.data.repository.RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail {
                    check(release.await(5, TimeUnit.SECONDS))
                    return detail()
                }
            }
        }

        try {
            ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
                val deadline = SystemClock.uptimeMillis() + 5_000L
                var camera: com.google.android.gms.maps.model.CameraPosition? = null
                while (SystemClock.uptimeMillis() < deadline && camera == null) {
                    scenario.onActivity { activity ->
                        val renderer = activity.javaClass.getDeclaredField("renderer").apply {
                            isAccessible = true
                        }.get(activity) ?: return@onActivity
                        val map = renderer.javaClass.getDeclaredField("map").apply {
                            isAccessible = true
                        }.get(renderer) as com.google.android.gms.maps.GoogleMap
                        camera = map.cameraPosition
                    }
                    if (camera == null) Thread.sleep(50L)
                }

                val initial = requireNotNull(camera)
                assertTrue(initial.target.latitude in 22.1..22.6)
                assertTrue(initial.target.longitude in 113.8..114.5)
                assertEquals(RouteDetailCameraPolicy.HONG_KONG_ZOOM, initial.zoom, 0.2f)
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun mapGestureTransfersCameraOwnershipToUser() {
        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
            val readyDeadline = SystemClock.uptimeMillis() + 5_000L
            var ready = false
            while (SystemClock.uptimeMillis() < readyDeadline && !ready) {
                scenario.onActivity { activity ->
                    ready = activity.javaClass.getDeclaredField("renderer").apply {
                        isAccessible = true
                    }.get(activity) != null
                }
                if (!ready) Thread.sleep(50L)
            }
            assertTrue(ready)

            onView(withId(R.id.routeDetailMap)).perform(swipeLeft())

            val ownerDeadline = SystemClock.uptimeMillis() + 3_000L
            var owner: RouteDetailCameraOwner? = null
            while (SystemClock.uptimeMillis() < ownerDeadline && owner != RouteDetailCameraOwner.USER) {
                scenario.onActivity { activity ->
                    owner = activity.javaClass.getDeclaredField("cameraOwner").apply {
                        isAccessible = true
                    }.get(activity) as RouteDetailCameraOwner
                }
                if (owner != RouteDetailCameraOwner.USER) Thread.sleep(50L)
            }
            assertEquals(RouteDetailCameraOwner.USER, owner)
        }
    }

    @Test
    fun floatingBackFinishesThePageFromMapVisibleState() {
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
            assertFullScreenChrome(scenario)

            scenario.recreate()
            waitForSheetState(scenario, BottomSheetBehavior.STATE_EXPANDED)
            assertFullScreenChrome(scenario)
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
        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
            onView(withId(R.id.routeDetailMapError)).check(matches(isDisplayed()))
            onView(withId(R.id.routeDetailSheetMapError)).check(matches(isDisplayed()))
            onView(withText("上車站")).check(matches(isDisplayed()))
            waitForSheetState(scenario, BottomSheetBehavior.STATE_EXPANDED)
            assertFullScreenChrome(scenario)
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
    fun etaDoesNotRefreshImmediatelyWhenReturningBeforeAutoRefreshDeadline() {
        val etaCalls = AtomicInteger()
        RouteDetailRuntime.etaResolver = {
            etaCalls.incrementAndGet()
            WaitTimeState.Available(7)
        }
        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
            waitForValue(etaCalls, 1)

            scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(150L)
            assertEquals(1, etaCalls.get())
            scenario.moveToState(Lifecycle.State.RESUMED)
            Thread.sleep(300L)
            assertEquals(1, etaCalls.get())
        }
    }

    @Test
    fun etaRefreshesAgainAfterSixtySecondsWhilePageStaysInForeground() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString(ARG_RUN_SIXTY_SECOND_ETA) == "true"
        )
        val etaCalls = AtomicInteger()
        RouteDetailRuntime.etaResolver = {
            etaCalls.incrementAndGet()
            WaitTimeState.Available(7)
        }

        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use {
            waitForValue(etaCalls, 1)
            val startedAt = SystemClock.uptimeMillis()
            val deadline = startedAt + 68_000L
            while (SystemClock.uptimeMillis() < deadline && etaCalls.get() < 2) Thread.sleep(250L)

            assertEquals(2, etaCalls.get())
            assertTrue(
                "The recurring ETA refresh ran before the 60-second interval",
                SystemClock.uptimeMillis() - startedAt >= 59_000L
            )
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
    fun stationCountMovesFromLoadingToAvailableOrUnavailableWithoutShowingZero() {
        val release = CountDownLatch(1)
        RouteDetailRuntime.repositoryFactory = {
            object : com.golink.busiscoming.data.repository.RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail {
                    check(release.await(2, TimeUnit.SECONDS))
                    return detail()
                }
            }
        }
        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
            waitForRideStopState(scenario) { it == RideStopCountState.Loading }
            release.countDown()
            waitForRideStopState(scenario) { it is RideStopCountState.Available && it.count > 0 }
        }

        RouteDetailRuntime.repositoryFactory = {
            object : com.golink.busiscoming.data.repository.RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail = error("detail unavailable")
            }
        }
        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
            waitForRideStopState(scenario) { it == RideStopCountState.Unavailable }
        }
    }

    @Test
    fun reenteringUsesCachedStructureWhileFreshDetailRunsConcurrently() {
        val cached = AtomicReference<RouteDetail?>()
        val loadCalls = AtomicInteger()
        val releaseSecondLoad = CountDownLatch(1)
        RouteDetailRuntime.repositoryFactory = {
            object : com.golink.busiscoming.data.repository.RouteDetailRepository {
                override fun loadCachedRouteDetail(route: BusRouteOption): RouteDetail? = cached.get()

                override fun loadRouteDetail(route: BusRouteOption): RouteDetail {
                    val call = loadCalls.incrementAndGet()
                    if (call == 2) check(releaseSecondLoad.await(3, TimeUnit.SECONDS))
                    return detail().also(cached::set)
                }
            }
        }

        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { first ->
            waitForTimelineItems(first)
        }

        try {
            ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { second ->
                waitForRideStopState(second) { it is RideStopCountState.Available && it.count > 0 }
                waitForValue(loadCalls, 2)
                waitForTimelineItems(second)
                releaseSecondLoad.countDown()
            }
        } finally {
            releaseSecondLoad.countDown()
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
                waitForExpandedViaToggle(scenario)
                onView(withText("1 個途經站 · 收起")).check(matches(withEffectiveVisibility(VISIBLE)))

                scenario.recreate()
                waitForExpandedViaToggle(scenario)
            }
        } finally {
            RouteDetailRuntime.reset()
        }
    }

    @Test
    fun partialGeometryRetryKeepsSuccessfulSegmentAndReloadsTheFailedSegment() {
        val route = twoLegRouteWithDetailQuery()
        val requestBatches = AtomicReference<List<List<String>>>(emptyList())
        RouteDetailRuntime.repositoryFactory = {
            object : com.golink.busiscoming.data.repository.RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail = twoLegDetail()
            }
        }
        RouteDetailRuntime.geometryRepositoryFactory = {
            object : RouteGeometryDataSource {
                override fun loadGeometries(
                    requests: List<RouteGeometryRequest>,
                    onResult: (RouteGeometryRequest, Result<RouteGeometrySegment>) -> Unit
                ): RouteGeometryLoadHandle {
                    requestBatches.updateAndGet { batches ->
                        batches + listOf(requests.map { it.key.routeVariant })
                    }
                    requests.forEach { request ->
                        val retryingOnlyFailedSegment = requests.size == 1
                        val secondSegment = request.key.routeVariant == "102-MEF-1"
                        val result = if (secondSegment && !retryingOnlyFailedSegment) {
                            Result.failure(IllegalStateException("second segment unavailable"))
                        } else {
                            Result.success(geometryFor(request.key))
                        }
                        onResult(request, result)
                    }
                    return RouteGeometryLoadHandle {}
                }
            }
        }
        RouteDetailRuntime.mapsAvailabilityChecker = { false }
        val latestPresentation = AtomicReference<RouteMapPresentation>()
        RouteDetailRuntime.presentationObserver = latestPresentation::set

        ActivityScenario.launch<RouteDetailActivity>(intent(route)).use { scenario ->
            waitForTimelineItems(scenario)
            waitForBusLineCount(latestPresentation, 1)
            val retainedLineId = requireNotNull(latestPresentation.get()).lines.single {
                it.kind == com.golink.busiscoming.ui.main.RouteMapLineKind.BUS
            }.stableId
            scenario.onActivity { activity ->
                val retry = activity.findViewById<View>(com.google.android.material.R.id.snackbar_action)
                assertTrue(retry.isShown)
                assertTrue(retry.isClickable)
                assertTrue(retry.performClick())
            }
            val retryDeadline = SystemClock.uptimeMillis() + 3_000L
            while (
                SystemClock.uptimeMillis() < retryDeadline &&
                requestBatches.get().lastOrNull() != listOf("102-MEF-1")
            ) Thread.sleep(50L)
            assertEquals(listOf("102-MEF-1"), requestBatches.get().lastOrNull())
            scenario.onActivity { activity ->
                val geometries = activity.javaClass.getDeclaredField("geometries").apply {
                    isAccessible = true
                }.get(activity) as Map<*, *>
                assertEquals(2, geometries.size)
            }
            waitForBusLineCount(latestPresentation, 2)

            val finalBusLines = requireNotNull(latestPresentation.get()).lines.filter {
                it.kind == com.golink.busiscoming.ui.main.RouteMapLineKind.BUS
            }
            assertTrue(finalBusLines.any { it.stableId == retainedLineId })
        }
    }

    @Test
    fun sameStopTransferWithMissingTimesAndFaresKeepsCompositeMarkerAndContinuousTimeline() {
        val sameStopDetail = twoLegDetail().copy(
            transfers = listOf(RouteDetailTransfer(RouteDetailTransferType.SAME_STOP)),
            plannedDepartureTime = null,
            plannedArrivalTime = null,
            legs = twoLegDetail().legs.map { leg ->
                leg.copy(
                    fareHkd = null,
                    plannedBoardingTime = null,
                    plannedAlightingTime = null
                )
            }
        )
        RouteDetailRuntime.repositoryFactory = {
            object : com.golink.busiscoming.data.repository.RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail = sameStopDetail
            }
        }
        RouteDetailRuntime.mapsAvailabilityChecker = { false }
        val latestPresentation = AtomicReference<RouteMapPresentation>()
        RouteDetailRuntime.presentationObserver = latestPresentation::set

        ActivityScenario.launch<RouteDetailActivity>(intent(twoLegRouteWithDetailQuery())).use { scenario ->
            waitForTimelineItems(scenario)
            val deadline = SystemClock.uptimeMillis() + 3_000L
            while (
                SystemClock.uptimeMillis() < deadline &&
                latestPresentation.get()?.markers?.none { it.role == RouteMapMarkerRole.TRANSFER } != false
            ) {
                Thread.sleep(50L)
            }
            scenario.onActivity { activity ->
                val items = (activity.findViewById<RecyclerView>(R.id.routeDetailList).adapter as RouteDetailAdapter)
                    .currentList
                val summary = items.filterIsInstance<RouteDetailUiItem.Summary>().single()
                assertEquals(
                    listOf(
                        RouteSummarySegmentKind.WALKING,
                        RouteSummarySegmentKind.BUS,
                        RouteSummarySegmentKind.SAME_STOP_TRANSFER,
                        RouteSummarySegmentKind.BUS,
                        RouteSummarySegmentKind.WALKING
                    ),
                    summary.segments.map { it.kind }
                )
                assertTrue(
                    items.any {
                        it is RouteDetailUiItem.Transfer &&
                            it.type == RouteDetailTransferType.SAME_STOP
                    }
                )
                assertTrue(
                    items.filterIsInstance<RouteDetailUiItem.Walking>()
                        .none { it.kind == RouteDetailWalkingKind.TRANSFER }
                )
                assertTrue(items.filterIsInstance<RouteDetailUiItem.BusLeg>().all { it.fareHkd == null })
                assertTrue(items.filterIsInstance<RouteDetailUiItem.Stop>().all { it.plannedTime == null })
            }
            val transfer = requireNotNull(latestPresentation.get()).markers.single {
                it.role == RouteMapMarkerRole.TRANSFER
            }
            assertEquals(setOf(0, 1), transfer.legIndexes)
            assertEquals(listOf("82X", "102"), transfer.routeLabels)
            assertEquals(2, transfer.timelineStopIds.size)
        }
    }

    @Test
    fun detailArrivingBeforeSlowGeometryDoesNotRestartRequestAndLineAppearsInPlace() {
        val loadCalls = AtomicInteger(0)
        val deliverGeometry = AtomicReference<(() -> Unit)?>(null)
        val latestPresentation = AtomicReference<RouteMapPresentation>()
        RouteDetailRuntime.presentationObserver = latestPresentation::set
        RouteDetailRuntime.mapsAvailabilityChecker = { false }
        RouteDetailRuntime.geometryRepositoryFactory = {
            object : RouteGeometryDataSource {
                override fun loadGeometries(
                    requests: List<RouteGeometryRequest>,
                    onResult: (RouteGeometryRequest, Result<RouteGeometrySegment>) -> Unit
                ): RouteGeometryLoadHandle {
                    loadCalls.incrementAndGet()
                    deliverGeometry.set {
                        requests.forEach { request -> onResult(request, Result.success(geometryFor(request.key))) }
                    }
                    return RouteGeometryLoadHandle {}
                }
            }
        }

        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
            waitForTimelineItems(scenario)
            assertEquals(1, loadCalls.get())

            requireNotNull(deliverGeometry.get()).invoke()

            waitForBusLineCount(latestPresentation, 1)
            assertEquals(1, loadCalls.get())
        }
    }

    @Test
    fun geometryArrivingBeforeDetailStaysHiddenThenPublishesWithoutRestart() {
        val releaseDetail = CountDownLatch(1)
        val detailStarted = CountDownLatch(1)
        val loadCalls = AtomicInteger(0)
        val latestPresentation = AtomicReference<RouteMapPresentation>()
        RouteDetailRuntime.presentationObserver = latestPresentation::set
        RouteDetailRuntime.mapsAvailabilityChecker = { false }
        RouteDetailRuntime.repositoryFactory = {
            object : com.golink.busiscoming.data.repository.RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail {
                    detailStarted.countDown()
                    check(releaseDetail.await(2, TimeUnit.SECONDS))
                    return detail()
                }
            }
        }
        RouteDetailRuntime.geometryRepositoryFactory = {
            object : RouteGeometryDataSource {
                override fun loadGeometries(
                    requests: List<RouteGeometryRequest>,
                    onResult: (RouteGeometryRequest, Result<RouteGeometrySegment>) -> Unit
                ): RouteGeometryLoadHandle {
                    loadCalls.incrementAndGet()
                    requests.forEach { request -> onResult(request, Result.success(geometryFor(request.key))) }
                    return RouteGeometryLoadHandle {}
                }
            }
        }

        ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
            assertTrue(detailStarted.await(2, TimeUnit.SECONDS))
            waitForBusLineCount(latestPresentation, 0)
            assertEquals(1, loadCalls.get())

            releaseDetail.countDown()
            waitForTimelineItems(scenario)

            waitForBusLineCount(latestPresentation, 1)
            assertEquals(1, loadCalls.get())
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

    private fun assertFullScreenChrome(scenario: ActivityScenario<RouteDetailActivity>) {
        scenario.onActivity { activity ->
            assertEquals(
                BottomSheetBehavior.STATE_EXPANDED,
                BottomSheetBehavior.from<View>(activity.findViewById(R.id.routeDetailSheet)).state
            )
            assertEquals(View.GONE, activity.findViewById<View>(R.id.routeDetailFloatingBack).visibility)
            assertTrue(activity.findViewById<View>(R.id.routeDetailSheetContent).paddingTop > 0)
            assertTrue(activity.findViewById<RecyclerView>(R.id.routeDetailList).adapter?.itemCount?.let { it > 0 } == true)
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

    private fun waitForRideStopState(
        scenario: ActivityScenario<RouteDetailActivity>,
        predicate: (RideStopCountState) -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + 3_000L
        while (SystemClock.uptimeMillis() < deadline) {
            var matched = false
            scenario.onActivity { activity ->
                val adapter = activity.findViewById<RecyclerView>(R.id.routeDetailList).adapter as? RouteDetailAdapter
                val summary = adapter?.currentList?.filterIsInstance<RouteDetailUiItem.Summary>()?.singleOrNull()
                matched = summary?.rideStopCount?.let(predicate) == true
            }
            if (matched) return
            Thread.sleep(50L)
        }
        scenario.onActivity { activity ->
            val adapter = activity.findViewById<RecyclerView>(R.id.routeDetailList).adapter as RouteDetailAdapter
            val summary = adapter.currentList.filterIsInstance<RouteDetailUiItem.Summary>().single()
            assertTrue("Unexpected ride stop state: ${summary.rideStopCount}", predicate(summary.rideStopCount))
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

    private fun waitForBusLineCount(presentation: AtomicReference<RouteMapPresentation>, expected: Int) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val count = presentation.get()?.lines?.count {
                it.kind == com.golink.busiscoming.ui.main.RouteMapLineKind.BUS
            }
            if (count == expected) return
            Thread.sleep(50L)
        }
        assertEquals(
            expected,
            presentation.get()?.lines?.count {
                it.kind == com.golink.busiscoming.ui.main.RouteMapLineKind.BUS
            }
        )
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

    private fun twoLegRouteWithDetailQuery(): BusRouteOption {
        val rawInfo = "2|*|CTB||82X-ISR-1||6||9||O|*|CTB||102-MEF-1||12||15||O|*|"
        val legs = listOf(
            P2pRouteLeg("CTB", "82X-ISR-1", "82X", 6, 9, "O", "outbound"),
            P2pRouteLeg("CTB", "102-MEF-1", "102", 12, 15, "O", "outbound")
        )
        return BusRouteOption(
            routeName = "82X → 102",
            routeSegments = listOf("82X", "102"),
            priceHkd = 20.0,
            durationMinutes = 35,
            arrivalMinutes = 35,
            transferCount = 1,
            walkingDistanceMeters = 0,
            waitTimeState = WaitTimeState.Loading,
            routeDetailQuery = P2pRouteDetailQuery(
                rawInfo,
                "02:04|*|35",
                "0",
                "0",
                P2pRoutePlan(rawInfo, "0", legs)
            )
        )
    }

    private fun twoLegDetail(): RouteDetail {
        val firstBoard = stop("第一段上車", 6, RouteDetailStopRole.BOARDING, "82X-ISR-1", 22.3000, 114.1000)
        val firstAlight = stop("轉乘站", 9, RouteDetailStopRole.ALIGHTING, "82X-ISR-1", 22.3100, 114.1100)
        val secondBoard = stop("轉乘站", 12, RouteDetailStopRole.BOARDING, "102-MEF-1", 22.3100, 114.1100)
        val secondAlight = stop("第二段下車", 15, RouteDetailStopRole.ALIGHTING, "102-MEF-1", 22.3200, 114.1200)
        return RouteDetail(
            routeName = "82X → 102",
            priceHkd = 20.0,
            durationMinutes = 35,
            walkingDistanceMeters = 0,
            legs = listOf(
                RouteDetailLeg("82X", "82X-ISR-1", null, firstBoard, emptyList(), firstAlight),
                RouteDetailLeg("102", "102-MEF-1", null, secondBoard, emptyList(), secondAlight)
            )
        )
    }

    private fun geometryFor(key: com.golink.busiscoming.data.model.RouteGeometryKey): RouteGeometrySegment {
        val points = if (key.routeVariant == "82X-ISR-1") {
            listOf(
                RouteGeometryPoint("first-1", 22.3000, 114.1000),
                RouteGeometryPoint("first-2", 22.3050, 114.1050),
                RouteGeometryPoint("first-3", 22.3100, 114.1100)
            )
        } else {
            listOf(
                RouteGeometryPoint("second-1", 22.3100, 114.1100),
                RouteGeometryPoint("second-2", 22.3150, 114.1150),
                RouteGeometryPoint("second-3", 22.3200, 114.1200)
            )
        }
        return RouteGeometrySegment(key, points)
    }

    private fun stop(name: String, sequence: Int, role: RouteDetailStopRole) = RouteDetailStop(
        name, name, sequence.toString(), sequence, 22.0, 114.0, "N118-TOS-1", role
    )

    private fun stop(
        name: String,
        sequence: Int,
        role: RouteDetailStopRole,
        routeVariant: String,
        latitude: Double,
        longitude: Double
    ) = RouteDetailStop(
        name,
        name,
        sequence.toString(),
        sequence,
        latitude,
        longitude,
        routeVariant,
        role
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

    private companion object {
        const val ARG_RUN_SIXTY_SECOND_ETA = "runSixtySecondEta"
    }

}
