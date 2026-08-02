package com.golink.busiscoming

import com.golink.busiscoming.data.repository.CitybusRouteGeometryParseException
import com.golink.busiscoming.data.repository.CitybusRouteGeometryParser
import com.golink.busiscoming.data.repository.CitybusRouteGeometryRepository
import com.golink.busiscoming.data.repository.RouteGeometryCache
import com.golink.busiscoming.data.repository.RouteGeometryRequest
import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.data.model.RouteGeometryCoordinate
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CitybusRouteGeometryRepositoryTest {
    @Test
    fun repositoryBuildsMinimalRequestAndRejectsInvalidKeysBeforeFetching() {
        var fetchCount = 0
        val repository = CitybusRouteGeometryRepository(
            geometryFetcher = { _: URL, headers ->
                fetchCount += 1
                assertEquals(emptyMap<String, String>(), headers)
                "first,22.3,114.1\nsecond,22.4,114.2"
            }
        )
        val key = RouteGeometryKey("780-CEF-1", 6, 17)

        assertEquals(
            "https://mobile.citybus.com.hk/nwp3/getlinep2p.php?rdv=780-CEF-1&start=6&dest=17",
            repository.buildGeometryUrl(key).toString()
        )
        assertEquals(2, repository.loadGeometry(key).points.size)
        assertEquals(1, fetchCount)
        assertThrows(IllegalArgumentException::class.java) {
            repository.loadGeometry(RouteGeometryKey("", 6, 17))
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.loadGeometry(RouteGeometryKey("780-CEF-1", 18, 17))
        }
        assertEquals(1, fetchCount)
    }

    @Test
    fun repositoryCachesOnlyValidatedSuccessUntilExpiry() {
        var now = 1_000L
        var fetchCount = 0
        val repository = CitybusRouteGeometryRepository(
            cache = RouteGeometryCache(clock = { now }, ttlMillis = 100L),
            geometryFetcher = { _, _ ->
                fetchCount += 1
                "first,22.3000,114.1000\nsecond,22.3100,114.1100"
            }
        )
        val key = RouteGeometryKey("82X-ISR-1", 6, 9)
        val boarding = RouteGeometryCoordinate(22.3001, 114.1001)
        val alighting = RouteGeometryCoordinate(22.3101, 114.1101)

        repository.loadGeometry(key, boarding, alighting)
        repository.loadGeometry(key, boarding, alighting)
        assertEquals(1, fetchCount)

        now += 101L
        repository.loadGeometry(key, boarding, alighting)
        assertEquals(2, fetchCount)

        val mismatchedRepository = CitybusRouteGeometryRepository(
            geometryFetcher = { _, _ ->
                fetchCount += 1
                "first,22.3000,114.1000\nsecond,22.3100,114.1100"
            }
        )
        repeat(2) {
            assertThrows(CitybusRouteGeometryParseException::class.java) {
                mismatchedRepository.loadGeometry(
                    key,
                    RouteGeometryCoordinate(24.0, 116.0),
                    alighting
                )
            }
        }
        assertEquals(4, fetchCount)
    }

    @Test
    fun loadGeometriesUsesAtMostThreeWorkersAndReportsEachSegment() {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val firstThreeStarted = CountDownLatch(3)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(5)
        val repository = CitybusRouteGeometryRepository(
            geometryFetcher = { _, _ ->
                val current = active.incrementAndGet()
                maxActive.updateAndGet { previous -> maxOf(previous, current) }
                firstThreeStarted.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                active.decrementAndGet()
                "first,22.3000,114.1000\nsecond,22.3100,114.1100"
            }
        )
        val requests = (1..5).map { index ->
            RouteGeometryRequest(RouteGeometryKey("ROUTE-$index", 1, 2))
        }

        repository.loadGeometries(requests) { _, result ->
            if (result.isSuccess) completed.countDown()
        }

        check(firstThreeStarted.await(2, TimeUnit.SECONDS))
        assertEquals(3, maxActive.get())
        release.countDown()
        check(completed.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun concurrentCallsForTheSameKeyShareOneFetch() {
        val fetchCount = AtomicInteger(0)
        val fetchStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repository = CitybusRouteGeometryRepository(
            geometryFetcher = { _, _ ->
                fetchCount.incrementAndGet()
                fetchStarted.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                "first,22.3000,114.1000\nsecond,22.3100,114.1100"
            }
        )
        val key = RouteGeometryKey("82X-ISR-1", 6, 9)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<RouteGeometryKey> {
                repository.loadGeometry(key).key
            }
            check(fetchStarted.await(2, TimeUnit.SECONDS))
            val second = executor.submit<RouteGeometryKey> {
                repository.loadGeometry(key).key
            }

            Thread.sleep(100)
            assertEquals(1, fetchCount.get())
            release.countDown()
            assertEquals(key, first.get(2, TimeUnit.SECONDS))
            assertEquals(key, second.get(2, TimeUnit.SECONDS))
            assertEquals(1, fetchCount.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun partialFailureReportsEachSegmentWithoutCachingTheFailure() {
        val attempts = mutableMapOf<String, Int>()
        val completed = CountDownLatch(2)
        val results = mutableMapOf<String, Boolean>()
        val repository = CitybusRouteGeometryRepository(
            geometryFetcher = { url, _ ->
                val variant = Regex("rdv=([^&]+)").find(url.query)?.groupValues?.get(1).orEmpty()
                synchronized(attempts) { attempts[variant] = attempts.getOrDefault(variant, 0) + 1 }
                if (variant == "FAILED") error("segment unavailable")
                "first,22.3000,114.1000\nsecond,22.3100,114.1100"
            }
        )
        val successful = RouteGeometryRequest(RouteGeometryKey("SUCCESS", 1, 2))
        val failed = RouteGeometryRequest(RouteGeometryKey("FAILED", 1, 2))

        repository.loadGeometries(listOf(successful, failed)) { request, result ->
            synchronized(results) { results[request.key.routeVariant] = result.isSuccess }
            completed.countDown()
        }

        assertTrue(completed.await(2, TimeUnit.SECONDS))
        assertEquals(mapOf("SUCCESS" to true, "FAILED" to false), results)
        assertThrows(IllegalStateException::class.java) { repository.loadGeometry(failed.key) }
        assertEquals(2, attempts["FAILED"])
    }

    @Test
    fun closingLoadHandleInvalidatesLateCallbacks() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val callbacks = AtomicInteger(0)
        val repository = CitybusRouteGeometryRepository(
            geometryFetcher = { _, _ ->
                started.countDown()
                release.await(2, TimeUnit.SECONDS)
                "first,22.3000,114.1000\nsecond,22.3100,114.1100"
            }
        )

        val handle = repository.loadGeometries(
            listOf(RouteGeometryRequest(RouteGeometryKey("CANCELLED", 1, 2)))
        ) { _, _ -> callbacks.incrementAndGet() }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        handle.close()
        release.countDown()
        Thread.sleep(100)

        assertEquals(0, callbacks.get())
    }

    @Test
    fun parserPreservesValidFixturePointOrder() {
        val points = CitybusRouteGeometryParser.parse(
            resourceText("citybus/getlinep2p-780-CEF-1-6-17.txt")
        )

        assertEquals(649, points.size)
        assertEquals("18239231", points.first().pointId)
        assertEquals(22.262426062091, points.first().latitude, 0.0)
        assertEquals(114.23437573053, points.first().longitude, 0.0)
        assertEquals("18231931", points.last().pointId)
    }

    @Test
    fun parserIgnoresMalformedLinesButRequiresTwoValidPoints() {
        val points = CitybusRouteGeometryParser.parse(
            """
            first,22.3000,114.1000
            missing,22.3001
            nonnumeric,latitude,longitude
            outside,95.0,114.0
            second,22.4000,114.2000
            """.trimIndent()
        )

        assertEquals(listOf("first", "second"), points.map { it.pointId })
        assertThrows(CitybusRouteGeometryParseException::class.java) {
            CitybusRouteGeometryParser.parse("only,22.3,114.1\ninvalid,NaN,114.2")
        }
        assertThrows(CitybusRouteGeometryParseException::class.java) {
            CitybusRouteGeometryParser.parse("")
        }
    }

    private fun resourceText(path: String): String {
        return requireNotNull(javaClass.classLoader?.getResource(path)).readText()
    }
}
