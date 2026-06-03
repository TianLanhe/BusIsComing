package com.example.busiscomming

import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.SortDirection
import com.example.busiscomming.data.model.SortField
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
}
