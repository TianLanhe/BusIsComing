package com.golink.busiscoming

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.location.ForegroundLocationSource
import com.golink.busiscoming.data.location.ForegroundLocationSubscription
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.repository.RouteDetailRepository
import com.golink.busiscoming.ui.main.RouteDetailActivity
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import com.golink.busiscoming.ui.main.RouteDetailRuntime
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteDetailCurrentPositionPermissionInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sourceStarts = AtomicInteger()

    @Before
    fun setUp() {
        assumeTrue(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
        )
        RouteDetailRuntime.mapsAvailabilityChecker = { true }
        RouteDetailRuntime.systemLocationEnabled = { true }
        RouteDetailRuntime.foregroundLocationSourceFactory = {
            ForegroundLocationSource {
                sourceStarts.incrementAndGet()
                ForegroundLocationSubscription {}
            }
        }
        RouteDetailRuntime.repositoryFactory = {
            object : RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption) =
                    DemoScreenshotFixtures.routeDetail()
            }
        }
    }

    @After
    fun tearDown() {
        RouteDetailRuntime.reset()
    }

    @Test
    fun firstOpenShowsPermissionSnackbarWithoutAutomaticSystemDialog() {
        val route = DemoScreenshotFixtures.primaryRoute()
        val intent = Intent(context, RouteDetailActivity::class.java).putExtras(
            RouteDetailLaunchArgs.fromRoute(route).toBundle()
        )

        ActivityScenario.launch<RouteDetailActivity>(intent).use {
            onView(withText(R.string.route_current_position_permission))
                .check(matches(isDisplayed()))
            assertEquals(0, sourceStarts.get())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(
                InstrumentationRegistry.getInstrumentation().uiAutomation.windows.none { window ->
                    window.root?.packageName?.toString() == PERMISSION_CONTROLLER_PACKAGE
                }
            )
        }
    }

    private companion object {
        const val PERMISSION_CONTROLLER_PACKAGE = "com.android.permissioncontroller"
    }
}
