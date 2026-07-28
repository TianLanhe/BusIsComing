package com.golink.busiscoming

import com.golink.busiscoming.ui.common.PlaceCandidatePresentationPolicy
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

    @Test
    fun searchCandidateHeightShowsFiveCompleteRowsWhenSpaceAllowsAndNeverMoreThanSix() {
        assertEquals(
            260,
            PlaceCandidatePresentationPolicy.heightPx(
                availableHeightPx = 1_000,
                rowHeightPx = 52,
                itemCount = 5,
                maxVisibleRows = 6
            )
        )
        assertEquals(
            312,
            PlaceCandidatePresentationPolicy.heightPx(
                availableHeightPx = 1_000,
                rowHeightPx = 52,
                itemCount = 20,
                maxVisibleRows = 6
            )
        )
    }

    @Test
    fun searchCandidateHeightFallsBackToOnlyCompleteRowsWhenImeLeavesLessSpace() {
        assertEquals(
            208,
            PlaceCandidatePresentationPolicy.heightPx(
                availableHeightPx = 231,
                rowHeightPx = 52,
                itemCount = 20,
                maxVisibleRows = 6
            )
        )
        assertEquals(
            52,
            PlaceCandidatePresentationPolicy.heightPx(
                availableHeightPx = 63,
                rowHeightPx = 52,
                itemCount = 20
            )
        )
        assertEquals(
            0,
            PlaceCandidatePresentationPolicy.heightPx(
                availableHeightPx = 51,
                rowHeightPx = 52,
                itemCount = 20,
                maxVisibleRows = 6
            )
        )
    }

    @Test
    fun editorBootstrapRequestsOneCompleteRowOnlyWhenInitialSpaceCannotFitOne() {
        assertEquals(
            52,
            PlaceCandidatePresentationPolicy.editorBootstrapHeightPx(
                availableHeightPx = 51,
                rowHeightPx = 52,
                itemCount = 20
            )
        )
        assertEquals(
            52,
            PlaceCandidatePresentationPolicy.editorBootstrapHeightPx(
                availableHeightPx = 0,
                rowHeightPx = 52,
                itemCount = 1
            )
        )
        assertEquals(
            0,
            PlaceCandidatePresentationPolicy.editorBootstrapHeightPx(
                availableHeightPx = 52,
                rowHeightPx = 52,
                itemCount = 20
            )
        )
        assertEquals(
            0,
            PlaceCandidatePresentationPolicy.editorBootstrapHeightPx(
                availableHeightPx = 51,
                rowHeightPx = 52,
                itemCount = 0
            )
        )
    }
}
