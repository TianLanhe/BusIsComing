package com.golink.busiscoming

import com.golink.busiscoming.ui.main.SearchPlaceAttributionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPlaceAttributionStateTest {
    @Test
    fun googleAttributionMovesWithItsFieldAndCanBeClearedIndependently() {
        val state = SearchPlaceAttributionState()

        state.setOriginGoogleMaps(true)
        state.swap()

        assertFalse(state.originUsesGoogleMaps)
        assertTrue(state.destinationUsesGoogleMaps)

        state.clearDestination()

        assertFalse(state.originUsesGoogleMaps)
        assertFalse(state.destinationUsesGoogleMaps)
    }

    @Test
    fun restoredAttributionPreservesTheOwningField() {
        val state = SearchPlaceAttributionState(
            originUsesGoogleMaps = false,
            destinationUsesGoogleMaps = true
        )

        assertFalse(state.originUsesGoogleMaps)
        assertTrue(state.destinationUsesGoogleMaps)
    }
}
