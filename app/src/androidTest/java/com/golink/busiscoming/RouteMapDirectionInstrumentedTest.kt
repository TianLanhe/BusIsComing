package com.golink.busiscoming

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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
import com.golink.busiscoming.ui.main.GoogleRouteMapRenderer
import com.golink.busiscoming.ui.main.RouteDetailActivity
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import com.golink.busiscoming.ui.main.RouteDetailRuntime
import com.golink.busiscoming.ui.main.RouteMapCoordinate
import com.golink.busiscoming.ui.main.RouteMapLine
import com.golink.busiscoming.ui.main.RouteMapLineKind
import com.golink.busiscoming.ui.main.RouteMapMarker
import com.golink.busiscoming.ui.main.RouteMapMarkerIconFactory
import com.golink.busiscoming.ui.main.RouteMapMarkerRole
import com.golink.busiscoming.ui.main.RouteMapPresentation
import com.golink.busiscoming.ui.main.RouteMapRenderPalette
import com.google.android.gms.maps.MapsInitializer
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteMapDirectionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

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
    }

    @After
    fun resetRuntime() = RouteDetailRuntime.reset()

    @Test
    fun productionRendererKeepsBusAndWalkingChevronsOnFixedDirectionShapes() {
        assumeTrue(InstrumentationRegistry.getArguments().getString(ARG_RUN_DIRECTION_SPIKE) == "true")
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            RouteDetailActivity::class.java
        ).putExtras(
            RouteDetailLaunchArgs.fromRoute(DemoScreenshotFixtures.primaryRoute()).toBundle()
        )

        ActivityScenario.launch<RouteDetailActivity>(intent).use { scenario ->
            val renderer = waitForRenderer(scenario)
            renderAndCapture(scenario, renderer, busPresentation(), BUS_SCREENSHOT)
            renderAndCapture(scenario, renderer, walkingPresentation(), WALKING_SCREENSHOT)
            assertTrue(renderer.hasRenderedWalkingPaths())
            if (InstrumentationRegistry.getArguments().getString(ARG_HOLD_FOR_INSPECTION) == "true") {
                SystemClock.sleep(30_000L)
            }
        }
    }

    @Test
    fun markerIconCacheReusesVisualKeysAndInvalidatesSelectedOrColorState() {
        assumeTrue(InstrumentationRegistry.getArguments().getString(ARG_RUN_DIRECTION_SPIKE) == "true")
        val context = instrumentation.targetContext
        MapsInitializer.initialize(context)
        val factory = RouteMapMarkerIconFactory(context, RouteMapRenderPalette.from(context))
        val base = RouteMapMarker(
            stableId = "boarding:1",
            title = "Boarding",
            position = point(22.28, 114.15),
            role = RouteMapMarkerRole.BOARDING,
            legIndexes = setOf(0)
        )

        assertSame(factory.icon(base), factory.icon(base.copy(stableId = "boarding:2")))
        assertNotSame(factory.icon(base), factory.icon(base.copy(selected = true)))
        assertNotSame(factory.icon(base), factory.icon(base.copy(legIndexes = setOf(1))))

        val transfer = base.copy(role = RouteMapMarkerRole.TRANSFER, legIndexes = setOf(0, 1))
        assertSame(factory.icon(transfer), factory.icon(transfer.copy(legIndexes = setOf(1, 0))))
        assertNotSame(factory.icon(transfer), factory.icon(transfer.copy(legIndexes = setOf(1, 2))))
    }

    private fun renderAndCapture(
        scenario: ActivityScenario<RouteDetailActivity>,
        renderer: GoogleRouteMapRenderer,
        presentation: RouteMapPresentation,
        screenshotName: String
    ) {
        scenario.onActivity {
            renderer.render(presentation)
            assertTrue(renderer.fitOverview(animated = false, paddingPx = dp(it, 48f)))
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(1_500L)
        scenario.onActivity { activity ->
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            assertTrue(screenshot.width > 0 && screenshot.height > 0)
            val output = File(requireNotNull(activity.getExternalFilesDir(null)), screenshotName)
            FileOutputStream(output).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun waitForRenderer(
        scenario: ActivityScenario<RouteDetailActivity>
    ): GoogleRouteMapRenderer {
        var renderer: GoogleRouteMapRenderer? = null
        var baseMapLoaded = false
        val deadline = SystemClock.uptimeMillis() + 15_000L
        while (SystemClock.uptimeMillis() < deadline && (renderer == null || !baseMapLoaded)) {
            scenario.onActivity { activity ->
                renderer = activity.javaClass.getDeclaredField("renderer").apply {
                    isAccessible = true
                }.get(activity) as? GoogleRouteMapRenderer
                baseMapLoaded = activity.javaClass.getDeclaredField("baseMapLoaded").apply {
                    isAccessible = true
                }.getBoolean(activity)
            }
            if (renderer == null || !baseMapLoaded) SystemClock.sleep(100L)
        }
        assertTrue("Google base map did not load", baseMapLoaded)
        return requireNotNull(renderer)
    }

    private fun busPresentation(): RouteMapPresentation = presentation(RouteMapLineKind.BUS)

    private fun walkingPresentation(): RouteMapPresentation = presentation(RouteMapLineKind.WALKING)

    private fun presentation(kind: RouteMapLineKind): RouteMapPresentation {
        val shapes = listOf(
            listOf(point(22.2840, 114.1515), point(22.2840, 114.1585)),
            listOf(
                point(22.2817, 114.1515),
                point(22.2817, 114.1555),
                point(22.2797, 114.1555),
                point(22.2797, 114.1585)
            ),
            listOf(
                point(22.2782, 114.1515),
                point(22.2791, 114.1532),
                point(22.2773, 114.1550),
                point(22.2782, 114.1585)
            ),
            listOf(point(22.2755, 114.1585), point(22.2755, 114.1515))
        )
        val lines = shapes.mapIndexed { index, points ->
            RouteMapLine(
                stableId = "direction-spike:${kind.name.lowercase()}:$index",
                kind = kind,
                points = points,
                legIndex = index,
                colorSlot = index
            )
        }
        return RouteMapPresentation(
            markers = emptyList(),
            lines = lines,
            boundsPoints = shapes.flatten()
        )
    }

    private fun point(latitude: Double, longitude: Double) =
        RouteMapCoordinate(latitude, longitude)

    private fun dp(activity: RouteDetailActivity, value: Float): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val ARG_RUN_DIRECTION_SPIKE = "runRouteMapDirectionSpike"
        const val ARG_HOLD_FOR_INSPECTION = "holdRouteMapDirectionSpike"
        const val BUS_SCREENSHOT = "route-map-direction-spike-bus.png"
        const val WALKING_SCREENSHOT = "route-map-direction-spike-walking.png"
    }
}
