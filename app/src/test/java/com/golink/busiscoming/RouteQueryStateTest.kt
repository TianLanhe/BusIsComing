package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.SortDirection
import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.model.WalkingDistanceDisplayState
import com.golink.busiscoming.ui.main.RouteQueryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(listOf("slow", "fast"), state.rawResults.map { it.routeName })
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
        assertEquals(
            listOf(8, 3),
            state.rawResults.map { (it.waitTimeState as WaitTimeState.Available).minutes }
        )
    }

    @Test
    fun `successful query records update time and clears loading and error state`() {
        val state = RouteQueryState()
        val routes = listOf(route("result"))

        state.begin(refresh = false)
        state.fail("temporary", preserveResults = false)
        state.begin(refresh = false)
        state.complete(routes, preserveSort = false, updatedAtMillis = 1234L)

        assertFalse(state.isQueryInProgress)
        assertFalse(state.isRefreshing)
        assertNull(state.errorMessage)
        assertEquals(1234L, state.updatedAtMillis)
        assertEquals(routes.map { it.resultId }, state.results.map { it.resultId })
    }

    @Test
    fun `refresh failure preserves results sort and previous update time`() {
        val state = RouteQueryState()
        state.complete(listOf(route("2"), route("1")), preserveSort = false, updatedAtMillis = 100L)
        state.toggleSort(SortField.ROUTE)
        val previousResults = state.results
        val previousDirection = state.sortDirection

        state.begin(refresh = true)
        state.fail("refresh failed", preserveResults = true)

        assertEquals(previousResults, state.results)
        assertEquals(SortField.ROUTE, state.sortField)
        assertEquals(previousDirection, state.sortDirection)
        assertEquals(100L, state.updatedAtMillis)
        assertEquals("refresh failed", state.errorMessage)
        assertFalse(state.isQueryInProgress)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `restored sort is applied to the next result set`() {
        val state = RouteQueryState()
        state.restoreSort(SortField.ROUTE, SortDirection.DESC)

        state.complete(
            routes = listOf(route("1"), route("2")),
            preserveSort = true,
            updatedAtMillis = 100L
        )

        assertEquals(SortField.ROUTE, state.sortField)
        assertEquals(SortDirection.DESC, state.sortDirection)
        assertEquals(listOf("2", "1"), state.results.map { it.routeName })
    }

    @Test
    fun `walking update preserves raw Citybus distance and only reorders walking sort`() {
        val state = RouteQueryState()
        state.complete(
            listOf(route("first"), route("second")),
            preserveSort = false,
            updatedAtMillis = 1L
        )

        state.updateWalkingDistance("first", WalkingDistanceDisplayState.CsdiSuccess(500))
        state.updateWalkingDistance("second", WalkingDistanceDisplayState.CsdiSuccess(100))
        assertEquals(listOf("first", "second"), state.rawResults.map { it.routeName })
        assertTrue(state.rawResults.all { it.walkingDistanceMeters == 100 })

        state.toggleSort(SortField.WALKING_DISTANCE)
        assertEquals(listOf("second", "first"), state.results.map { it.routeName })
    }

    @Test
    fun `clear resets query metadata`() {
        val state = RouteQueryState()
        state.begin(refresh = false)
        state.fail("failed", preserveResults = false)

        state.clear()

        assertTrue(state.results.isEmpty())
        assertNull(state.sortField)
        assertEquals(SortDirection.ASC, state.sortDirection)
        assertNull(state.updatedAtMillis)
        assertNull(state.errorMessage)
        assertFalse(state.isQueryInProgress)
        assertFalse(state.isRefreshing)
    }

    private fun route(id: String): BusRouteOption =
        BusRouteOption(id, listOf(id), 1.0, 10, 10, 0, 100, resultId = id)
}
