package com.golink.busiscoming

import android.Manifest
import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.location.CurrentLocationCoordinator
import com.golink.busiscoming.data.location.CurrentLocationSnapshot
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfig
import com.golink.busiscoming.data.repository.RouteConfigRepository
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.SavedRouteUsageSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class MainSavedRouteRankingInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @get:Rule
    val locationPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @Before
    fun setUp() {
        context.deleteDatabase("bus_is_coming.db")
        runShell("cmd location set-location-enabled true")
        insertRoute("最近起點", 22.2767)
    }

    @After
    fun tearDown() {
        context.deleteDatabase("bus_is_coming.db")
    }

    @Test
    fun coarseAndDelayedLocationsRankRoutesWithoutOverridingManualSelection() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                setField(activity, "hasAttemptedNearbyRouteSelection", true)
                val initialSelectedRouteId = selectedRoute(activity)?.id
                val middleId = insertRoute("中間起點", 22.2770)
                val farthestId = insertRoute("最遠起點", 22.2773)
                invokeNoArg(activity, "loadRouteConfigs")
                val routeIds = mapOf(
                    "中間起點" to middleId,
                    "最遠起點" to farthestId
                )
                val coordinator = getField<CurrentLocationCoordinator>(activity, "currentLocationCoordinator")
                coordinator.updateSnapshotForTests(
                    CurrentLocationSnapshot(
                        latitude = 22.2766,
                        longitude = 114.2395,
                        accuracyMeters = 2_000f,
                        elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime()
                    )
                )

                invokeNearbySelection(activity, generation = 0)

                assertEquals(
                    listOf("最近起點", "中間起點", "最遠起點"),
                    routeNames(activity)
                )
                assertEquals(initialSelectedRouteId, selectedRoute(activity)?.id)
                assertEquals(
                    "最近起點",
                    firstText(activity.findViewById(R.id.routeShortcutCardsContainer))
                )
                assertFalse(
                    allText(activity.findViewById(R.id.routeShortcutCardsContainer)).contains("使用次數")
                )

                invokeSelectRoute(activity, routeByName(activity, "中間起點"))
                coordinator.updateSnapshotForTests(
                    CurrentLocationSnapshot(
                        latitude = 22.2773,
                        longitude = 114.2395,
                        accuracyMeters = 30f,
                        elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime()
                    )
                )
                invokeNearbySelection(activity, generation = 0)

                assertEquals(routeIds.getValue("中間起點"), selectedRoute(activity)?.id)
                assertEquals("最遠起點", routeNames(activity).first())

                val longRouteName = "新增後仍依定位排序的超長常用路線名稱"
                insertRoute(longRouteName, 22.2773)
                invokeNoArg(activity, "loadRouteConfigs")

                assertEquals(longRouteName, routeNames(activity).first())
                assertEquals(longRouteName, firstText(activity.findViewById(R.id.routeShortcutCardsContainer)))
                assertEquals(routeIds.getValue("中間起點"), selectedRoute(activity)?.id)
            }
        }
    }

    @Test
    fun preciseLocationRanksAndAutomaticallySelectsNearestRoute() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                setField(activity, "hasAttemptedNearbyRouteSelection", true)
                insertRoute("中間起點", 22.2770)
                val farthestId = insertRoute("最遠起點", 22.2773)
                invokeNoArg(activity, "loadRouteConfigs")

                val coordinator = getField<CurrentLocationCoordinator>(activity, "currentLocationCoordinator")
                coordinator.updateSnapshotForTests(
                    CurrentLocationSnapshot(
                        latitude = 22.2773,
                        longitude = 114.2395,
                        accuracyMeters = 30f,
                        elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime()
                    )
                )
                invokeNearbySelection(activity, generation = 0)

                assertEquals("最遠起點", routeNames(activity).first())
                assertEquals(farthestId, selectedRoute(activity)?.id)
                assertEquals(farthestId, getField<Long?>(activity, "nearbySelectedRouteId"))
                assertEquals(
                    true,
                    allText(activity.findViewById(R.id.routeShortcutCardsContainer)).contains("附近")
                )
            }
        }
    }

    @Test
    fun disabledLocationKeepsRepositoryFallbackOrder() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                setField(activity, "hasAttemptedNearbyRouteSelection", true)
                insertRoute("中間起點", 22.2770)
                insertRoute("最遠起點", 22.2773)
                invokeNoArg(activity, "loadRouteConfigs")
            }

            runShell("cmd location set-location-enabled false")
            try {
                scenario.onActivity { activity ->
                    invokeNearbySelection(activity, generation = 0)

                    assertEquals(
                        listOf("最遠起點", "中間起點", "最近起點"),
                        routeNames(activity)
                    )
                    assertEquals(null, getField<CurrentLocationSnapshot?>(activity, "currentLocationSnapshot"))
                    assertEquals(null, getField<Long?>(activity, "nearbySelectedRouteId"))
                }
            } finally {
                runShell("cmd location set-location-enabled true")
            }
        }
    }

    @Test
    fun longRouteNamesRemainVisibleAtLargeFontScale() {
        runShell("settings put system font_scale 1.3")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val longRouteName = "大字體下仍然可以辨識的超長常用巴士路線名稱"
                scenario.onActivity { activity ->
                    setField(activity, "hasAttemptedNearbyRouteSelection", true)
                    insertRoute(longRouteName, 22.2768)
                    invokeNoArg(activity, "loadRouteConfigs")
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val container = activity.findViewById<ViewGroup>(R.id.routeShortcutCardsContainer)
                    assertEquals(true, allText(container).contains(longRouteName))
                    assertEquals(true, container.childCount >= 1)
                    assertEquals(true, container.getChildAt(0).height > 0)
                }
            }
        } finally {
            runShell("settings put system font_scale 1.0")
        }
    }

    @Test
    fun configurationChangeKeepsUsageEligibilityWhileColdLaunchResetsIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var selectedRouteId: Long? = null
            scenario.onActivity { activity ->
                selectedRouteId = selectedRoute(activity)?.id
                val session = getField<SavedRouteUsageSession>(activity, "savedRouteUsageSession")
                assertEquals(true, session.consumeUsageRecord(selectedRouteId!!))
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val session = getField<SavedRouteUsageSession>(activity, "savedRouteUsageSession")
                assertEquals(selectedRouteId, session.selectedRouteId)
                assertEquals(selectedRouteId, session.recordedRouteId)
            }
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val session = getField<SavedRouteUsageSession>(activity, "savedRouteUsageSession")
                assertEquals(null, session.recordedRouteId)
            }
        }
    }

    private fun insertRoute(name: String, originLatitude: Double): Long {
        val repository = RouteConfigRepository(context)
        return try {
            repository.insert(
                name,
                Place(name, originLatitude, 114.2395),
                Place("終點", 22.30, 114.20)
            )
        } finally {
            repository.close()
        }
    }

    private fun routeNames(activity: MainActivity): List<String> {
        return getField<List<RouteConfig>>(activity, "routeConfigs").map { it.name }
    }

    private fun routeByName(activity: MainActivity, name: String): RouteConfig {
        return getField<List<RouteConfig>>(activity, "routeConfigs").first { it.name == name }
    }

    private fun selectedRoute(activity: MainActivity): RouteConfig? {
        return getField(activity, "selectedRoute")
    }

    private fun invokeNearbySelection(activity: MainActivity, generation: Int) {
        activity.javaClass.getDeclaredMethod(
            "selectNearbyRouteWhenLocationAvailable",
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(activity, generation)
    }

    private fun invokeSelectRoute(activity: MainActivity, route: RouteConfig) {
        activity.javaClass.getDeclaredMethod("selectRoute", RouteConfig::class.java)
            .apply { isAccessible = true }
            .invoke(activity, route)
    }

    private fun invokeNoArg(activity: MainActivity, name: String) {
        activity.javaClass.getDeclaredMethod(name)
            .apply { isAccessible = true }
            .invoke(activity)
    }

    private fun setField(target: Any, name: String, value: Any) {
        target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }.set(target, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(target: Any, name: String): T {
        return target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }.get(target) as T
    }

    private fun firstText(root: View): String {
        return when (root) {
            is TextView -> root.text.toString()
            is ViewGroup -> (0 until root.childCount)
                .asSequence()
                .map { firstText(root.getChildAt(it)) }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            else -> ""
        }
    }

    private fun allText(root: View): String {
        return when (root) {
            is TextView -> root.text.toString()
            is ViewGroup -> (0 until root.childCount)
                .joinToString(" ") { allText(root.getChildAt(it)) }
            else -> ""
        }
    }

    private fun runShell(command: String) {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        descriptor.close()
    }
}
