package com.golink.busiscoming

import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.ui.main.SearchQuerySnapshot
import com.golink.busiscoming.ui.main.SuccessfulSearchContextState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuccessfulSearchContextStateTest {
    private val origin = Place("Central", 22.2819, 114.1582)
    private val destination = Place("Causeway Bay", 22.2802, 114.1849)
    private val snapshot = SearchQuerySnapshot(origin, destination)

    @Test
    fun `new successful queries use distinct monotonic tokens even for the same places`() {
        val state = SuccessfulSearchContextState()

        val first = state.recordSuccess(queryId = 7, snapshot = snapshot)
        state.invalidate()
        val second = state.recordSuccess(queryId = 8, snapshot = snapshot)

        assertNotEquals(first.token, second.token)
        assertEquals(7, first.queryId)
        assertEquals(8, second.queryId)
        assertFalse(state.isCurrent(first))
        assertTrue(state.isCurrent(second))
    }

    @Test
    fun `same context refresh and destination round trip preserve ownership`() {
        val state = SuccessfulSearchContextState()
        val context = state.recordSuccess(queryId = 11, snapshot = snapshot)

        assertTrue(state.retainForRefresh(snapshot))
        assertTrue(state.isCurrent(context))
        assertEquals(context, state.currentFor(snapshot, resultCount = 2))
    }

    @Test
    fun `new query input mutation empty and failure invalidate the current context`() {
        val state = SuccessfulSearchContextState()
        val context = state.recordSuccess(queryId = 12, snapshot = snapshot)

        state.invalidate()

        assertFalse(state.isCurrent(context))
        assertNull(state.currentFor(snapshot, resultCount = 2))
    }

    @Test
    fun `only the current snapshot with nonempty results is savable`() {
        val state = SuccessfulSearchContextState()
        val context = state.recordSuccess(queryId = 13, snapshot = snapshot)
        val other = SearchQuerySnapshot(origin, destination.copy(name = "Wan Chai"))

        assertEquals(context, state.currentFor(snapshot, resultCount = 1))
        assertNull(state.currentFor(snapshot, resultCount = 0))
        assertNull(state.currentFor(other, resultCount = 1))
    }
}
