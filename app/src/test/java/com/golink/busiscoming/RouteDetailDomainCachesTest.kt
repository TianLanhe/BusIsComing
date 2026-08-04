package com.golink.busiscoming

import com.golink.busiscoming.data.model.RouteDetailCompleteness
import com.golink.busiscoming.data.repository.CitybusRouteDetailParser
import com.golink.busiscoming.data.repository.CitybusRouteParser
import com.golink.busiscoming.data.repository.RouteStructureCache
import com.golink.busiscoming.data.repository.RouteStructureCacheKey
import com.golink.busiscoming.data.repository.WalkingDistanceCache
import com.golink.busiscoming.data.repository.WalkingDistanceCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RouteDetailDomainCachesTest {
    @Test
    fun cachesExpireAndStructureIsIsolatedByLanguage() {
        var now = 1_000L
        val cache = RouteStructureCache(clock = { now }, ttlMillis = 100L)
        val detail = completeDetail()
        val tcKey = RouteStructureCacheKey("plan", "0")
        val enKey = RouteStructureCacheKey("plan", "1")

        cache.put(tcKey, detail)

        assertNotNull(cache.get(tcKey))
        assertNull(cache.get(enKey))

        now += 100L

        assertNull(cache.get(tcKey))
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

        assertEquals(236, cache.get(key)?.originWalking?.distanceMeters)
        assertEquals(26, cache.get(key)?.destinationWalking?.distanceMeters)
    }

    private fun completeDetail() = CitybusRouteDetailParser.parseDetail(
        requireNotNull(javaClass.classLoader?.getResource("citybus/getp2pstopinroute-n118.html")).readText(),
        requireNotNull(
            CitybusRouteParser.parseP2pRoutePlan("1|*|CTB||N118-TOS-1||5||9||O|*|")
        )
    )
}
