package com.golink.busiscoming

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.golink.busiscoming.data.location.ForegroundLocationSource
import com.golink.busiscoming.data.location.ForegroundLocationSubscription
import com.golink.busiscoming.data.location.JourneyLocationFix
import com.golink.busiscoming.data.location.RouteDetailLocationUiState
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailTransfer
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteGeometryPoint
import com.golink.busiscoming.data.model.RouteGeometrySegment
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.RouteDetailRepository
import com.golink.busiscoming.data.repository.RouteGeometryDataSource
import com.golink.busiscoming.data.repository.RouteGeometryLoadHandle
import com.golink.busiscoming.data.repository.RouteGeometryRequest
import com.golink.busiscoming.ui.main.RouteDetailActivity
import com.golink.busiscoming.ui.main.RouteDetailAdapter
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import com.golink.busiscoming.ui.main.RouteDetailRuntime
import com.golink.busiscoming.ui.main.RouteDetailUiItem
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteDetailCurrentPositionInstrumentedTest {
    @get:Rule
    val locationPermission: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val source = FakeForegroundLocationSource()
    private val states = CopyOnWriteArrayList<RouteDetailLocationUiState>()

    @Before
    fun setUp() {
        val arguments = InstrumentationRegistry.getArguments()
        arguments.getString(ARG_LANGUAGE)?.let { value ->
            val (choice, localeTag) = when (value) {
                    "traditional" -> AppLanguageChoice.TRADITIONAL_CHINESE to "zh-Hant-HK"
                    "simplified" -> AppLanguageChoice.SIMPLIFIED_CHINESE to "zh-Hans-CN"
                    "english" -> AppLanguageChoice.ENGLISH to "en"
                    else -> error("Unknown position test language: $value")
                }
            AppLanguageRepository(context).setChoice(choice)
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "cmd locale set-app-locales ${context.packageName} --locales $localeTag"
            ).close()
        }
        arguments.getString(ARG_THEME)?.let { value ->
            val theme = when (value) {
                "light" -> AppThemeMode.LIGHT
                "dark" -> AppThemeMode.DARK
                else -> error("Unknown position test theme: $value")
            }
            AppThemePreferenceStore(context).setMode(theme)
            AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        }
        RouteDetailRuntime.mapsAvailabilityChecker = { true }
        RouteDetailRuntime.systemLocationEnabled = { true }
        RouteDetailRuntime.foregroundLocationSourceFactory = { source }
        RouteDetailRuntime.locationStateObserver = states::add
        RouteDetailRuntime.repositoryFactory = {
            object : RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail = detail()
            }
        }
        RouteDetailRuntime.geometryRepositoryFactory = {
            object : RouteGeometryDataSource {
                override fun loadGeometries(
                    requests: List<RouteGeometryRequest>,
                    onResult: (RouteGeometryRequest, Result<RouteGeometrySegment>) -> Unit
                ): RouteGeometryLoadHandle {
                    requests.forEach { request ->
                        val startOffset = if (request.key.routeVariant == VARIANT) 0.0 else 0.002
                        onResult(
                            request,
                            Result.success(
                                RouteGeometrySegment(
                                    request.key,
                                    listOf(
                                        RouteGeometryPoint("p0", BASE_LATITUDE, BASE_LONGITUDE + startOffset),
                                        RouteGeometryPoint("p1", BASE_LATITUDE, BASE_LONGITUDE + startOffset + 0.001),
                                        RouteGeometryPoint("p2", BASE_LATITUDE, BASE_LONGITUDE + startOffset + 0.002)
                                    )
                                )
                            )
                        )
                    }
                    return RouteGeometryLoadHandle {}
                }
            }
        }
    }

    @After
    fun tearDown() {
        RouteDetailRuntime.reset()
        AppLanguageRepository(context).setChoice(AppLanguageChoice.TRADITIONAL_CHINESE)
        AppThemePreferenceStore(context).setMode(AppThemeMode.SYSTEM)
        AppCompatDelegate.setDefaultNightMode(AppThemeMode.SYSTEM.nightMode)
    }

    @Test
    fun firstForegroundFixShowsSummaryPinAndAutoExpandsMatchedLeg() {
        ActivityScenario.launch<RouteDetailActivity>(intent()).use { scenario ->
            waitUntil("location subscription") { source.subscriptions.isNotEmpty() }
            waitUntil("route detail") { adapterItems(scenario).size > 5 }

            scenario.onActivity {
                source.subscriptions.last().emit(
                    JourneyLocationFix(
                        BASE_LATITUDE,
                        BASE_LONGITUDE + 0.001,
                        8f,
                        SystemClock.elapsedRealtime()
                    )
                )
            }

            waitUntil("visible matched position") {
                states.any { it is RouteDetailLocationUiState.Visible }
            }
            waitUntil("automatically expanded via stops") {
                adapterItems(scenario).filterIsInstance<RouteDetailUiItem.ViaToggle>()
                    .singleOrNull()
                    ?.expanded == true
            }
            try {
                waitUntil("summary pin layout") {
                    var visible = false
                    scenario.onActivity { activity ->
                        visible = activity.findViewById<ImageView?>(R.id.routeCurrentPositionSummaryPin)
                            ?.getGlobalVisibleRect(Rect()) == true
                    }
                    visible
                }
            } catch (failure: AssertionError) {
                throw AssertionError("${failure.message}\n${pinLayoutDiagnostic(scenario)}", failure)
            }
            onView(withId(R.id.routeCurrentPositionSummaryPin)).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val pin = activity.findViewById<ImageView>(R.id.routeCurrentPositionSummaryPin)
                val density = activity.resources.displayMetrics.density
                assertEquals((18f * density).toInt(), pin.width)
                assertEquals((22f * density).toInt(), pin.height)
                assertEquals((18f * density).toInt(), pin.drawable.intrinsicWidth)
                assertEquals((22f * density).toInt(), pin.drawable.intrinsicHeight)
                assertTrue(!pin.isClickable)
                assertTrue(!pin.isFocusable)
                assertEquals(
                    android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                    pin.importantForAccessibility
                )
                assertEquals(
                    android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                    activity.findViewById<android.view.View>(R.id.routeDetailPositionOverlay)
                        .importantForAccessibility
                )
                assertConfiguredMatrix(activity)
            }
        }
    }

    @Test
    fun backgroundClosesSubscriptionAndRecreateStartsNewGeneration() {
        ActivityScenario.launch<RouteDetailActivity>(intent()).use { scenario ->
            waitUntil("first location subscription") { source.subscriptions.size == 1 }
            val first = source.subscriptions.first()

            scenario.moveToState(Lifecycle.State.CREATED)
            waitUntil("background subscription close") { first.closed }

            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil("second location subscription") { source.subscriptions.size == 2 }
            scenario.recreate()
            waitUntil("old subscription closed after recreate") {
                source.subscriptions.dropLast(1).all(FakeSubscription::closed)
            }
            waitUntil("recreated location subscription") { source.subscriptions.size >= 3 }
            assertTrue(source.subscriptions.last().closed.not())
        }
    }

    @Test
    fun systemLocationOffShowsRecoveryWithoutStartingSource() {
        RouteDetailRuntime.systemLocationEnabled = { false }

        ActivityScenario.launch<RouteDetailActivity>(intent()).use {
            onView(withText(R.string.route_current_position_system_location_off))
                .check(matches(isDisplayed()))
            assertTrue(source.subscriptions.isEmpty())
        }
    }

    @Test
    fun directRouteAcceptsAdjacentReverseMovementAndHidesUnreliableFix() {
        ActivityScenario.launch<RouteDetailActivity>(intent()).use { scenario ->
            waitUntil("location subscription") { source.subscriptions.isNotEmpty() }
            waitUntil("route detail") { adapterItems(scenario).size > 5 }

            emit(scenario, 0.001)
            waitUntil("via node") {
                (states.lastOrNull() as? RouteDetailLocationUiState.Visible)
                    ?.position is com.golink.busiscoming.data.model.JourneyPosition.AtNode
            }
            emit(scenario, 0.0005)
            waitUntil("first bus edge") {
                (states.lastOrNull() as? RouteDetailLocationUiState.Visible)
                    ?.position is com.golink.busiscoming.data.model.JourneyPosition.BetweenNodes
            }
            emit(scenario, 0.001)
            emit(scenario, 0.0015)
            emit(scenario, 0.0005)
            waitUntil("reverse adjacent edge") {
                ((states.lastOrNull() as? RouteDetailLocationUiState.Visible)
                    ?.position as? com.golink.busiscoming.data.model.JourneyPosition.BetweenNodes)
                    ?.edgeId == "bus:0:0"
            }
            emit(scenario, 0.0005, accuracy = 100f)
            waitUntil("unreliable fix hidden") {
                states.lastOrNull() === RouteDetailLocationUiState.Hidden
            }
        }
    }

    @Test
    fun transferRouteAutoExpandsOnlyTheMatchedSecondLeg() {
        RouteDetailRuntime.repositoryFactory = {
            object : RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail = transferDetail()
            }
        }

        ActivityScenario.launch<RouteDetailActivity>(transferIntent()).use { scenario ->
            waitUntil("transfer location subscription") { source.subscriptions.isNotEmpty() }
            waitUntil("transfer route detail") {
                adapterItems(scenario).filterIsInstance<RouteDetailUiItem.ViaToggle>().size == 2
            }
            emit(scenario, 0.003)
            waitUntil("second leg expansion") {
                val toggles = adapterItems(scenario).filterIsInstance<RouteDetailUiItem.ViaToggle>()
                toggles.size == 2 && !toggles[0].expanded && toggles[1].expanded
            }
        }
    }

    private fun emit(
        scenario: ActivityScenario<RouteDetailActivity>,
        longitudeOffset: Double,
        accuracy: Float = 8f
    ) {
        scenario.onActivity {
            source.subscriptions.last().emit(
                JourneyLocationFix(
                    BASE_LATITUDE,
                    BASE_LONGITUDE + longitudeOffset,
                    accuracy,
                    SystemClock.elapsedRealtime()
                )
            )
        }
    }

    private fun adapterItems(
        scenario: ActivityScenario<RouteDetailActivity>
    ): List<RouteDetailUiItem> {
        var items: List<RouteDetailUiItem> = emptyList()
        scenario.onActivity { activity ->
            items = (activity.findViewById<RecyclerView>(R.id.routeDetailList).adapter
                as RouteDetailAdapter).currentList
        }
        return items
    }

    private fun intent(): Intent = Intent(context, RouteDetailActivity::class.java).putExtras(
        RouteDetailLaunchArgs.fromRoute(
            route(),
            Place("測試起點", BASE_LATITUDE, BASE_LONGITUDE - 0.001),
            Place("測試終點", BASE_LATITUDE, BASE_LONGITUDE + 0.003)
        ).toBundle()
    )

    private fun transferIntent(): Intent = Intent(context, RouteDetailActivity::class.java).putExtras(
        RouteDetailLaunchArgs.fromRoute(
            transferRoute(),
            Place("測試起點", BASE_LATITUDE, BASE_LONGITUDE - 0.001),
            Place("測試終點", BASE_LATITUDE, BASE_LONGITUDE + 0.005)
        ).toBundle()
    )

    private fun route(): BusRouteOption {
        val query = P2pRouteDetailQuery(
            rawInfo = "current-position-test",
            generalInfo = "12:30",
            listId = "1",
            lang = "1",
            plan = P2pRoutePlan(
                legs = listOf(P2pRouteLeg("CTB", VARIANT, "R1", 1, 3, "O", null))
            )
        )
        return BusRouteOption(
            routeName = "R1",
            routeSegments = listOf("R1"),
            priceHkd = 8.0,
            durationMinutes = 20,
            arrivalMinutes = 6,
            transferCount = 0,
            walkingDistanceMeters = 100,
            waitTimeState = WaitTimeState.Available(6),
            routeDetailQuery = query
        )
    }

    private fun transferRoute(): BusRouteOption {
        val rawInfo = "transfer-current-position-test"
        return route().copy(
            routeName = "R1 → R2",
            routeSegments = listOf("R1", "R2"),
            transferCount = 1,
            routeDetailQuery = P2pRouteDetailQuery(
                rawInfo = rawInfo,
                generalInfo = "12:30",
                listId = "1",
                lang = "1",
                plan = P2pRoutePlan(
                    legs = listOf(
                        P2pRouteLeg("CTB", VARIANT, "R1", 1, 3, "O", null),
                        P2pRouteLeg("CTB", SECOND_VARIANT, "R2", 10, 12, "O", null)
                    )
                )
            )
        )
    }

    private fun detail(): RouteDetail {
        fun stop(name: String, sequence: Int, offset: Double, role: RouteDetailStopRole) =
            RouteDetailStop(
                rawName = name,
                displayName = name,
                stopId = "$VARIANT-$sequence",
                sequence = sequence,
                latitude = BASE_LATITUDE,
                longitude = BASE_LONGITUDE + offset,
                routeVariant = VARIANT,
                role = role
            )
        return RouteDetail(
            routeName = "R1",
            priceHkd = 8.0,
            durationMinutes = 20,
            walkingDistanceMeters = 100,
            legs = listOf(
                RouteDetailLeg(
                    route = "R1",
                    routeVariant = VARIANT,
                    directionText = "測試方向",
                    boardingStop = stop("甲站", 1, 0.0, RouteDetailStopRole.BOARDING),
                    viaStops = listOf(stop("乙站", 2, 0.001, RouteDetailStopRole.VIA)),
                    alightingStop = stop("丙站", 3, 0.002, RouteDetailStopRole.ALIGHTING)
                )
            ),
            originName = "測試起點",
            destinationName = "測試終點"
        )
    }

    private fun transferDetail(): RouteDetail {
        fun stop(
            variant: String,
            name: String,
            sequence: Int,
            offset: Double,
            role: RouteDetailStopRole
        ) = RouteDetailStop(
            rawName = name,
            displayName = name,
            stopId = "$variant-$sequence",
            sequence = sequence,
            latitude = BASE_LATITUDE,
            longitude = BASE_LONGITUDE + offset,
            routeVariant = variant,
            role = role
        )
        val first = detail().legs.single()
        val second = RouteDetailLeg(
            route = "R2",
            routeVariant = SECOND_VARIANT,
            directionText = "第二段方向",
            boardingStop = stop(
                SECOND_VARIANT,
                "丙站",
                10,
                0.002,
                RouteDetailStopRole.BOARDING
            ),
            viaStops = listOf(
                stop(SECOND_VARIANT, "丁站", 11, 0.003, RouteDetailStopRole.VIA)
            ),
            alightingStop = stop(
                SECOND_VARIANT,
                "戊站",
                12,
                0.004,
                RouteDetailStopRole.ALIGHTING
            )
        )
        return detail().copy(
            routeName = "R1 → R2",
            legs = listOf(first, second),
            transfers = listOf(RouteDetailTransfer(RouteDetailTransferType.SAME_STOP))
        )
    }

    private fun waitUntil(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(50L)
        }
        assertTrue("Timed out waiting for $description", condition())
    }

    private fun pinLayoutDiagnostic(
        scenario: ActivityScenario<RouteDetailActivity>
    ): String {
        var diagnostic = "pin not found"
        scenario.onActivity { activity ->
            var view: android.view.View? =
                activity.findViewById(R.id.routeCurrentPositionSummaryPin)
            val lines = mutableListOf<String>()
            while (view != null) {
                val rect = Rect()
                lines += "${view.javaClass.simpleName} id=${view.id} " +
                    "xy=${view.x},${view.y} size=${view.width}x${view.height} " +
                    "shown=${view.isShown} global=${view.getGlobalVisibleRect(rect)}:$rect"
                view = view.parent as? android.view.View
            }
            diagnostic = lines.joinToString("\n")
        }
        return diagnostic
    }

    private fun assertConfiguredMatrix(activity: RouteDetailActivity) {
        when (InstrumentationRegistry.getArguments().getString(ARG_LANGUAGE)) {
            "traditional" -> assertEquals("Hant", activity.resources.configuration.locales[0].script)
            "simplified" -> assertEquals("Hans", activity.resources.configuration.locales[0].script)
            "english" -> assertEquals("en", activity.resources.configuration.locales[0].language)
            null -> Unit
        }
        val expectedNightMode = when (
            InstrumentationRegistry.getArguments().getString(ARG_THEME)
        ) {
            "light" -> Configuration.UI_MODE_NIGHT_NO
            "dark" -> Configuration.UI_MODE_NIGHT_YES
            null -> return
            else -> error("Unknown position test theme")
        }
        assertEquals(
            expectedNightMode,
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        )
    }

    private class FakeForegroundLocationSource : ForegroundLocationSource {
        val subscriptions = CopyOnWriteArrayList<FakeSubscription>()

        override fun start(onLocation: (JourneyLocationFix) -> Unit): ForegroundLocationSubscription =
            FakeSubscription(onLocation).also(subscriptions::add)
    }

    private class FakeSubscription(
        private val onLocation: (JourneyLocationFix) -> Unit
    ) : ForegroundLocationSubscription {
        @Volatile var closed = false

        fun emit(fix: JourneyLocationFix) {
            if (!closed) onLocation(fix)
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val BASE_LATITUDE = 22.3000
        const val BASE_LONGITUDE = 114.1700
        const val VARIANT = "R1-A"
        const val SECOND_VARIANT = "R2-A"
        const val ARG_LANGUAGE = "positionLanguage"
        const val ARG_THEME = "positionTheme"
    }
}
