package com.golink.busiscoming

import com.golink.busiscoming.data.local.CrossOperatorSnapshotStore
import com.golink.busiscoming.data.local.RouteDatabaseSnapshot
import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.CrossOperatorEtaQuery
import com.golink.busiscoming.data.model.CrossOperatorRouteMatch
import com.golink.busiscoming.data.model.CtbRouteSlice
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.JointOperatorRoute
import com.golink.busiscoming.data.model.StaticRouteStop
import com.golink.busiscoming.data.model.StaticRouteVariant
import com.golink.busiscoming.data.repository.CrossOperatorMappingReason
import com.golink.busiscoming.data.repository.CrossOperatorMappingRepository
import com.golink.busiscoming.data.repository.CrossOperatorMappingResolution
import com.golink.busiscoming.data.repository.CrossOperatorMatchStore
import com.golink.busiscoming.data.repository.CtbRouteSliceStore
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossOperatorMappingRepositoryTest {
    @Test
    fun nonJointRouteDoesNotLoadCtbStaticStopsOrRunDp() {
        var loads = 0
        val repository = repository(
            snapshot = snapshot(jointRoutes = emptyList()),
            slice = null,
            loader = { _, _ -> loads += 1; emptyList() }
        )

        val result = repository.resolve(query(), "C1", "C2")

        assertEquals(
            CrossOperatorMappingResolution.Disabled(CrossOperatorMappingReason.NOT_JOINT),
            result
        )
        assertEquals(0, loads)
    }

    @Test
    fun resolvesKmbWinnerFromCurrentSliceAndReusesPersistentMatch() {
        var matchCalls = 0
        val matchStore = MemoryMatchStore()
        val repository = repository(
            snapshot = snapshot(),
            slice = slice("2026-08-17"),
            matchStore = matchStore,
            matchRoutes = { ctb, candidates ->
                matchCalls += 1
                com.golink.busiscoming.data.repository.CrossOperatorRouteMatcher().match(ctb, candidates)
            }
        )

        val first = repository.resolve(query(), "C1", "C2") as CrossOperatorMappingResolution.Enabled
        val second = repository.resolve(query(), "C1", "C2") as CrossOperatorMappingResolution.Enabled

        assertEquals(
            CrossOperatorEtaQuery(BusOperator.KMB, "118", "I", "1", "K1", "K2"),
            first.query
        )
        assertEquals(first.query, second.query)
        assertEquals(1, matchCalls)
        assertEquals(1, matchStore.saveCount)
    }

    @Test
    fun staleSliceIsUsedImmediatelyAndRefreshedInBackground() {
        val executor = QueueExecutor()
        var loads = 0
        val repository = repository(
            snapshot = snapshot(),
            slice = slice("2026-08-16"),
            executor = executor,
            loader = { _, _ -> loads += 1; listOf(slice("2026-08-17")) }
        )

        assertTrue(repository.resolve(query(), "C1", "C2") is CrossOperatorMappingResolution.Enabled)
        assertEquals(0, loads)
        assertEquals(1, executor.tasks.size)
        executor.runAll()
        assertEquals(1, loads)
    }

    @Test
    fun changedInputDiscardsOldDpAndRecomputesAtMostOnceBeforeSaving() {
        val snapshotStore = MemorySnapshotStore(snapshot())
        val sliceStore = MemorySliceStore(slice("2026-08-17"))
        val matchStore = MemoryMatchStore()
        var matchCalls = 0
        val repository = CrossOperatorMappingRepository(
            snapshotStore = snapshotStore,
            sliceStore = sliceStore,
            matchStore = matchStore,
            routeLoader = { _, _ -> error("must not load") },
            backgroundExecutor = Executor { it.run() },
            clock = { millis("2026-08-17T06:00:00+08:00") },
            matchRoutes = { ctb, candidates ->
                matchCalls += 1
                if (matchCalls == 1) {
                    sliceStore.saveCtbRouteSlices(
                        listOf(slice("2026-08-17").copy(fingerprint = "new-fingerprint"))
                    )
                }
                com.golink.busiscoming.data.repository.CrossOperatorRouteMatcher().match(ctb, candidates)
            }
        )

        val result = repository.resolve(query(), "C1", "C2")

        assertTrue(result is CrossOperatorMappingResolution.Enabled)
        assertEquals(2, matchCalls)
        assertEquals(1, matchStore.saveCount)
    }

    @Test
    fun transientSliceFailureDoesNotPersistNoMatch() {
        val matchStore = MemoryMatchStore()
        val repository = repository(
            snapshot = snapshot(),
            slice = null,
            loader = { _, _ -> error("temporary") },
            matchStore = matchStore
        )

        assertEquals(
            CrossOperatorMappingResolution.Disabled(CrossOperatorMappingReason.SLICE_UNAVAILABLE),
            repository.resolve(query(), "C1", "C2")
        )
        assertEquals(0, matchStore.saveCount)
    }

    private fun repository(
        snapshot: RouteDatabaseSnapshot,
        slice: CtbRouteSlice?,
        loader: (String, String) -> List<CtbRouteSlice> = { _, _ -> error("must not load") },
        matchStore: MemoryMatchStore = MemoryMatchStore(),
        executor: Executor = Executor { it.run() },
        matchRoutes: (StaticRouteVariant, List<StaticRouteVariant>) -> CrossOperatorRouteMatch =
            { ctb, candidates ->
                com.golink.busiscoming.data.repository.CrossOperatorRouteMatcher().match(ctb, candidates)
            }
    ): CrossOperatorMappingRepository {
        val snapshotStore = MemorySnapshotStore(snapshot)
        val sliceStore = MemorySliceStore(slice)
        return CrossOperatorMappingRepository(
            snapshotStore = snapshotStore,
            sliceStore = sliceStore,
            matchStore = matchStore,
            routeLoader = loader,
            backgroundExecutor = executor,
            clock = { millis("2026-08-17T06:00:00+08:00") },
            matchRoutes = matchRoutes
        )
    }

    private fun snapshot(
        jointRoutes: List<JointOperatorRoute> = listOf(JointOperatorRoute("118", BusOperator.KMB))
    ): RouteDatabaseSnapshot {
        return RouteDatabaseSnapshot(
            id = "snapshot",
            dataDay = "2026-08-17",
            completedAtMillis = 1L,
            jointRoutes = jointRoutes,
            ctbRoutes = emptyList(),
            variants = listOf(
                StaticRouteVariant(
                    BusOperator.KMB,
                    "118",
                    "I",
                    "1",
                    listOf(
                        StaticRouteStop("K1", 4, 22.28001, 114.15001),
                        StaticRouteStop("K2", 5, 22.29001, 114.16001)
                    )
                )
            )
        )
    }

    private fun slice(day: String) = CtbRouteSlice(
        route = "118",
        direction = "outbound",
        verifiedDataDay = day,
        fingerprint = "slice-$day",
        stops = listOf(
            StaticRouteStop("C1", 1, 22.28, 114.15),
            StaticRouteStop("C2", 2, 22.29, 114.16)
        )
    )

    private fun query() = FirstLegEtaQuery(
        company = "CTB",
        routeVariant = "118-TOS-1",
        route = "118",
        boardingSeq = 1,
        alightingSeq = 2,
        bound = "O",
        directionPath = "outbound",
        rawInfo = "raw",
        lang = "0"
    )

    private fun millis(value: String): Long =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).parse(value)!!.time

    private class MemorySnapshotStore(private val snapshot: RouteDatabaseSnapshot) :
        CrossOperatorSnapshotStore {
        override fun stageSnapshot(snapshot: RouteDatabaseSnapshot) = Unit
        override fun activateSnapshot(snapshotId: String) = Unit
        override fun activeSnapshot(): RouteDatabaseSnapshot = snapshot
        override fun invalidateMatchesForRoute(route: String) = Unit
    }

    private class MemorySliceStore(slice: CtbRouteSlice?) : CtbRouteSliceStore {
        private val values = mutableMapOf<String, CtbRouteSlice>()

        init {
            if (slice != null) values["${slice.route}|${slice.direction}"] = slice
        }

        override fun loadCtbRouteSlice(route: String, direction: String): CtbRouteSlice? =
            values["$route|$direction"]

        override fun saveCtbRouteSlices(slices: List<CtbRouteSlice>) {
            slices.forEach { values["${it.route}|${it.direction}"] = it }
        }
    }

    private class MemoryMatchStore : CrossOperatorMatchStore {
        private var value: CrossOperatorRouteMatch? = null
        var saveCount = 0

        override fun load(
            route: String,
            direction: String,
            ctbFingerprint: String,
            snapshotId: String,
            operatorFingerprint: String
        ): CrossOperatorRouteMatch? = value

        override fun save(
            route: String,
            direction: String,
            ctbFingerprint: String,
            snapshotId: String,
            operatorFingerprint: String,
            match: CrossOperatorRouteMatch
        ) {
            saveCount += 1
            value = match
        }
    }

    private class QueueExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) {
            tasks += command
        }
        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }
}
