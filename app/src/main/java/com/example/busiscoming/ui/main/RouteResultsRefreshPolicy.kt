package com.example.busiscoming.ui.main

import com.example.busiscoming.data.model.SortField

object RouteResultsRefreshPolicy {
    fun canRefresh(
        hasQueryContext: Boolean,
        hasResults: Boolean,
        isQueryInProgress: Boolean
    ): Boolean {
        return hasQueryContext && hasResults && !isQueryInProgress
    }

    fun shouldRecordUsage(isRefresh: Boolean, recordUsageRequested: Boolean): Boolean {
        return recordUsageRequested && !isRefresh
    }

    fun resolveSortField(
        preserveSort: Boolean,
        currentSortField: SortField?,
        defaultSortField: SortField = SortField.DURATION
    ): SortField {
        return if (preserveSort && currentSortField != null) currentSortField else defaultSortField
    }

    fun shouldResetSortDirection(preserveSort: Boolean, currentSortField: SortField?): Boolean {
        return !preserveSort || currentSortField == null
    }
}
