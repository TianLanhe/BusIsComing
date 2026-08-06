package com.golink.busiscoming

import com.golink.busiscoming.data.model.RouteDetailCompleteness
import com.golink.busiscoming.data.repository.CitybusRouteDetailParser
import com.golink.busiscoming.data.repository.CitybusRouteDetailRepository
import com.golink.busiscoming.data.repository.CitybusRouteParser
import com.golink.busiscoming.data.repository.RouteDetailCacheOwner
import com.golink.busiscoming.data.repository.RouteStructureCache
import com.golink.busiscoming.data.repository.RouteStructureCacheKey
import com.golink.busiscoming.data.repository.WalkingDistanceCache
import com.golink.busiscoming.data.repository.WalkingDistanceCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailDomainCachesTest {
    @Test
    fun cachesExpireAndStructureIsIsolatedByLanguage() {
        var now = 1_000L
        val cache = RouteStructureCache(clock = { now }, ttlMillis = 100L)
        val detail = completeDetail()
        val tcKey = RouteStructureCacheKey("plan", "0")
        val enKey = RouteStructureCacheKey("plan", "1")

        cache.put(tcKey, plan(), detail)

        assertNotNull(cache.get(tcKey))
        assertNull(cache.get(enKey))

        now += 100L

        assertNull(cache.get(tcKey))
    }

    @Test
    fun structureCacheRejectsInvalidOrLowerQualityAndDropsDynamicFields() {
        val cache = RouteStructureCache()
        val plan = plan()
        val key = RouteStructureCacheKey(plan.fingerprint(), "0")
        val complete = completeDetail()

        assertTrue(cache.put(key, plan, complete))
        assertFalse(
            cache.put(
                key,
                plan,
                complete.copy(
                    legs = complete.legs.map { it.copy(directionText = null) },
                    originName = null,
                    destinationName = null
                )
            )
        )
        assertFalse(
            cache.put(
                key,
                plan,
                complete.copy(legs = listOf(complete.legs.single().copy(viaStops = emptyList())))
            )
        )

        val snapshot = requireNotNull(cache.get(key))
        val leg = snapshot.legs.single()
        assertNotNull(leg.directionText)
        assertNull(leg.fareHkd)
        assertNull(leg.plannedBoardingTime)
        assertNull(leg.plannedAlightingTime)
    }

    @Test
    fun sharedCacheOwnerServesReliableStructureAcrossRepositoryConsumers() {
        val owner = RouteDetailCacheOwner()
        var fetchCount = 0
        val query = com.golink.busiscoming.data.model.P2pRouteDetailQuery(
            rawInfo = "1|*|CTB||N118-TOS-1||5||9||O|*|",
            generalInfo = "02:04|*|13",
            listId = "0",
            lang = "0",
            plan = plan()
        )
        val route = com.golink.busiscoming.data.model.BusRouteOption(
            routeName = "N118",
            routeSegments = listOf("N118"),
            priceHkd = 17.8,
            durationMinutes = 13,
            arrivalMinutes = 13,
            transferCount = 0,
            walkingDistanceMeters = 262,
            routeDetailQuery = query
        )
        val first = CitybusRouteDetailRepository(
            cacheOwner = owner,
            detailFetcher = { _, _ ->
                fetchCount += 1
                fixture()
            }
        )
        val second = CitybusRouteDetailRepository(
            cacheOwner = owner,
            detailFetcher = { _, _ -> error("network must not be needed for cache read") }
        )

        first.loadRouteDetail(route)
        val cached = second.loadCachedRouteDetail(route)

        assertEquals(1, fetchCount)
        assertEquals(4, cached?.totalViaStopCount)
        assertNull(cached?.legs?.single()?.fareHkd)
        assertEquals("02:04", cached?.plannedArrivalTime)
    }

    @Test
    fun partialOrSessionMissingWalkingNeverOverwritesCompleteWalking() {
        val cache = WalkingDistanceCache()
        val key = WalkingDistanceCacheKey("origin:destination", "plan")
        val complete = completeDetail()

        cache.put(key, complete)
        cache.put(key, complete.copy(completeness = RouteDetailCompleteness.PARTIAL))
        cache.put(
            key,
            complete.copy(
                originWalking = complete.originWalking?.copy(distanceMeters = null),
                completeness = RouteDetailCompleteness.SESSION_MISSING
            )
        )

        assertEquals(236, cache.get(key)?.originDistanceMeters)
        assertEquals(26, cache.get(key)?.destinationDistanceMeters)
    }

    private fun completeDetail() = CitybusRouteDetailParser.parseDetail(
        fixture(),
        plan()
    )

    private fun plan() = requireNotNull(
        CitybusRouteParser.parseP2pRoutePlan("1|*|CTB||N118-TOS-1||5||9||O|*|")
    )

    private fun fixture() = requireNotNull(
        javaClass.classLoader?.getResource("citybus/getp2pstopinroute-n118.html")
    ).readText()
}
