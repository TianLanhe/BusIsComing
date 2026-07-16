package com.golink.busiscoming

import com.golink.busiscoming.ui.navigation.TopLevelDestination
import com.golink.busiscoming.ui.navigation.TopLevelDestinationState
import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationStateTest {
    @Test
    fun `new state defaults to frequent routes`() {
        val state = TopLevelDestinationState()

        assertEquals(TopLevelDestination.FREQUENT_ROUTES, state.selected)
    }

    @Test
    fun `selecting another destination keeps the explicit selection`() {
        val state = TopLevelDestinationState()

        state.select(TopLevelDestination.SEARCH)

        assertEquals(TopLevelDestination.SEARCH, state.selected)
    }
}
