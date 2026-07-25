package com.golink.busiscoming

import com.golink.busiscoming.ui.common.PlacePairToolAlignment
import org.junit.Assert.assertEquals
import org.junit.Test

class PlacePairToolAlignmentTest {
    @Test
    fun centeredTopUsesTheMeasuredInputBounds() {
        assertEquals(
            20,
            PlacePairToolAlignment.centeredTop(
                inputTop = 12,
                inputBottom = 76,
                toolHeight = 48
            )
        )
    }

    @Test
    fun swapCenterRemovesTheVisibleOriginCandidateDisplacement() {
        assertEquals(
            46,
            PlacePairToolAlignment.swapTop(
                originCenter = 36,
                destinationCenter = 184,
                originCandidateOccupiedHeight = 80,
                toolHeight = 48
            )
        )
    }
}
