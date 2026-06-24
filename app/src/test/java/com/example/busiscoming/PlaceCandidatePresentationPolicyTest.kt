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
    fun candidateHeightUsesAvailableSpaceWithThreeToSixRows() {
        assertEquals(
            288,
            PlaceCandidatePresentationPolicy.heightPx(
                availableHeightPx = 1_000,
                rowHeightPx = 48,
                itemCount = 20
            )
        )
        assertEquals(
            144,
            PlaceCandidatePresentationPolicy.heightPx(
                availableHeightPx = 180,
                rowHeightPx = 48,
                itemCount = 20
            )
        )
        assertEquals(
            96,
            PlaceCandidatePresentationPolicy.heightPx(
                availableHeightPx = 1_000,
                rowHeightPx = 48,
                itemCount = 2
            )
        )
    }
}
