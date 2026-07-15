package com.golink.busiscoming

import com.golink.busiscoming.data.location.CurrentLocationSnapshot
import com.golink.busiscoming.data.location.SavedRouteLocationSorter
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfig
import com.golink.busiscoming.ui.main.RouteShortcutSelector
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteShortcutSelectorTest {
    private val routes = listOf(
        route(1, "A"),
        route(2, "B"),
        route(3, "C"),
        route(4, "D"),
        route(5, "E")
    )

    @Test
    fun returnsOriginalTop3WhenNoSelectedRoute() {
        assertEquals(listOf("A", "B", "C"), RouteShortcutSelector.visibleRoutes(routes, null).map { it.name })
    }

    @Test
    fun keepsOriginalTop3WhenSelectedRouteIsAlreadyVisible() {
        assertEquals(listOf("A", "B", "C"), RouteShortcutSelector.visibleRoutes(routes, routes[1]).map { it.name })
    }

    @Test
    fun promotesSelectedRouteWhenItIsOutsideTop3() {
        assertEquals(listOf("D", "A", "B"), RouteShortcutSelector.visibleRoutes(routes, routes[3]).map { it.name })
    }

    @Test
    fun replacesPreviousPromotedRouteWhenAnotherOutsideTop3RouteIsSelected() {
        assertEquals(listOf("E", "A", "B"), RouteShortcutSelector.visibleRoutes(routes, routes[4]).map { it.name })
    }

    @Test
    fun returnsAllRoutesWhenSavedRouteCountIsTwoOrLess() {
        assertEquals(listOf("A", "B"), RouteShortcutSelector.visibleRoutes(routes.take(2), routes[1]).map { it.name })
    }

    @Test
    fun usesLocationRankedListAsShortcutBaseline() {
        val ranked = SavedRouteLocationSorter.sort(
            routes = routes,
            location = CurrentLocationSnapshot(
                latitude = routes[4].origin.latitude,
                longitude = routes[4].origin.longitude,
                accuracyMeters = 30f,
                elapsedRealtimeMillis = 1_000L
            )
        )

        assertEquals(listOf("E", "D", "C"), RouteShortcutSelector.visibleRoutes(ranked, null).map { it.name })
    }

    private fun route(id: Long, name: String): RouteConfig {
        return RouteConfig(
            id = id,
            name = name,
            origin = Place("起點$name", 22.0 + id, 114.0 + id),
            destination = Place("終點$name", 23.0 + id, 115.0 + id)
        )
    }
}
