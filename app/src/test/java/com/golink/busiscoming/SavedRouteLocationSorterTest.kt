package com.golink.busiscoming

import com.golink.busiscoming.data.location.CurrentLocationSnapshot
import com.golink.busiscoming.data.location.SavedRouteLocationSorter
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SavedRouteLocationSorterTest {
    private val currentLocation = CurrentLocationSnapshot(
        latitude = 22.2766,
        longitude = 114.2395,
        accuracyMeters = 30f,
        elapsedRealtimeMillis = 1_000L
    )

    @Test
    fun keepsRepositoryOrderWhenLocationIsUnavailable() {
        val routes = listOf(
            route(1, "常用第一", 22.35, 114.25, usageCount = 8),
            route(2, "常用第二", 22.2768, 114.2397, usageCount = 1)
        )

        val sorted = SavedRouteLocationSorter.sort(routes, location = null)

        assertSame(routes, sorted)
    }

    @Test
    fun sortsRoutesByOriginDistanceAscending() {
        val routes = listOf(
            route(1, "遠", 22.35, 114.25, usageCount = 20),
            route(2, "近", 22.2768, 114.2397, usageCount = 1),
            route(3, "中", 22.29, 114.24, usageCount = 10)
        )

        val sorted = SavedRouteLocationSorter.sort(routes, currentLocation)

        assertEquals(listOf("近", "中", "遠"), sorted.map { it.name })
    }

    @Test
    fun usesUsageCountThenLastUsedTimeForEqualDistance() {
        val routes = listOf(
            route(1, "較少使用", 22.28, 114.24, usageCount = 2, lastUsedAt = 500L),
            route(2, "較早使用", 22.28, 114.24, usageCount = 5, lastUsedAt = 100L),
            route(3, "最近使用", 22.28, 114.24, usageCount = 5, lastUsedAt = 300L)
        )

        val sorted = SavedRouteLocationSorter.sort(routes, currentLocation)

        assertEquals(listOf("最近使用", "較早使用", "較少使用"), sorted.map { it.name })
    }

    @Test
    fun preservesRepositoryFallbackOrderWhenAllVisibleKeysTie() {
        val routes = listOf(
            route(30, "更新較新", 22.28, 114.24, usageCount = 5, lastUsedAt = 300L),
            route(20, "更新較舊", 22.28, 114.24, usageCount = 5, lastUsedAt = 300L)
        )

        val sorted = SavedRouteLocationSorter.sort(routes, currentLocation)

        assertEquals(listOf(30L, 20L), sorted.map { it.id })
    }

    @Test
    fun coarseLocationStillParticipatesInSorting() {
        val routes = listOf(
            route(1, "遠", 22.35, 114.25),
            route(2, "近", 22.2768, 114.2397)
        )
        val coarseLocation = currentLocation.copy(accuracyMeters = 2_000f)

        val sorted = SavedRouteLocationSorter.sort(routes, coarseLocation)

        assertEquals(listOf("近", "遠"), sorted.map { it.name })
    }

    private fun route(
        id: Long,
        name: String,
        latitude: Double,
        longitude: Double,
        usageCount: Int = 0,
        lastUsedAt: Long? = null
    ): RouteConfig {
        return RouteConfig(
            id = id,
            name = name,
            origin = Place("起點$name", latitude, longitude),
            destination = Place("終點$name", 22.4, 114.3),
            usageCount = usageCount,
            lastUsedAt = lastUsedAt
        )
    }
}
