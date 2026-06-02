package com.example.busiscomming

import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.SortDirection
import com.example.busiscomming.data.model.SortField
import com.example.busiscomming.data.repository.BusRouteSorter
import org.junit.Assert.assertEquals
import org.junit.Test

class BusRouteSorterTest {
    private val routes = listOf(
        BusRouteOption("A", 8.0, 12),
        BusRouteOption("B", 6.5, 5),
        BusRouteOption("C", 10.0, 8)
    )

    @Test
    fun sortsByPriceAscending() {
        val sorted = BusRouteSorter.sort(routes, SortField.PRICE, SortDirection.ASC)

        assertEquals(listOf("B", "A", "C"), sorted.map { it.routeName })
    }

    @Test
    fun sortsByPriceDescending() {
        val sorted = BusRouteSorter.sort(routes, SortField.PRICE, SortDirection.DESC)

        assertEquals(listOf("C", "A", "B"), sorted.map { it.routeName })
    }

    @Test
    fun sortsByWaitTimeAscending() {
        val sorted = BusRouteSorter.sort(routes, SortField.WAIT_TIME, SortDirection.ASC)

        assertEquals(listOf("B", "C", "A"), sorted.map { it.routeName })
    }

    @Test
    fun sortsByWaitTimeDescending() {
        val sorted = BusRouteSorter.sort(routes, SortField.WAIT_TIME, SortDirection.DESC)

        assertEquals(listOf("A", "C", "B"), sorted.map { it.routeName })
    }
}
