package com.golink.busiscoming

import com.golink.busiscoming.data.local.RouteDatabaseSnapshot
import com.golink.busiscoming.data.model.CachedStaticSource
import com.golink.busiscoming.data.model.GlobalStaticSource
import com.golink.busiscoming.data.repository.GlobalFetchResponse
import com.golink.busiscoming.data.repository.GlobalUpdateResult
import com.golink.busiscoming.data.repository.HttpGlobalStaticDataFetcher
import com.golink.busiscoming.data.repository.RouteDatabaseUpdateCoordinator
import com.golink.busiscoming.data.repository.RouteDatabaseUpdateState
import com.golink.busiscoming.data.repository.RouteDatabaseUpdateTrigger
import com.golink.busiscoming.data.repository.RetryingGlobalStaticDataFetcher
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.concurrent.Executor
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossOperatorUpdateCoordinatorTest {
    @Test
    fun httpFetcherUsesFiveOfficialUrlsConditionalHeadersAndExtractsOnlyGtfsRoutes() {
        val requests = mutableListOf<Pair<URL, Map<String, String>>>()
        val fetcher = HttpGlobalStaticDataFetcher { url, headers ->
            requests += url to headers
            GlobalFetchResponse(
                200,
                if (url.path.endsWith("gtfs.zip")) gtfsZip() else "{}".toByteArray(),
                "new-etag",
                "new-last-modified"
            )
        }
        val cached = CachedStaticSource("old-etag", "old-last-modified", byteArrayOf(1))

        val gtfs = fetcher.fetch(GlobalStaticSource.GTFS_ROUTES, cached)
        GlobalStaticSource.entries.drop(1).forEach { source -> fetcher.fetch(source, cached) }

        assertTrue(gtfs.body!!.toString(Charsets.UTF_8).startsWith("route_id,agency_id"))
        assertEquals(5, requests.size)
        assertEquals("old-etag", requests.first().second["If-None-Match"])
        assertEquals("old-last-modified", requests.first().second["If-Modified-Since"])
        assertEquals(
            setOf(
                "https://static.data.gov.hk/td/pt-headway-tc/gtfs.zip",
                "https://data.etabus.gov.hk/v1/transport/kmb/route/",
                "https://data.etabus.gov.hk/v1/transport/kmb/route-stop",
                "https://data.etabus.gov.hk/v1/transport/kmb/stop",
                "https://rt.data.gov.hk/v2/transport/citybus/route/CTB"
            ),
            requests.map { it.first.toString() }.toSet()
        )
    }

    @Test
    fun autoAndManualChecksJoinOneSingleFlightAndManualBypassesDataDay() {
        val executor = QueueExecutor()
        var calls = 0
        var active: RouteDatabaseSnapshot? = null
        val coordinator = RouteDatabaseUpdateCoordinator(
            activeSnapshot = { active },
            update = { day ->
                calls += 1
                RouteDatabaseSnapshot(day, day, 200L, emptyList(), emptyList(), emptyList()).also {
                    active = it
                }.let { GlobalUpdateResult.Success(true, it) }
            },
            clock = { millis("2026-08-17T06:00:00+08:00") },
            executor = executor
        )
        val states = mutableListOf<RouteDatabaseUpdateState>()
        coordinator.observe(states::add)

        assertTrue(coordinator.check(RouteDatabaseUpdateTrigger.APP_FOREGROUND))
        assertFalse(coordinator.check(RouteDatabaseUpdateTrigger.MANUAL))
        assertEquals(1, executor.pendingCount)
        executor.runNext()

        assertEquals(1, calls)
        assertTrue(states.last() is RouteDatabaseUpdateState.Success)
        assertFalse(coordinator.check(RouteDatabaseUpdateTrigger.APP_FOREGROUND))
        assertTrue(coordinator.check(RouteDatabaseUpdateTrigger.MANUAL))
        executor.runNext()
        assertEquals(2, calls)
    }

    @Test
    fun retriesTransientFailureWithFiniteBackoffButDoesNotLoopOnForbidden() {
        var attempts = 0
        val delays = mutableListOf<Long>()
        val retrying = RetryingGlobalStaticDataFetcher(
            delegate = { _, _ ->
                attempts += 1
                if (attempts < 3) throw java.io.IOException("temporary")
                GlobalFetchResponse(200, "ok".toByteArray(), null, null)
            },
            maxAttempts = 3,
            firstBackoffMillis = 10,
            sleeper = delays::add
        )

        assertEquals(200, retrying.fetch(GlobalStaticSource.KMB_ROUTES, null).statusCode)
        assertEquals(3, attempts)
        assertEquals(listOf(10L, 20L), delays)

        attempts = 0
        val forbidden = RetryingGlobalStaticDataFetcher(
            delegate = { _, _ ->
                attempts += 1
                GlobalFetchResponse(403, null, null, null)
            },
            sleeper = { error("403 must not be retried") }
        )
        assertEquals(403, forbidden.fetch(GlobalStaticSource.KMB_STOPS, null).statusCode)
        assertEquals(1, attempts)
    }

    @Test
    fun failedAutomaticCheckDoesNotLoopOnEveryForegroundInSameDataDay() {
        val executor = QueueExecutor()
        var calls = 0
        val coordinator = RouteDatabaseUpdateCoordinator(
            activeSnapshot = { null },
            update = {
                calls += 1
                GlobalUpdateResult.Failure("unavailable")
            },
            clock = { millis("2026-08-17T06:00:00+08:00") },
            executor = executor
        )

        assertTrue(coordinator.check(RouteDatabaseUpdateTrigger.APP_FOREGROUND))
        executor.runNext()

        assertEquals(1, calls)
        assertFalse(coordinator.check(RouteDatabaseUpdateTrigger.APP_FOREGROUND))
        assertTrue(coordinator.check(RouteDatabaseUpdateTrigger.MANUAL))
        executor.runNext()
        assertEquals(2, calls)
    }

    private fun gtfsZip(): ByteArray {
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("routes.txt"))
                zip.write("route_id,agency_id\n118,KMB+CTB\n".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("stops.txt"))
                zip.write("ignored".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
    }

    private fun millis(value: String): Long {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            .parse(value)!!.time
    }

    private class QueueExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        val pendingCount: Int get() = tasks.size

        override fun execute(command: Runnable) {
            tasks += command
        }

        fun runNext() {
            tasks.removeFirst().run()
        }
    }
}
