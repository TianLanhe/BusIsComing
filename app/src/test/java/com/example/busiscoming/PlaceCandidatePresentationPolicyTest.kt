package com.example.busiscoming

import com.example.busiscoming.ui.common.PlaceCandidatePresentationPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceCandidatePresentationPolicyTest {
    @Test
    fun limitsCandidateResultsToOneHundred() {
        assertEquals(100, PlaceCandidatePresentationPolicy.limit((1..140).toList()).size)
    }

    @Test
    fun candidateHeightUsesFortyPercentWithAtLeastThreeRows() {
        assertEquals(
            400,
            PlaceCandidatePresentationPolicy.heightPx(
                visibleHeightPx = 1_000,
                rowHeightPx = 48,
                itemCount = 20
            )
        )
        assertEquals(
            144,
            PlaceCandidatePresentationPolicy.heightPx(
                visibleHeightPx = 200,
                rowHeightPx = 48,
                itemCount = 20
            )
        )
        assertEquals(
            96,
            PlaceCandidatePresentationPolicy.heightPx(
                visibleHeightPx = 1_000,
                rowHeightPx = 48,
                itemCount = 2
            )
        )
    }
}
