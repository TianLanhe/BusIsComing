package com.golink.busiscoming

import com.golink.busiscoming.ui.main.RouteQueryState
import com.golink.busiscoming.ui.main.SearchQueryStatusCard
import com.golink.busiscoming.ui.main.SearchQueryUiPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryUiPolicyTest {
    @Test
    fun `initial query disables the action and displays the shared loading card`() {
        val state = RouteQueryState().apply { begin(refresh = false) }

        val ui = SearchQueryUiPolicy.resolve(
            queryState = state,
            hasSubmittedQuery = true,
            hasValidPlaces = true
        )

        assertFalse(ui.isQueryEnabled)
        assertTrue(ui.isQuerying)
        assertEquals(SearchQueryStatusCard.LOADING, ui.statusCard)
    }

    @Test
    fun `empty and failure results keep the editor actionable with their matching cards`() {
        val empty = RouteQueryState().apply {
            begin(refresh = false)
            complete(emptyList(), preserveSort = false, updatedAtMillis = 1L)
        }
        val failure = RouteQueryState().apply {
            begin(refresh = false)
            fail("failure", preserveResults = false)
        }

        val emptyUi = SearchQueryUiPolicy.resolve(empty, hasSubmittedQuery = true, hasValidPlaces = true)
        val failureUi = SearchQueryUiPolicy.resolve(failure, hasSubmittedQuery = true, hasValidPlaces = true)

        assertTrue(emptyUi.isQueryEnabled)
        assertEquals(SearchQueryStatusCard.EMPTY, emptyUi.statusCard)
        assertTrue(failureUi.isQueryEnabled)
        assertEquals(SearchQueryStatusCard.FAILURE, failureUi.statusCard)
    }

    @Test
    fun `refresh keeps readable results and hides the initial-query card`() {
        val state = RouteQueryState().apply {
            complete(listOf(route("old")), preserveSort = false, updatedAtMillis = 1L)
            begin(refresh = true)
        }

        val ui = SearchQueryUiPolicy.resolve(state, hasSubmittedQuery = true, hasValidPlaces = true)

        assertFalse(ui.isQueryEnabled)
        assertTrue(ui.isRefreshing)
        assertEquals(SearchQueryStatusCard.HIDDEN, ui.statusCard)
    }

    @Test
    fun `refresh success confirmation continues to block another search without replacing results`() {
        val state = RouteQueryState().apply {
            complete(listOf(route("old")), preserveSort = false, updatedAtMillis = 1L)
        }

        val ui = SearchQueryUiPolicy.resolve(
            queryState = state,
            hasSubmittedQuery = true,
            hasValidPlaces = true,
            refreshFeedbackVisible = true,
            refreshFeedbackBlocksQueries = true
        )

        assertFalse(ui.isQueryEnabled)
        assertEquals(SearchQueryStatusCard.HIDDEN, ui.statusCard)
        assertEquals(listOf("old"), state.results.map { it.routeName })
    }

    @Test
    fun `cancelled query restores an actionable editor without inventing an empty result card`() {
        val state = RouteQueryState().apply {
            begin(refresh = false)
            cancel()
        }

        val ui = SearchQueryUiPolicy.resolve(
            queryState = state,
            hasSubmittedQuery = false,
            hasValidPlaces = true
        )

        assertTrue(ui.isQueryEnabled)
        assertEquals(SearchQueryStatusCard.HIDDEN, ui.statusCard)
    }

    private fun route(id: String) = com.golink.busiscoming.data.model.BusRouteOption(
        routeName = id,
        routeSegments = listOf(id),
        priceHkd = 1.0,
        durationMinutes = 10,
        arrivalMinutes = 5,
        transferCount = 0,
        walkingDistanceMeters = 10,
        resultId = id
    )
}
