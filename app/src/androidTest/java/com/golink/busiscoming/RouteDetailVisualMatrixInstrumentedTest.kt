package com.golink.busiscoming

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.P2pRouteRecoveryContext
import com.golink.busiscoming.data.model.PedestrianRoute
import com.golink.busiscoming.data.model.PedestrianRoutePath
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.repository.CsdiPedestrianRequest
import com.golink.busiscoming.data.repository.CsdiPedestrianResponse
import com.golink.busiscoming.data.repository.CitybusP2pStopMapResolver
import com.golink.busiscoming.data.repository.PedestrianRequestPriority
import com.golink.busiscoming.data.repository.PedestrianRequestTrigger
import com.golink.busiscoming.data.repository.PedestrianRouteRequestRuntime
import com.golink.busiscoming.data.repository.PedestrianSubscription
import com.golink.busiscoming.data.repository.RouteDetailRepository
import com.golink.busiscoming.data.repository.RouteGeometryDataSource
import com.golink.busiscoming.data.repository.RouteGeometryLoadHandle
import com.golink.busiscoming.data.repository.RouteGeometryRequest
import com.golink.busiscoming.data.model.RouteGeometrySegment
import com.golink.busiscoming.ui.main.RouteDetailActivity
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import com.golink.busiscoming.ui.main.RouteDetailRuntime
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteDetailVisualMatrixInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @After
    fun restoreEnvironment() {
        RouteDetailRuntime.reset()
        executeShell("settings put system font_scale 1.0")
        executeShell("wm size reset")
        executeShell("wm density reset")
        AppThemePreferenceStore(context).setMode(AppThemeMode.SYSTEM)
        AppCompatDelegate.setDefaultNightMode(AppThemeMode.SYSTEM.nightMode)
        AppLanguageRepository(context).setChoice(AppLanguageChoice.TRADITIONAL_CHINESE)
        executeShell("cmd locale set-app-locales ${context.packageName} --locales zh-Hant")
    }

    @Test
    fun routeDetailFitsThreeLanguagesAndBothThemesAtRequestedFontScale() {
        val fontScale = requireNotNull(
            InstrumentationRegistry.getArguments().getString(ARG_FONT_SCALE)
        ).toFloat()
        val detail = longContentDetail()
        RouteDetailRuntime.repositoryFactory = {
            object : RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail = detail
            }
        }
        RouteDetailRuntime.etaResolver = { DemoScreenshotFixtures.primaryRoute().waitTimeState }
        RouteDetailRuntime.stopMapResolverFactory = {
            CitybusP2pStopMapResolver(stopMapFetcher = { _, _ -> demoStopMapResponse() })
        }
        RouteDetailRuntime.pedestrianRuntime = immediatePedestrianRuntime()
        RouteDetailRuntime.geometryRepositoryFactory = {
            object : RouteGeometryDataSource {
                override fun loadGeometries(
                    requests: List<RouteGeometryRequest>,
                    onResult: (RouteGeometryRequest, Result<RouteGeometrySegment>) -> Unit
                ): RouteGeometryLoadHandle = RouteGeometryLoadHandle {}
            }
        }
        executeShell("wm size 1080x2400")
        executeShell("wm density 480")
        executeShell("settings put system font_scale $fontScale")

        val languages = listOf(
            AppLanguageChoice.TRADITIONAL_CHINESE,
            AppLanguageChoice.SIMPLIFIED_CHINESE,
            AppLanguageChoice.ENGLISH
        )
        val themes = listOf(AppThemeMode.LIGHT, AppThemeMode.DARK)
        languages.forEach { language ->
            themes.forEach { theme ->
                configure(language, theme)
                ActivityScenario.launch<RouteDetailActivity>(intent()).use { scenario ->
                    waitForDetail(scenario)
                    waitForCsdiAttribution(scenario)
                    waitForUi()
                    scenario.onActivity { activity ->
                        assertTouchTargets(activity)
                        assertMapControlsVisibleAndLegendAbsent(activity)
                        assertAttributionIsNotCovered(activity)
                        saveScreenshot(activity, screenshotName(language, theme, fontScale, "summary"))
                    }
                    scenario.onActivity { activity ->
                        BottomSheetBehavior.from(
                            activity.findViewById<MaterialCardView>(R.id.routeDetailSheet)
                        ).state = BottomSheetBehavior.STATE_EXPANDED
                    }
                    waitForFullDetent(scenario)
                    scenario.onActivity { activity ->
                        assertEquals(fontScale, activity.resources.configuration.fontScale, 0.01f)
                        assertEquals(360, activity.resources.displayMetrics.widthPixels * 160 / activity.resources.displayMetrics.densityDpi)
                        val expectedNightMode = if (theme == AppThemeMode.DARK) {
                            Configuration.UI_MODE_NIGHT_YES
                        } else {
                            Configuration.UI_MODE_NIGHT_NO
                        }
                        assertEquals(
                            expectedNightMode,
                            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        )
                        assertTrue(removedTitle(language) !in collectText(activity.window.decorView))
                        assertEquals(
                            View.GONE,
                            activity.findViewById<View>(R.id.routeDetailFloatingBack).visibility
                        )
                        assertTrue(activity.findViewById<View>(R.id.routeDetailSheetContent).paddingTop > 0)
                        activity.findViewById<RecyclerView>(R.id.routeDetailList).scrollToPosition(4)
                    }
                    waitForUi()
                    scenario.onActivity { activity ->
                        val visibleText = collectText(activity.window.decorView)
                        assertTrue(visibleText.any { it.contains(LONG_DIRECTION) })
                        assertNoEllipsis(activity.window.decorView)
                        toggleLeg(activity, 0)
                        toggleLeg(activity, 1)
                    }
                    waitForUi()
                    scenario.onActivity { activity ->
                        val list = activity.findViewById<RecyclerView>(R.id.routeDetailList)
                        list.scrollToPosition(requireNotNull(list.adapter).itemCount - 1)
                    }
                    waitForUi()
                    scenario.onActivity { activity ->
                        assertTrue(collectText(activity.window.decorView).any { it.contains(LONG_DESTINATION) })
                        assertNoEllipsis(activity.window.decorView)
                        assertTouchTargets(activity)
                        saveScreenshot(activity, screenshotName(language, theme, fontScale, "full"))
                    }
                }
            }
        }
    }

    private fun configure(language: AppLanguageChoice, theme: AppThemeMode) {
        AppThemePreferenceStore(context).setMode(theme)
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        AppLanguageRepository(context).setChoice(language)
        executeShell(
            "cmd locale set-app-locales ${context.packageName} --locales ${language.localeTag()}"
        )
    }

    private fun waitForFullDetent(scenario: ActivityScenario<RouteDetailActivity>) {
        waitUntil {
            var expanded = false
            scenario.onActivity { activity ->
                expanded = BottomSheetBehavior.from(
                    activity.findViewById<MaterialCardView>(R.id.routeDetailSheet)
                ).state == BottomSheetBehavior.STATE_EXPANDED &&
                    activity.findViewById<View>(R.id.routeDetailFloatingBack).visibility == View.GONE
            }
            expanded
        }
    }

    private fun waitForDetail(scenario: ActivityScenario<RouteDetailActivity>) {
        waitUntil {
            val count = AtomicInteger()
            scenario.onActivity { activity ->
                count.set(activity.findViewById<RecyclerView>(R.id.routeDetailList).adapter?.itemCount ?: 0)
            }
            count.get() > 2
        }
    }

    private fun intent(): Intent = Intent(context, RouteDetailActivity::class.java).putExtras(
        RouteDetailLaunchArgs.fromRoute(
            DemoScreenshotFixtures.primaryRoute().let { route ->
                route.copy(
                    routeDetailQuery = route.routeDetailQuery?.copy(
                        recoveryContext = P2pRouteRecoveryContext(
                            originLatitude = 22.2798,
                            originLongitude = 114.1798,
                            destinationLatitude = 22.2806,
                            destinationLongitude = 114.1806,
                            searchMode = "T"
                        )
                    )
                )
            }
        ).toBundle()
    )

    private fun immediatePedestrianRuntime(): PedestrianRouteRequestRuntime =
        object : PedestrianRouteRequestRuntime {
            override fun subscribe(
                request: CsdiPedestrianRequest,
                priority: PedestrianRequestPriority,
                trigger: PedestrianRequestTrigger,
                callback: (CsdiPedestrianResponse) -> Unit
            ): PedestrianSubscription {
                callback(
                    CsdiPedestrianResponse.Success(
                        PedestrianRoute(
                            rawDistanceMeters = 88.2,
                            rawTimeMinutes = 1.47,
                            paths = listOf(PedestrianRoutePath(listOf(request.start, request.end)))
                        )
                    )
                )
                return PedestrianSubscription {}
            }
        }

    private fun demoStopMapResponse(): String = """
        <iframe onload="
            addstoponmap('28X-DEMO-1-1',114.1801,22.2801,'S','1','1 - 海庭苑平台','28X-DEMO-1','O','N','114.1801','22.2801');
            addstoponmap('28X-DEMO-1-4',114.1804,22.2804,'E','4','4 - 景澄站北','28X-DEMO-1','O','N','114.1804','22.2804');
            addstoponmap('86-DEMO-1-1',114.1801,22.2801,'S','1','1 - 景澄站南','86-DEMO-1','O','N','114.1801','22.2801');
            addstoponmap('86-DEMO-1-4',114.1804,22.2804,'E','4','4 - 松嶺邨總站','86-DEMO-1','O','N','114.1804','22.2804');
        "></iframe>
    """.trimIndent()

    private fun longContentDetail(): RouteDetail {
        val original = DemoScreenshotFixtures.routeDetail()
        val firstLeg = original.legs.first()
        return original.copy(
            destinationName = LONG_DESTINATION,
            legs = original.legs.toMutableList().apply {
                this[0] = firstLeg.copy(
                    directionText = LONG_DIRECTION,
                    boardingStop = firstLeg.boardingStop.copy(
                        rawName = LONG_BOARDING_STOP,
                        displayName = LONG_BOARDING_STOP
                    )
                )
            }
        )
    }

    private fun toggleLeg(activity: RouteDetailActivity, index: Int) {
        activity.javaClass.getDeclaredMethod("toggleLeg", Int::class.javaPrimitiveType).apply {
            isAccessible = true
        }.invoke(activity, index)
    }

    private fun assertNoEllipsis(view: View) {
        if (view is TextView && view.layout != null) {
            for (line in 0 until view.layout.lineCount) {
                assertEquals("Unexpected ellipsis in '${view.text}'", 0, view.layout.getEllipsisCount(line))
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) assertNoEllipsis(view.getChildAt(index))
        }
    }

    private fun assertTouchTargets(activity: RouteDetailActivity) {
        val minimum = (48f * activity.resources.displayMetrics.density).toInt()
        listOf(
            R.id.routeDetailFloatingBack,
            R.id.routeDetailSheetHandle,
            R.id.routeDetailLocation,
            R.id.routeDetailOverview
        ).forEach { id ->
            val view = activity.findViewById<View>(id)
            if (view.visibility == View.VISIBLE) {
                assertTrue("Touch target width is under 48dp for id=$id", view.width >= minimum)
                assertTrue("Touch target height is under 48dp for id=$id", view.height >= minimum)
            }
        }
    }

    private fun assertMapControlsVisibleAndLegendAbsent(activity: RouteDetailActivity) {
        assertEquals(
            "Map legend resource must be removed",
            0,
            activity.resources.getIdentifier("routeDetailMapLegend", "id", activity.packageName)
        )
        listOf(R.id.routeDetailLocation, R.id.routeDetailOverview).forEach { id ->
            val controlBounds = Rect()
            val control = activity.findViewById<View>(id)
            assertTrue("Map control must be visible for id=$id", control.getGlobalVisibleRect(controlBounds))
        }
    }

    private fun assertAttributionIsNotCovered(activity: RouteDetailActivity) {
        val watermark = findViewWithTag(activity.window.decorView, "GoogleWatermark")
        assertTrue("Google watermark must be visible", watermark?.visibility == View.VISIBLE)
        val sheet = activity.findViewById<MaterialCardView>(R.id.routeDetailSheet)
        val location = IntArray(2)
        requireNotNull(watermark).getLocationOnScreen(location)
        assertTrue("Google watermark must remain above the summary sheet", location[1] + watermark.height <= sheet.top)
        val csdi = activity.findViewById<View>(R.id.routeDetailCsdiAttribution)
        assertTrue("CSDI attribution must be visible with rendered walking paths", csdi.isShown)
        val csdiBounds = Rect()
        val watermarkBounds = Rect()
        val sheetBounds = Rect()
        assertTrue(csdi.getGlobalVisibleRect(csdiBounds))
        assertTrue(watermark.getGlobalVisibleRect(watermarkBounds))
        assertTrue(sheet.getGlobalVisibleRect(sheetBounds))
        assertTrue(!Rect.intersects(csdiBounds, watermarkBounds))
        assertTrue(!Rect.intersects(csdiBounds, sheetBounds))
    }

    private fun waitForCsdiAttribution(scenario: ActivityScenario<RouteDetailActivity>) {
        waitUntil {
            var visible = false
            scenario.onActivity { activity ->
                visible = activity.findViewById<View>(R.id.routeDetailCsdiAttribution).isShown
            }
            visible
        }
    }

    private fun findViewWithTag(root: View, tag: String): View? {
        if (root.tag?.toString() == tag) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) findViewWithTag(root.getChildAt(index), tag)?.let { return it }
        return null
    }

    private fun saveScreenshot(activity: RouteDetailActivity, name: String) {
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val output = File(requireNotNull(activity.getExternalFilesDir(null)), name)
        FileOutputStream(output).use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun screenshotName(
        language: AppLanguageChoice,
        theme: AppThemeMode,
        fontScale: Float,
        state: String
    ): String = "route-detail-${language.name.lowercase()}-${theme.name.lowercase()}-$fontScale-$state.png"

    private fun collectText(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { collectText(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun waitForUi() {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300L)
        instrumentation.waitForIdleSync()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(100L)
        }
        assertTrue("Timed out waiting for route detail", condition())
    }

    private fun executeShell(command: String) {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        descriptor.use { FileInputStream(it.fileDescriptor).use(FileInputStream::readBytes) }
    }

    private fun removedTitle(language: AppLanguageChoice): String = when (language) {
        AppLanguageChoice.TRADITIONAL_CHINESE -> "路線詳情"
        AppLanguageChoice.SIMPLIFIED_CHINESE -> "路线详情"
        AppLanguageChoice.ENGLISH -> "Route details"
        AppLanguageChoice.FOLLOW_SYSTEM -> error("Not part of the explicit language matrix")
    }

    private fun AppLanguageChoice.localeTag(): String = when (this) {
        AppLanguageChoice.TRADITIONAL_CHINESE -> "zh-Hant"
        AppLanguageChoice.SIMPLIFIED_CHINESE -> "zh-Hans"
        AppLanguageChoice.ENGLISH -> "en"
        AppLanguageChoice.FOLLOW_SYSTEM -> ""
    }

    private companion object {
        const val ARG_FONT_SCALE = "fontScale"
        const val LONG_DIRECTION = "香港海岸公園及景澄站海濱長廊方向"
        const val LONG_BOARDING_STOP = "海庭苑公共運輸交匯處臨時上落客區"
        const val LONG_DESTINATION = "松嶺邨公共運輸交匯處臨時總站"
    }
}
