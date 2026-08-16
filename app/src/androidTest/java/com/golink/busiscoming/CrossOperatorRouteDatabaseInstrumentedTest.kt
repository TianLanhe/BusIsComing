package com.golink.busiscoming

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.golink.busiscoming.data.local.CrossOperatorRouteDatabase
import com.golink.busiscoming.data.local.RouteDatabaseSnapshot
import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.CachedStaticSource
import com.golink.busiscoming.data.model.CtbRouteSlice
import com.golink.busiscoming.data.model.CrossOperatorMatchStatus
import com.golink.busiscoming.data.model.CrossOperatorRouteMatch
import com.golink.busiscoming.data.model.CrossOperatorStopPair
import com.golink.busiscoming.data.model.JointOperatorRoute
import com.golink.busiscoming.data.model.GlobalStaticSource
import com.golink.busiscoming.data.model.StaticRouteStop
import com.golink.busiscoming.data.model.StaticRouteVariant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrossOperatorRouteDatabaseInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: CrossOperatorRouteDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(CrossOperatorRouteDatabase.DATABASE_NAME)
        database = CrossOperatorRouteDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(CrossOperatorRouteDatabase.DATABASE_NAME)
    }

    @Test
    fun stagingSnapshotDoesNotReplaceActiveUntilAtomicActivation() {
        database.stageSnapshot(snapshot("first", "2026-08-16", "118"))
        database.activateSnapshot("first")
        val capturedFirst = database.activeSnapshot()

        database.stageSnapshot(snapshot("second", "2026-08-17", "S1"))
        assertEquals("first", database.activeSnapshot()?.id)
        database.activateSnapshot("second")

        assertEquals("first", capturedFirst?.id)
        assertEquals("second", database.activeSnapshot()?.id)
        assertEquals("S1", database.activeSnapshot()?.jointRoutes?.single()?.route)
        assertThrows(IllegalArgumentException::class.java) {
            database.activateSnapshot("first")
        }
        assertTrue(CrossOperatorRouteDatabase.DATABASE_NAME != "bus_is_coming.db")
    }

    @Test
    fun invalidatesOnlyChangedRouteMatchAndRejectsVersionMismatch() {
        val first = snapshot("first", "2026-08-17", "118")
        database.stageSnapshot(first)
        database.activateSnapshot(first.id)
        val variant = first.variants.single()
        val match = CrossOperatorRouteMatch(
            status = CrossOperatorMatchStatus.MATCHED,
            winner = variant,
            rawCost = 20.0,
            normalizedCost = 10.0,
            stopPairs = listOf(CrossOperatorStopPair("C1", "K1", 1, 20.0)),
            algorithmVersion = 1,
            gapCostMeters = 100.0,
            thresholdMetersPerStop = 46.0
        )
        database.saveMatch("118", "outbound", "ctb-fp", "first", "kmb-fp", match)

        assertEquals(
            CrossOperatorMatchStatus.MATCHED,
            database.loadMatch("118", "outbound", "ctb-fp", "first", "kmb-fp", 1, 100.0, 46.0)?.status
        )
        assertNull(database.loadMatch("118", "outbound", "ctb-fp", "first", "kmb-fp", 2, 100.0, 46.0))

        database.invalidateMatchesForRoute("118")
        assertNull(database.loadMatch("118", "outbound", "ctb-fp", "first", "kmb-fp", 1, 100.0, 46.0))
    }

    @Test
    fun keepsSourceBodiesWithSnapshotAndPublishesCtbSliceAtomically() {
        val snapshot = snapshot("first", "2026-08-17", "118").copy(
            sourceCaches = mapOf(
                GlobalStaticSource.GTFS_ROUTES to CachedStaticSource(
                    etag = "gtfs-v1",
                    lastModified = null,
                    body = "route_id,agency_id".toByteArray()
                )
            )
        )
        database.stageSnapshot(snapshot)
        database.activateSnapshot(snapshot.id)

        assertEquals(
            "route_id,agency_id",
            database.activeSnapshot()?.sourceCaches?.get(GlobalStaticSource.GTFS_ROUTES)
                ?.body?.toString(Charsets.UTF_8)
        )

        val slice = CtbRouteSlice(
            route = "118",
            direction = "outbound",
            verifiedDataDay = "2026-08-17",
            fingerprint = "ctb-fp",
            stops = listOf(StaticRouteStop("001227", 1, 22.2648838, 114.2415686, "樂軒臺"))
        )
        database.saveCtbRouteSlice(slice)

        assertEquals(slice, database.loadCtbRouteSlice("118", "outbound"))
        assertNull(database.loadCtbRouteSlice("118", "inbound"))
    }

    @Test
    fun largeReproducibleSourceBodyRoundTripsWithoutCursorWindowOverflow() {
        val largeBody = ByteArray(3 * 1024 * 1024) { index -> ('A'.code + index % 10).toByte() }
        val snapshot = snapshot("large", "2026-08-17", "118").copy(
            sourceCaches = mapOf(
                GlobalStaticSource.KMB_ROUTE_STOPS to CachedStaticSource(null, null, largeBody)
            )
        )

        database.stageSnapshot(snapshot)
        database.activateSnapshot(snapshot.id)
        database.close()
        database = CrossOperatorRouteDatabase(context)

        assertTrue(
            largeBody.contentEquals(
                database.activeSnapshot()?.sourceCaches
                    ?.get(GlobalStaticSource.KMB_ROUTE_STOPS)?.body
            )
        )
    }

    @Test
    fun incompatibleSchemaRebuildsOnlyTheDisposableRouteDatabase() {
        val snapshot = snapshot("before-rebuild", "2026-08-17", "118")
        database.stageSnapshot(snapshot)
        database.activateSnapshot(snapshot.id)

        database.onUpgrade(database.writableDatabase, 1, 2)

        assertNull(database.activeSnapshot())
        assertTrue(CrossOperatorRouteDatabase.DATABASE_NAME != "bus_is_coming.db")
    }

    private fun snapshot(id: String, dataDay: String, route: String): RouteDatabaseSnapshot {
        val operator = if (route == "S1") BusOperator.LWB else BusOperator.KMB
        return RouteDatabaseSnapshot(
            id = id,
            dataDay = dataDay,
            completedAtMillis = 1_000L,
            jointRoutes = listOf(JointOperatorRoute(route, operator)),
            ctbRoutes = emptyList(),
            variants = listOf(
                StaticRouteVariant(
                    operator = operator,
                    route = route,
                    direction = "I",
                    serviceType = "1",
                    stops = listOf(StaticRouteStop("K1", 1, 22.28, 114.15, "站一"))
                )
            )
        )
    }
}
