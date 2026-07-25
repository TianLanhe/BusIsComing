package com.golink.busiscoming

import com.golink.busiscoming.ui.common.PlaceInputMessage
import com.golink.busiscoming.ui.main.SearchFieldCaptionState
import com.golink.busiscoming.ui.main.SearchFieldCaptionStatus
import com.golink.busiscoming.ui.main.SearchFieldValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFieldCaptionStateTest {
    @Test
    fun captionPriorityIsValidationSearchLocationGoogleInstructionThenNone() {
        val state = SearchFieldCaptionState()
        assertEquals(SearchFieldCaptionStatus.INSTRUCTION, state.visibleStatus())

        state.setGoogleMaps(true)
        assertEquals(SearchFieldCaptionStatus.GOOGLE_MAPS, state.visibleStatus())

        state.setLocationFailure(true)
        assertEquals(SearchFieldCaptionStatus.LOCATION_FAILURE, state.visibleStatus())

        state.onPlaceInputMessage(PlaceInputMessage.NO_MATCHES)
        state.setLocationFailure(true)
        assertEquals(SearchFieldCaptionStatus.NO_MATCHES, state.visibleStatus())

        state.setValidation(SearchFieldValidation.MISSING_PLACE)
        assertEquals(SearchFieldCaptionStatus.MISSING_PLACE, state.visibleStatus())

        state.setValidation(null)
        state.onPlaceInputMessage(PlaceInputMessage.NONE)
        state.setLocationFailure(false)
        state.setGoogleMaps(false)
        assertNull(state.visibleStatus())
    }

    @Test
    fun messageChangesClearStaleTransientFailuresAndValidation() {
        val state = SearchFieldCaptionState()
        state.setLocationFailure(true)
        state.setValidation(SearchFieldValidation.SAME_AS_ORIGIN)

        state.onPlaceInputMessage(PlaceInputMessage.INSTRUCTION)

        assertEquals(SearchFieldCaptionStatus.INSTRUCTION, state.visibleStatus())
    }

    @Test
    fun onlySearchAndValidationStatusesUseErrorStyling() {
        assertTrue(SearchFieldCaptionStatus.SEARCH_FAILED.isError)
        assertTrue(SearchFieldCaptionStatus.MISSING_PLACE.isError)
        assertTrue(SearchFieldCaptionStatus.SAME_AS_ORIGIN.isError)
        assertFalse(SearchFieldCaptionStatus.NO_MATCHES.isError)
        assertFalse(SearchFieldCaptionStatus.LOCATION_FAILURE.isError)
        assertFalse(SearchFieldCaptionStatus.GOOGLE_MAPS.isError)
        assertFalse(SearchFieldCaptionStatus.INSTRUCTION.isError)
    }
}
