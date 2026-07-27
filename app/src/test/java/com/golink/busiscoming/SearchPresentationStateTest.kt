package com.golink.busiscoming

import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.ui.main.SearchDisplayMode
import com.golink.busiscoming.ui.main.SearchPresentationState
import com.golink.busiscoming.ui.main.SearchSaveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPresentationStateTest {
    private val origin = Place("Central", 22.2819, 114.1582)
    private val destination = Place("Causeway Bay", 22.2802, 114.1849)
    private val replacement = Place("Admiralty", 22.2798, 114.1647)

    @Test
    fun `new query enters querying with its snapshot and clears a prior saved state`() {
        val state = SearchPresentationState()
        state.beginQuery(origin, destination)
        state.completeWithResults()
        state.markSaved()

        state.beginQuery(replacement, destination)

        assertEquals(SearchDisplayMode.QUERYING, state.mode)
        assertEquals(replacement, state.querySnapshot?.origin)
        assertEquals(destination, state.querySnapshot?.destination)
        assertEquals(SearchSaveState.UNAVAILABLE, state.saveState)
    }

    @Test
    fun `nonempty completion exposes results and a save action for the current query snapshot`() {
        val state = SearchPresentationState()

        state.beginQuery(origin, destination)

        assertTrue(state.completeWithResults())
        assertEquals(SearchDisplayMode.RESULTS, state.mode)
        assertEquals(origin, state.querySnapshot?.origin)
        assertEquals(destination, state.querySnapshot?.destination)
        assertEquals(SearchSaveState.AVAILABLE, state.saveState)
    }

    @Test
    fun `empty results failure and cancellation return to editing without a stale snapshot`() {
        val state = SearchPresentationState()

        state.beginQuery(origin, destination)
        assertTrue(state.completeEmpty())
        assertEditingWithoutQueryContext(state)

        state.beginQuery(origin, destination)
        assertTrue(state.failQuery())
        assertEditingWithoutQueryContext(state)

        state.beginQuery(origin, destination)
        assertTrue(state.cancelQuery())
        assertEditingWithoutQueryContext(state)
    }

    @Test
    fun `cancel editing unchanged results restores the folded result state and saved marker`() {
        val state = SearchPresentationState()
        state.beginQuery(origin, destination)
        state.completeWithResults()
        state.markSaved()

        assertTrue(state.beginEditingResults())
        assertEquals(SearchDisplayMode.EDITING_RESULTS, state.mode)
        assertTrue(state.cancelEditing())

        assertEquals(SearchDisplayMode.RESULTS, state.mode)
        assertEquals(SearchSaveState.SAVED, state.saveState)
        assertEquals(origin, state.querySnapshot?.origin)
    }

    @Test
    fun `actual input change while editing results invalidates the old results snapshot and save action`() {
        val state = SearchPresentationState()
        state.beginQuery(origin, destination)
        state.completeWithResults()
        state.beginEditingResults()

        assertTrue(state.onInputChanged())

        assertEquals(SearchDisplayMode.DIRTY_EDITING, state.mode)
        assertNull(state.querySnapshot)
        assertEquals(SearchSaveState.UNAVAILABLE, state.saveState)
        assertFalse(state.cancelEditing())
    }

    @Test
    fun `saving marks only the current successful query and rejects duplicate save transitions`() {
        val state = SearchPresentationState()
        state.beginQuery(origin, destination)
        state.completeWithResults()

        assertTrue(state.markSaved())
        assertEquals(SearchSaveState.SAVED, state.saveState)
        assertFalse(state.markSaved())
    }

    @Test
    fun `input changes without retained results remain editing rather than inventing a result context`() {
        val state = SearchPresentationState()

        assertFalse(state.onInputChanged())

        assertEquals(SearchDisplayMode.EDITING, state.mode)
        assertNull(state.querySnapshot)
        assertEquals(SearchSaveState.UNAVAILABLE, state.saveState)
    }

    @Test
    fun `input change while querying returns to editing and rejects that query completion`() {
        val state = SearchPresentationState()
        state.beginQuery(origin, destination)

        assertTrue(state.onInputChanged())

        assertEditingWithoutQueryContext(state)
        assertFalse(state.completeWithResults())
    }

    private fun assertEditingWithoutQueryContext(state: SearchPresentationState) {
        assertEquals(SearchDisplayMode.EDITING, state.mode)
        assertNull(state.querySnapshot)
        assertEquals(SearchSaveState.UNAVAILABLE, state.saveState)
    }
}
