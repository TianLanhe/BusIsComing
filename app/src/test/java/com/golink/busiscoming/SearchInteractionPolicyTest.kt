package com.golink.busiscoming

import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.ui.main.SearchCurrentPlaceRequestState
import com.golink.busiscoming.ui.main.SearchResultSaveEligibility
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchInteractionPolicyTest {
    private val origin = Place("Central", 22.2819, 114.1582)
    private val destination = Place("Causeway Bay", 22.2802, 114.1849)

    @Test
    fun `save is visible only for a nonempty result matching current places`() {
        assertTrue(
            SearchResultSaveEligibility.isVisible(
                queryOrigin = origin,
                queryDestination = destination,
                currentOrigin = origin,
                currentDestination = destination,
                resultCount = 2,
                queryInProgress = false,
                queryFailed = false
            )
        )
    }

    @Test
    fun `save is hidden for changed input new query failure or empty result`() {
        val replacement = origin.copy(name = "Admiralty")
        assertFalse(SearchResultSaveEligibility.isVisible(origin, destination, replacement, destination, 2, false, false))
        assertFalse(SearchResultSaveEligibility.isVisible(origin, destination, origin, destination, 2, true, false))
        assertFalse(SearchResultSaveEligibility.isVisible(origin, destination, origin, destination, 2, false, true))
        assertFalse(SearchResultSaveEligibility.isVisible(origin, destination, origin, destination, 0, false, false))
    }

    @Test
    fun `auto location starts once only when no origin state exists`() {
        val state = SearchCurrentPlaceRequestState()

        val first = state.beginAutoRequest(
            hasSelectedOrigin = false,
            originInput = "",
            hasSubmittedQuery = false
        )

        assertNotNull(first)
        assertNull(state.beginAutoRequest(false, "", false))
        assertTrue(state.isPending)
    }

    @Test
    fun `auto location does not start over restored or submitted origin state`() {
        assertNull(SearchCurrentPlaceRequestState().beginAutoRequest(true, "", false))
        assertNull(SearchCurrentPlaceRequestState().beginAutoRequest(false, "Central", false))
        assertNull(SearchCurrentPlaceRequestState().beginAutoRequest(false, "", true))
    }

    @Test
    fun `restored search starts at most one silent candidate snapshot request`() {
        val state = SearchCurrentPlaceRequestState()

        assertNull(state.beginSilentSnapshotRequest(canRequest = false))
        val token = state.beginSilentSnapshotRequest(canRequest = true)

        assertNotNull(token)
        assertNull(state.beginSilentSnapshotRequest(canRequest = true))
        assertTrue(state.finish(token!!))
    }

    @Test
    fun `a newer request invalidates a silent candidate snapshot callback`() {
        val state = SearchCurrentPlaceRequestState()
        val stale = state.beginSilentSnapshotRequest(canRequest = true)!!

        state.beginManualRequest()

        assertFalse(state.finish(stale))
    }

    @Test
    fun `invalidated location callback cannot finish or overwrite a newer state`() {
        val state = SearchCurrentPlaceRequestState()
        val stale = state.beginManualRequest()

        state.invalidate()

        assertFalse(state.isCurrent(stale))
        assertFalse(state.finish(stale))
        assertFalse(state.isPending)
    }
}
