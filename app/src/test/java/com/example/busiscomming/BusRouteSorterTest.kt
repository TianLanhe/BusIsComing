package com.example.busiscomming

import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.SortDirection
import com.example.busiscomming.data.model.SortField
import com.example.busiscomming.data.model.WaitTimeState
import com.example.busiscomming.data.model.toDisplayText
import com.example.busiscomming.data.repository.BusRouteSorter
import org.junit.Assert.assertEquals
import org.junit.Test

class BusRouteSorterTest {
    private val routes = listOf(
        BusRouteOption("82X \u2192 102", listOf("82X", "102"), 20.4, 34, 34, 1, 456),
        BusRouteOption("8X", listOf("8X"), 8.1, 45, 45, 0, 438),
        BusRouteOption("8P \u2192 A11", listOf("8P", "A11"), 14.9, 39, 39, 1, 673),
        BusRouteOption("8P \u2192 10 \u2192 85", listOf("8P", "10", "85"), 18.1, 42, 42, 2, 296)
    )

    @Test
    fun sortsByRouteTransfersAscending() {
        val sorted = BusRouteSorter.sort(routes, SortField.ROUTE, SortDirection.ASC)

        assertEquals(
            listOf("8X", "82X \u2192 102", "8P \u2192 A11", "8P \u2192 10 \u2192 85"),
            sorted.map { it.routeName }
        )
    }

    @Test
    fun sortsByRouteTransfersDescending() {
        val sorted = BusRouteSorter.sort(routes, SortField.ROUTE, SortDirection.DESC)

        assertEquals(
            listOf("8P \u2192 10 \u2192 85", "8P \u2192 A11", "82X \u2192 102", "8X"),
            sorted.map { it.routeName }
        )
    }

    @Test
    fun sortsByPriceAscending() {
        val sorted = BusRouteSorter.sort(routes, SortField.PRICE, SortDirection.ASC)

        assertEquals(
            listOf("8X", "8P \u2192 A11", "8P \u2192 10 \u2192 85", "82X \u2192 102"),
            sorted.map { it.routeName }
        )
    }

    @Test
    fun sortsByPriceDescending() {
        val sorted = BusRouteSorter.sort(routes, SortField.PRICE, SortDirection.DESC)

        assertEquals(
            listOf("82X \u2192 102", "8P \u2192 10 \u2192 85", "8P \u2192 A11", "8X"),
            sorted.map { it.routeName }
        )
    }

    @Test
    fun sortsByDurationAscending() {
        val sorted = BusRouteSorter.sort(routes, SortField.DURATION, SortDirection.ASC)

        assertEquals(
            listOf("82X \u2192 102", "8P \u2192 A11", "8P \u2192 10 \u2192 85", "8X"),
            sorted.map { it.routeName }
        )
    }

    @Test
    fun sortsByDurationDescending() {
        val sorted = BusRouteSorter.sort(routes, SortField.DURATION, SortDirection.DESC)

        assertEquals(
            listOf("8X", "8P \u2192 10 \u2192 85", "8P \u2192 A11", "82X \u2192 102"),
            sorted.map { it.routeName }
        )
    }

    @Test
    fun sortsByArrivalAscending() {
        val sorted = BusRouteSorter.sort(routes, SortField.ARRIVAL, SortDirection.ASC)

        assertEquals(
            listOf("82X \u2192 102", "8P \u2192 A11", "8P \u2192 10 \u2192 85", "8X"),
            sorted.map { it.routeName }
        )
    }

    @Test
    fun sortsByArrivalDescending() {
        val sorted = BusRouteSorter.sort(routes, SortField.ARRIVAL, SortDirection.DESC)

        assertEquals(
            listOf("8X", "8P \u2192 10 \u2192 85", "8P \u2192 A11", "82X \u2192 102"),
            sorted.map { it.routeName }
        )
    }

    @Test
    fun sortsByWaitTimeWithLoadingAndUnavailableLastAscending() {
        val sorted = BusRouteSorter.sort(waitTimeRoutes, SortField.ARRIVAL, SortDirection.ASC)

        assertEquals(
            listOf("available 3", "available 8", "loading", "unavailable"),
            sorted.map { it.routeName }
        )
    }

    @Test
    fun sortsByWaitTimeWithLoadingAndUnavailableLastDescending() {
        val sorted = BusRouteSorter.sort(waitTimeRoutes, SortField.ARRIVAL, SortDirection.DESC)

        assertEquals(
            listOf("available 8", "available 3", "loading", "unavailable"),
            sorted.map { it.routeName }
        )
    }

    @Test
    fun formatsWaitTimeStatesForDisplay() {
        assertEquals("3", WaitTimeState.Available(3).toDisplayText())
        assertEquals("...", WaitTimeState.Loading.toDisplayText())
        assertEquals("-", WaitTimeState.Unavailable.toDisplayText())
    }

    @Test
    fun sortsByWalkingDistanceAscending() {
        val sorted = BusRouteSorter.sort(routes, SortField.WALKING_DISTANCE, SortDirection.ASC)

        assertEquals(
            listOf("8P \u2192 10 \u2192 85", "8X", "82X \u2192 102", "8P \u2192 A11"),
            sorted.map { it.routeName }
        )
    }

    @Test
    fun sortsByWalkingDistanceDescending() {
        val sorted = BusRouteSorter.sort(routes, SortField.WALKING_DISTANCE, SortDirection.DESC)

        assertEquals(
            listOf("8P \u2192 A11", "82X \u2192 102", "8X", "8P \u2192 10 \u2192 85"),
            sorted.map { it.routeName }
        )
    }

    private val waitTimeRoutes = listOf(
        BusRouteOption("loading", listOf("1"), 1.0, 10, 10, 0, 100, WaitTimeState.Loading),
        BusRouteOption("available 8", listOf("2"), 1.0, 10, 10, 0, 100, WaitTimeState.Available(8)),
        BusRouteOption("unavailable", listOf("3"), 1.0, 10, 10, 0, 100, WaitTimeState.Unavailable),
        BusRouteOption("available 3", listOf("4"), 1.0, 10, 10, 0, 100, WaitTimeState.Available(3))
    )
}
