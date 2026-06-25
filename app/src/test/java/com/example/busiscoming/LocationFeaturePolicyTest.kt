package com.example.busiscoming

import com.example.busiscoming.data.location.CurrentLocationSnapshot
import com.example.busiscoming.data.location.GeoDistanceCalculator
import com.example.busiscoming.data.location.NearbyRouteSelectionPolicy
import com.example.busiscoming.data.location.PlaceNameResolutionResult
import com.example.busiscoming.data.location.PlaceDistanceFormatter
import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.model.RouteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFeaturePolicyTest {
    @Test
    fun formatsCandidateDistanceBoundaries() {
        assertEquals("368m", PlaceDistanceFormatter.compact(368.4))
        assertEquals("999m", PlaceDistanceFormatter.compact(999.4))
        assertEquals("1.0km", PlaceDistanceFormatter.compact(999.6))
        assertEquals("1.3km", PlaceDistanceFormatter.compact(1249.6))
    }

    @Test
    fun calculatesKnownHongKongDistance() {
        val distance = GeoDistanceCalculator.distanceMeters(
            fromLatitude = 22.28552,
            fromLongitude = 114.15769,
            toLatitude = 22.31880,
            toLongitude = 114.16850
        )

        assertTrue(distance in 3700..3900)
    }

    @Test
    fun currentPlaceKeepsOriginalCoordinatesWithResolvedAddressName() {
        val snapshot = CurrentLocationSnapshot(22.2766, 114.2395, 30f, 1_000L)
        val nameResult = PlaceNameResolutionResult.Success("柴灣道")
        val place = Place(
            name = nameResult.addressName,
            latitude = snapshot.latitude,
            longitude = snapshot.longitude
        )

        assertEquals("柴灣道", place.name)
        assertEquals(22.2766, place.latitude, 0.0)
        assertEquals(114.2395, place.longitude, 0.0)
    }

    @Test
    fun nearbyRouteSelectsNearestWhenPrecise() {
        val routes = listOf(
            route(1, "遠", 22.35, 114.25),
            route(2, "近", 22.2768, 114.2397)
        )

        val selected = NearbyRouteSelectionPolicy.selectRoute(
            location = CurrentLocationSnapshot(22.2766, 114.2395, 30f, 1_000L),
            routes = routes
        )

        assertEquals("近", selected?.name)
    }

    @Test
    fun nearbyRouteFallsBackWhenCoarseAndNotClearlyAhead() {
        val routes = listOf(
            route(1, "A", 22.2768, 114.2397),
            route(2, "B", 22.2770, 114.2399)
        )

        val selected = NearbyRouteSelectionPolicy.selectRoute(
            location = CurrentLocationSnapshot(22.2766, 114.2395, 800f, 1_000L),
            routes = routes
        )

        assertNull(selected)
    }

    private fun route(id: Long, name: String, latitude: Double, longitude: Double): RouteConfig {
        return RouteConfig(
            id = id,
            name = name,
            origin = Place("起點$name", latitude, longitude),
            destination = Place("終點$name", 22.4, 114.3)
        )
    }
}
