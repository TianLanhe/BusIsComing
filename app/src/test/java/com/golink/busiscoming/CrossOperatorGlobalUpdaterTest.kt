package com.golink.busiscoming

import com.golink.busiscoming.data.local.CrossOperatorSnapshotStore
import com.golink.busiscoming.data.local.RouteDatabaseSnapshot
import com.golink.busiscoming.data.model.GlobalStaticSource
import com.golink.busiscoming.data.repository.CrossOperatorGlobalUpdater
import com.golink.busiscoming.data.repository.GlobalFetchResponse
import com.golink.busiscoming.data.repository.GlobalStaticDataFetcher
import com.golink.busiscoming.data.repository.GlobalUpdateResult
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossOperatorGlobalUpdaterTest {
    @Test
    fun publishesOnlyAfterAllFiveSourcesParseAndValidate() {
        val store = MemoryStore()
        val updater = CrossOperatorGlobalUpdater(
            store = store,
            fetcher = fixtureFetcher(),
            clock = { 123_456L },
            snapshotIdFactory = { "snapshot-1" }
        )

        val result = updater.update("2026-08-17") as GlobalUpdateResult.Success

        assertTrue(result.changed)
        assertEquals("snapshot-1", store.activeSnapshot()?.id)
        assertEquals(setOf("118", "S1"), store.activeSnapshot()?.jointRoutes?.map { it.route }?.toSet())
        assertEquals(2, store.activeSnapshot()?.variants?.size)
        assertEquals(GlobalStaticSource.entries.toSet(), store.activeSnapshot()?.sourceCaches?.keys)
    }

    @Test
    fun oneFailedSourceKeepsPreviousSnapshotAndTimestamp() {
        val store = MemoryStore()
        val previous = CrossOperatorGlobalUpdater(
            store = store,
            fetcher = fixtureFetcher(),
            clock = { 100L },
            snapshotIdFactory = { "previous" }
        )
        previous.update("2026-08-16")
        val failing = CrossOperatorGlobalUpdater(
            store = store,
            fetcher = GlobalStaticDataFetcher { source, cached ->
                if (source == GlobalStaticSource.KMB_STOPS) throw IOException("timeout")
                fixtureFetcher().fetch(source, cached)
            },
            clock = { 200L },
            snapshotIdFactory = { "failed" }
        )

        val result = failing.update("2026-08-17")

        assertTrue(result is GlobalUpdateResult.Failure)
        assertEquals("previous", store.activeSnapshot()?.id)
        assertEquals(100L, store.activeSnapshot()?.completedAtMillis)
    }

    @Test
    fun valid304ReusesEveryCachedBodyAndRecordsUnchangedSuccess() {
        val store = MemoryStore()
        CrossOperatorGlobalUpdater(
            store,
            fixtureFetcher(),
            clock = { 100L },
            snapshotIdFactory = { "previous" }
        ).update("2026-08-16")
        val requestedCachedSources = mutableSetOf<GlobalStaticSource>()
        val updater = CrossOperatorGlobalUpdater(
            store = store,
            fetcher = GlobalStaticDataFetcher { source, cached ->
                if (cached != null) requestedCachedSources += source
                GlobalFetchResponse(304, null, cached?.etag, cached?.lastModified)
            },
            clock = { 200L },
            snapshotIdFactory = { "current" }
        )

        val result = updater.update("2026-08-17") as GlobalUpdateResult.Success

        assertFalse(result.changed)
        assertEquals(GlobalStaticSource.entries.toSet(), requestedCachedSources)
        assertEquals("current", store.activeSnapshot()?.id)
        assertEquals(200L, store.activeSnapshot()?.completedAtMillis)
    }

    @Test
    fun rejects304WhenThereIsNoCachedBody() {
        val store = MemoryStore()
        val updater = CrossOperatorGlobalUpdater(
            store,
            GlobalStaticDataFetcher { _, _ -> GlobalFetchResponse(304, null, null, null) }
        )

        assertTrue(updater.update("2026-08-17") is GlobalUpdateResult.Failure)
        assertNull(store.activeSnapshot())
    }

    @Test
    fun fullBodiesAreCachedButNonJointReferentialDriftDoesNotDisableJointRoutes() {
        val base = fixtureFetcher()
        val fetcher = GlobalStaticDataFetcher { source, cached ->
            val response = base.fetch(source, cached)
            when (source) {
                GlobalStaticSource.KMB_ROUTES -> response.copy(
                    body = fixture("kmb_route.json")
                        .replace(
                            "]}",
                            ",{\"route\":\"252B\",\"bound\":\"O\",\"service_type\":\"1\"}]}"
                        ).toByteArray()
                )
                GlobalStaticSource.KMB_ROUTE_STOPS -> response.copy(
                    body = fixture("kmb_route_stop.json")
                        .replace(
                            "]}",
                            ",{\"route\":\"252B\",\"bound\":\"O\",\"service_type\":\"1\",\"seq\":\"1\",\"stop\":\"MISSING\"}]}"
                        ).toByteArray()
                )
                else -> response
            }
        }
        val store = MemoryStore()

        val result = CrossOperatorGlobalUpdater(store, fetcher).update("2026-08-17")

        assertTrue(result is GlobalUpdateResult.Success)
        assertEquals(setOf("118", "S1"), store.activeSnapshot()?.variants?.map { it.route }?.toSet())
        assertTrue(
            store.activeSnapshot()?.sourceCaches?.get(GlobalStaticSource.KMB_ROUTE_STOPS)
                ?.body?.toString(Charsets.UTF_8)?.contains("252B") == true
        )
    }

    private fun fixtureFetcher(): GlobalStaticDataFetcher {
        val files = mapOf(
            GlobalStaticSource.GTFS_ROUTES to "gtfs_routes.csv",
            GlobalStaticSource.KMB_ROUTES to "kmb_route.json",
            GlobalStaticSource.KMB_ROUTE_STOPS to "kmb_route_stop.json",
            GlobalStaticSource.KMB_STOPS to "kmb_stop.json",
            GlobalStaticSource.CTB_ROUTES to "ctb_route.json"
        )
        return GlobalStaticDataFetcher { source, _ ->
            GlobalFetchResponse(
                statusCode = 200,
                body = fixture(files.getValue(source)).toByteArray(),
                etag = "${source.name}-etag",
                lastModified = "Sun, 17 Aug 2026 00:00:00 GMT"
            )
        }
    }

    private fun fixture(name: String): String {
        return requireNotNull(javaClass.classLoader?.getResource("cross_operator/$name")).readText()
    }

    private class MemoryStore : CrossOperatorSnapshotStore {
        private val staged = mutableMapOf<String, RouteDatabaseSnapshot>()
        private var activeId: String? = null

        override fun stageSnapshot(snapshot: RouteDatabaseSnapshot) {
            staged[snapshot.id] = snapshot
        }

        override fun activateSnapshot(snapshotId: String) {
            require(staged.containsKey(snapshotId))
            activeId = snapshotId
        }

        override fun activeSnapshot(): RouteDatabaseSnapshot? = activeId?.let(staged::get)

        override fun invalidateMatchesForRoute(route: String) = Unit
    }
}
