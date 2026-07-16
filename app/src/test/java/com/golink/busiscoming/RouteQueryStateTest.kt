package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.ui.main.RouteQueryState
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteQueryStateTest {
    @Test
    fun `initial results default to duration and preserve an existing sort on refresh`() {
        val state = RouteQueryState()
        val routes = listOf(
            BusRouteOption("slow", listOf("1"), 1.0, 30, 30, 0, 100),
            BusRouteOption("fast", listOf("2"), 1.0, 10, 10, 0, 100)
        )

        state.replaceInitial(routes, preserveSort = false)
        assertEquals(listOf("fast", "slow"), state.results.map { it.routeName })
        state.toggleSort(SortField.ROUTE)
        state.replaceInitial(routes.reversed(), preserveSort = true)
        assertEquals(SortField.ROUTE, state.sortField)
    }

    @Test
    fun `eta update reorders arrival sorted results`() {
        val state = RouteQueryState()
        val routes = listOf(
            BusRouteOption("later", listOf("1"), 1.0, 10, 10, 0, 100, resultId = "later"),
            BusRouteOption("soon", listOf("2"), 1.0, 10, 10, 0, 100, resultId = "soon")
        )
        state.replaceInitial(routes, preserveSort = false)
        state.toggleSort(SortField.ARRIVAL)
        state.updateWaitTime("later", WaitTimeState.Available(8))
        state.updateWaitTime("soon", WaitTimeState.Available(3))

        assertEquals(listOf("soon", "later"), state.results.map { it.routeName })
    }
}
