package com.golink.busiscoming

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.golink.busiscoming.data.location.CurrentLocationSnapshot
import com.golink.busiscoming.data.location.ForegroundLocationHeadingState
import com.golink.busiscoming.data.location.ForegroundLocationHeadingTracker
import com.golink.busiscoming.data.location.SystemLocationUtils
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteGeometrySegment
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.RouteDetailRepository
import com.golink.busiscoming.data.repository.RouteGeometryDataSource
import com.golink.busiscoming.data.repository.RouteGeometryLoadHandle
import com.golink.busiscoming.data.repository.RouteGeometryRequest
import com.golink.busiscoming.ui.main.GoogleRouteMapRenderer
import com.golink.busiscoming.ui.main.RouteDetailActivity
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import com.golink.busiscoming.ui.main.RouteDetailRuntime
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteDetailLocationHeadingInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var tracker: FakeLocationHeadingTracker

    @get:Rule
    val locationPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Before
    fun setUpRuntime() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString(ARG_RUN_LOCATION_HEADING) == "true"
        )
        assumeTrue(SystemLocationUtils.isLocationEnabled(context))
        tracker = FakeLocationHeadingTracker()
        RouteDetailRuntime.locationHeadingTrackerFactory = { tracker }
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
    }

    @After
    fun resetRuntime() = RouteDetailRuntime.reset()

    @Test
    fun stationaryPhoneHeadingDrivesOpaqueMarkerAndClickDoesNotEnableCameraFollow() {
        ActivityScenario.launch<RouteDetailActivity>(intent()).use { scenario ->
            val renderer = waitForRenderer(scenario)
            val firstLocation = location(22.3193, 114.1694)

            scenario.onActivity {
                tracker.emit(ForegroundLocationHeadingState(location = firstLocation))
            }
            awaitNextDisplayFrame()
            assertTrue(renderer.currentLocationSnapshot().hasAccuracyArea)
            assertFalse(renderer.currentLocationSnapshot().hasDirectionMarker)

            scenario.onActivity {
                tracker.emit(
                    ForegroundLocationHeadingState(
                        location = firstLocation,
                        headingDegrees = 90f
                    )
                )
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(200L)
            assertTrue(renderer.currentLocationSnapshot().hasDirectionMarker)
            assertEquals(90f, renderer.currentLocationSnapshot().headingDegrees ?: -1f, 0.5f)

            onView(withId(R.id.routeDetailLocation)).perform(click())
            val focusedLatitude = readCameraLatitude(scenario)
            val focusedLongitude = readCameraLongitude(scenario)
            assertTrue(abs(focusedLatitude - firstLocation.latitude) < 0.0001)
            assertTrue(abs(focusedLongitude - firstLocation.longitude) < 0.0001)

            scenario.onActivity {
                tracker.emit(
                    ForegroundLocationHeadingState(
                        location = location(22.3205, 114.1710),
                        headingDegrees = 180f
                    )
                )
            }
            awaitNextDisplayFrame()
            SystemClock.sleep(200L)
            instrumentation.waitForIdleSync()
            assertEquals(180f, renderer.currentLocationSnapshot().headingDegrees ?: -1f, 0.5f)
            assertEquals(focusedLatitude, readCameraLatitude(scenario), 0.0)
            assertEquals(focusedLongitude, readCameraLongitude(scenario), 0.0)

            scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(renderer.currentLocationSnapshot().hasDirectionMarker)
            assertFalse(renderer.currentLocationSnapshot().hasAccuracyArea)
            assertTrue(tracker.stopCount >= 1)
        }
    }

    @Test
    fun calibrationPromptAppearsOnceAndClearsWhenConfidenceRecovers() {
        ActivityScenario.launch<RouteDetailActivity>(intent()).use { scenario ->
            waitForRenderer(scenario)
            val currentLocation = location(22.3193, 114.1694)

            scenario.onActivity {
                tracker.emit(
                    ForegroundLocationHeadingState(
                        location = currentLocation,
                        headingDegrees = 45f,
                        calibrationRequired = true,
                        calibrationPromptSequence = 1L
                    )
                )
            }
            onView(withText(R.string.route_map_heading_calibration_required))
                .check(matches(isDisplayed()))

            scenario.onActivity {
                tracker.emit(
                    ForegroundLocationHeadingState(
                        location = currentLocation,
                        headingDegrees = 50f,
                        calibrationRequired = false,
                        calibrationPromptSequence = 1L
                    )
                )
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300L)
            onView(withText(R.string.route_map_heading_calibration_required)).check(doesNotExist())
        }
    }

    @Test
    fun unavailableMapNeverStartsLocationOrHeadingTracking() {
        RouteDetailRuntime.mapsAvailabilityChecker = { false }

        ActivityScenario.launch<RouteDetailActivity>(intent()).use {
            instrumentation.waitForIdleSync()

            assertEquals(0, tracker.startCount)
        }
    }

    @Test
    fun systemLocationModeChangeStopsClearsAndRestartsTracking() {
        var systemLocationEnabled = true
        RouteDetailRuntime.systemLocationEnabledChecker = { systemLocationEnabled }

        ActivityScenario.launch<RouteDetailActivity>(intent()).use { scenario ->
            val renderer = waitForRenderer(scenario)
            val currentLocation = location(22.3193, 114.1694)
            scenario.onActivity {
                tracker.emit(
                    ForegroundLocationHeadingState(
                        location = currentLocation,
                        headingDegrees = 45f
                    )
                )
            }
            awaitNextDisplayFrame()
            assertEquals(1, tracker.startCount)
            assertTrue(renderer.currentLocationSnapshot().hasDirectionMarker)

            systemLocationEnabled = false
            sendLocationModeChanged(scenario)
            instrumentation.waitForIdleSync()

            assertEquals(1, tracker.stopCount)
            assertFalse(renderer.currentLocationSnapshot().hasDirectionMarker)
            assertFalse(renderer.currentLocationSnapshot().hasAccuracyArea)

            systemLocationEnabled = true
            sendLocationModeChanged(scenario)
            instrumentation.waitForIdleSync()

            assertEquals(2, tracker.startCount)
        }
    }

    @Test
    fun pausedActivityCannotRestartTrackingFromLateMapWork() {
        ActivityScenario.launch<RouteDetailActivity>(intent()).use { scenario ->
            waitForRenderer(scenario)
            assertEquals(1, tracker.startCount)
            assertTrue(readBoolean(scenario, "locationModeReceiverRegistered"))

            scenario.moveToState(Lifecycle.State.CREATED)
            val startsBeforeLateWork = tracker.startCount
            assertFalse(readBoolean(scenario, "locationModeReceiverRegistered"))
            scenario.onActivity { activity ->
                activity.javaClass.getDeclaredMethod("updateMyLocationTracking").apply {
                    isAccessible = true
                }.invoke(activity)
            }
            instrumentation.waitForIdleSync()

            assertEquals(startsBeforeLateWork, tracker.startCount)
        }
    }

    private fun intent(): Intent {
        val route = DemoScreenshotFixtures.primaryRoute()
        return Intent(context, RouteDetailActivity::class.java).putExtras(
            RouteDetailLaunchArgs.fromRoute(
                route,
                Place("模擬起點", 22.3000, 114.1700),
                Place("模擬終點", 22.2900, 114.1900)
            ).toBundle()
        )
    }

    private fun waitForRenderer(
        scenario: ActivityScenario<RouteDetailActivity>
    ): GoogleRouteMapRenderer {
        var renderer: GoogleRouteMapRenderer? = null
        val deadline = SystemClock.uptimeMillis() + 30_000L
        while (SystemClock.uptimeMillis() < deadline && renderer == null) {
            scenario.onActivity { activity ->
                renderer = activity.javaClass.getDeclaredField("renderer").apply {
                    isAccessible = true
                }.get(activity) as? GoogleRouteMapRenderer
            }
            if (renderer == null) SystemClock.sleep(100L)
        }
        return requireNotNull(renderer) { "Google map renderer did not become ready" }
    }

    private fun readCameraLatitude(scenario: ActivityScenario<RouteDetailActivity>): Double =
        readNullableDouble(scenario, "cameraLatitude") ?: error("camera latitude unavailable")

    private fun readCameraLongitude(scenario: ActivityScenario<RouteDetailActivity>): Double =
        readNullableDouble(scenario, "cameraLongitude") ?: error("camera longitude unavailable")

    private fun readNullableDouble(
        scenario: ActivityScenario<RouteDetailActivity>,
        fieldName: String
    ): Double? {
        var value: Double? = null
        scenario.onActivity { activity ->
            value = activity.javaClass.getDeclaredField(fieldName).apply {
                isAccessible = true
            }.get(activity) as? Double
        }
        return value
    }

    private fun readBoolean(
        scenario: ActivityScenario<RouteDetailActivity>,
        fieldName: String
    ): Boolean {
        var value = false
        scenario.onActivity { activity ->
            value = activity.javaClass.getDeclaredField(fieldName).apply {
                isAccessible = true
            }.getBoolean(activity)
        }
        return value
    }

    private fun sendLocationModeChanged(scenario: ActivityScenario<RouteDetailActivity>) {
        scenario.onActivity { activity ->
            val receiver = activity.javaClass.getDeclaredField("locationModeChangedReceiver").apply {
                isAccessible = true
            }.get(activity) as BroadcastReceiver
            receiver.onReceive(activity, Intent(LocationManager.MODE_CHANGED_ACTION))
        }
    }

    private fun awaitNextDisplayFrame() {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(100L)
        instrumentation.waitForIdleSync()
    }

    private fun location(latitude: Double, longitude: Double) = CurrentLocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 12f,
        elapsedRealtimeMillis = SystemClock.elapsedRealtime()
    )

    private companion object {
        const val ARG_RUN_LOCATION_HEADING = "runRouteDetailLocationHeading"
    }
}

private class FakeLocationHeadingTracker : ForegroundLocationHeadingTracker {
    private var observer: ((ForegroundLocationHeadingState) -> Unit)? = null
    private var latestLocation: CurrentLocationSnapshot? = null
    var stopCount = 0
        private set
    var startCount = 0
        private set

    override fun start(observer: (ForegroundLocationHeadingState) -> Unit) {
        startCount += 1
        this.observer = observer
        observer(ForegroundLocationHeadingState())
    }

    override fun stop() {
        stopCount += 1
        observer = null
        latestLocation = null
    }

    override fun latestLocation(): CurrentLocationSnapshot? = latestLocation

    fun emit(state: ForegroundLocationHeadingState) {
        latestLocation = state.location
        observer?.invoke(state)
    }
}
