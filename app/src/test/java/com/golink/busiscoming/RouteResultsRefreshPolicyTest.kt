package com.golink.busiscoming

import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.ui.main.RouteResultsRefreshPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteResultsRefreshPolicyTest {
    @Test
    fun enablesRefreshOnlyForIdleResultsWithQueryContext() {
        assertTrue(
            RouteResultsRefreshPolicy.canRefresh(
                hasQueryContext = true,
                hasResults = true,
                isQueryInProgress = false
            )
        )
        assertFalse(RouteResultsRefreshPolicy.canRefresh(true, true, isQueryInProgress = true))
        assertFalse(RouteResultsRefreshPolicy.canRefresh(true, hasResults = false, false))
        assertFalse(RouteResultsRefreshPolicy.canRefresh(hasQueryContext = false, true, false))
    }

    @Test
    fun refreshDoesNotRecordRouteUsageForSavedOrTemporaryQueries() {
        assertFalse(RouteResultsRefreshPolicy.shouldRecordUsage(isRefresh = true, recordUsageRequested = true))
        assertFalse(RouteResultsRefreshPolicy.shouldRecordUsage(isRefresh = true, recordUsageRequested = false))
        assertTrue(RouteResultsRefreshPolicy.shouldRecordUsage(isRefresh = false, recordUsageRequested = true))
    }

    @Test
    fun refreshPreservesSortFieldAndInitialQueryFallsBackToDuration() {
        assertEquals(
            SortField.ARRIVAL,
            RouteResultsRefreshPolicy.resolveSortField(
                preserveSort = true,
                currentSortField = SortField.ARRIVAL
            )
        )
        assertEquals(
            SortField.DURATION,
            RouteResultsRefreshPolicy.resolveSortField(
                preserveSort = false,
                currentSortField = SortField.ARRIVAL
            )
        )
        assertEquals(
            SortField.DURATION,
            RouteResultsRefreshPolicy.resolveSortField(
                preserveSort = true,
                currentSortField = null
            )
        )
    }

    @Test
    fun refreshKeepsDirectionOnlyWhenExistingSortIsPreserved() {
        assertFalse(
            RouteResultsRefreshPolicy.shouldResetSortDirection(
                preserveSort = true,
                currentSortField = SortField.PRICE
            )
        )
        assertTrue(RouteResultsRefreshPolicy.shouldResetSortDirection(preserveSort = false, SortField.PRICE))
        assertTrue(RouteResultsRefreshPolicy.shouldResetSortDirection(preserveSort = true, currentSortField = null))
    }
}
