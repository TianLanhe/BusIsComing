package com.golink.busiscoming

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
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
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteDetailLocationPermissionInstrumentedTest {
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
    fun restoreEnvironment() {
        executeShell("settings put secure location_mode 3")
        RouteDetailRuntime.reset()
    }

    @Test
    fun locationButtonRequestsPermissionThenFocusesInjectedLocation() {
        assumeDeviceValidationEnabled()
        assertTrue("Test must start without location permission", !hasForegroundLocationPermission())

        ActivityScenario.launch<RouteDetailActivity>(intent()).use { scenario ->
            waitForActivityWindow()
            assertNull("Opening route details must not request location automatically", findPermissionNode(ALLOW_BUTTON_ID))

            onView(withId(R.id.routeDetailLocation)).perform(click())
            val allowButton = waitForPermissionNode(ALLOW_BUTTON_ID)
            saveScreenshot(scenario, "route-detail-location-permission-dialog.png")
            assertTrue(allowButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitUntil("foreground location permission") { hasForegroundLocationPermission() }
            SystemClock.sleep(5_000L)
            onView(withId(R.id.routeDetailLocation)).perform(click())
            waitForCameraTarget(scenario, EXPECTED_LATITUDE, EXPECTED_LONGITUDE)
            saveScreenshot(scenario, "route-detail-location-granted.png")
        }
    }

    @Test
    fun grantedPermissionWithSystemLocationOffShowsLocalizedRecovery() {
        assumeDeviceValidationEnabled()
        assertTrue("Test must start with location permission", hasForegroundLocationPermission())
        executeShell("settings put secure location_mode 0")

        ActivityScenario.launch<RouteDetailActivity>(intent()).use { scenario ->
            onView(withId(R.id.routeDetailLocation)).perform(click())
            onView(withText(R.string.route_map_location_disabled)).check(matches(isDisplayed()))
            onView(withText(R.string.settings)).check(matches(isDisplayed()))
            onView(withId(R.id.routeDetailSheet)).check(matches(isDisplayed()))
            saveScreenshot(scenario, "route-detail-location-disabled.png")
        }
    }

    @Test
    fun repeatedLocationDenialOffersSettingsWithoutBreakingRouteDetails() {
        assumeDeviceValidationEnabled()
        assertTrue("Test must start without location permission", !hasForegroundLocationPermission())

        ActivityScenario.launch<RouteDetailActivity>(intent()).use { scenario ->
            onView(withId(R.id.routeDetailLocation)).perform(click())
            val firstDeny = waitForPermissionNode(DENY_BUTTON_ID)
            assertTrue(firstDeny.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            onView(withText(R.string.route_map_location_permission_denied)).check(matches(isDisplayed()))

            onView(withId(R.id.routeDetailLocation)).perform(click())
            val secondDeny = waitForAnyPermissionNode(
                DENY_AND_DONT_ASK_AGAIN_BUTTON_ID,
                DENY_DONT_ASK_AGAIN_BUTTON_ID,
                DENY_BUTTON_ID
            )
            assertTrue(secondDeny.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            onView(withText(R.string.route_map_location_permission_settings)).check(matches(isDisplayed()))
            onView(withText(R.string.settings)).check(matches(isDisplayed()))
            onView(withId(R.id.routeDetailSheet)).check(matches(isDisplayed()))
            saveScreenshot(scenario, "route-detail-location-permanently-denied.png")
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

    private fun waitForActivityWindow() {
        waitUntil("route detail accessibility window") {
            instrumentation.uiAutomation.windows.any { window ->
                window.root?.packageName?.toString() == context.packageName
            }
        }
    }

    private fun waitForPermissionNode(viewId: String): AccessibilityNodeInfo =
        requireNotNull(findPermissionNode(viewId, 8_000L)) {
            "Timed out waiting for permission controller node $viewId"
        }

    private fun waitForAnyPermissionNode(vararg viewIds: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < deadline) {
            viewIds.forEach { viewId -> findPermissionNode(viewId)?.let { return it } }
            SystemClock.sleep(100L)
        }
        error("Timed out waiting for one of the permission controller nodes ${viewIds.toList()}")
    }

    private fun findPermissionNode(viewId: String, timeoutMillis: Long = 0L): AccessibilityNodeInfo? {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        do {
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.windows.forEach { window ->
                window.root?.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()?.let { return it }
            }
            if (timeoutMillis == 0L) return null
            SystemClock.sleep(100L)
        } while (SystemClock.uptimeMillis() < deadline)
        return null
    }

    private fun waitForCameraTarget(
        scenario: ActivityScenario<RouteDetailActivity>,
        latitude: Double,
        longitude: Double
    ) {
        waitUntil("camera target at injected emulator location", 10_000L) {
            var matches = false
            scenario.onActivity { activity ->
                val actualLatitude = readNullableDouble(activity, "cameraLatitude")
                val actualLongitude = readNullableDouble(activity, "cameraLongitude")
                matches = actualLatitude != null && actualLongitude != null &&
                    abs(actualLatitude - latitude) < 0.02 && abs(actualLongitude - longitude) < 0.02
            }
            matches
        }
    }

    private fun readNullableDouble(activity: RouteDetailActivity, name: String): Double? =
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity) as? Double

    private fun hasForegroundLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun saveScreenshot(scenario: ActivityScenario<RouteDetailActivity>, name: String) {
        scenario.onActivity { activity ->
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            val output = File(requireNotNull(activity.getExternalFilesDir(null)), name)
            FileOutputStream(output).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun waitUntil(description: String, timeoutMillis: Long = 8_000L, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100L)
        }
        assertTrue("Timed out waiting for $description", condition())
    }

    private fun executeShell(command: String) {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        descriptor.use { FileInputStream(it.fileDescriptor).use(FileInputStream::readBytes) }
    }

    private fun assumeDeviceValidationEnabled() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString(ARG_RUN_LOCATION_PERMISSION) == "true"
        )
    }

    private companion object {
        const val ARG_RUN_LOCATION_PERMISSION = "runRouteDetailLocationPermission"
        const val ALLOW_BUTTON_ID =
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button"
        const val DENY_BUTTON_ID = "com.android.permissioncontroller:id/permission_deny_button"
        const val DENY_AND_DONT_ASK_AGAIN_BUTTON_ID =
            "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button"
        const val DENY_DONT_ASK_AGAIN_BUTTON_ID =
            "com.android.permissioncontroller:id/permission_deny_dont_ask_again_button"
        const val EXPECTED_LATITUDE = 22.3193
        const val EXPECTED_LONGITUDE = 114.1694
    }
}
