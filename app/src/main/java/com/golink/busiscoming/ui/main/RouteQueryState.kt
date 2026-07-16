package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.data.model.SortDirection
import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.BusRouteSorter

class RouteQueryState {
    var results: List<BusRouteOption> = emptyList()
        private set
    var sortField: SortField? = null
        private set
    var sortDirection: SortDirection = SortDirection.ASC
        private set
    var updatedAtMillis: Long? = null
        private set
    var errorMessage: String? = null
        private set
    var isQueryInProgress: Boolean = false
        private set
    var isRefreshing: Boolean = false
        private set

    fun begin(refresh: Boolean) {
        isQueryInProgress = true
        isRefreshing = refresh
        errorMessage = null
        if (!refresh) {
            results = emptyList()
            updatedAtMillis = null
        }
    }

    fun complete(
        routes: List<BusRouteOption>,
        preserveSort: Boolean,
        updatedAtMillis: Long
    ) {
        replaceInitial(routes, preserveSort)
        this.updatedAtMillis = updatedAtMillis
        errorMessage = null
        isQueryInProgress = false
        isRefreshing = false
    }

    fun fail(message: String, preserveResults: Boolean) {
        if (!preserveResults) {
            results = emptyList()
            sortField = null
            sortDirection = SortDirection.ASC
            updatedAtMillis = null
        }
        errorMessage = message
        isQueryInProgress = false
        isRefreshing = false
    }

    fun cancel() {
        isQueryInProgress = false
        isRefreshing = false
    }

    fun restoreSort(field: SortField?, direction: SortDirection) {
        sortField = field
        sortDirection = direction
        field?.let { results = BusRouteSorter.sort(results, it, direction) }
    }

    fun replaceInitial(routes: List<BusRouteOption>, preserveSort: Boolean) {
        val nextSort = RouteResultsRefreshPolicy.resolveSortField(preserveSort, sortField)
        if (RouteResultsRefreshPolicy.shouldResetSortDirection(preserveSort, sortField)) {
            sortDirection = SortDirection.ASC
        }
        sortField = nextSort
        results = BusRouteSorter.sort(routes, nextSort, sortDirection)
    }

    fun toggleSort(field: SortField) {
        sortDirection = if (sortField == field && sortDirection == SortDirection.ASC) {
            SortDirection.DESC
        } else {
            SortDirection.ASC
        }
        sortField = field
        results = BusRouteSorter.sort(results, field, sortDirection)
    }

    fun updateWaitTime(routeId: String, waitTimeState: WaitTimeState): Boolean {
        return update(routeId) { it.copy(waitTimeState = waitTimeState) }
    }

    fun updateStopPreview(routeId: String, preview: RouteCardStopPreview): Boolean {
        return update(routeId) { it.copy(stopPreview = preview) }
    }

    fun update(routeId: String, transform: (BusRouteOption) -> BusRouteOption): Boolean {
        return updateInternal(routeId, transform)
    }

    fun clear() {
        results = emptyList()
        sortField = null
        sortDirection = SortDirection.ASC
        updatedAtMillis = null
        errorMessage = null
        isQueryInProgress = false
        isRefreshing = false
    }

    private fun updateInternal(routeId: String, transform: (BusRouteOption) -> BusRouteOption): Boolean {
        var changed = false
        results = results.map { route ->
            if (route.resultId == routeId) {
                changed = true
                transform(route)
            } else {
                route
            }
        }
        if (changed && sortField == SortField.ARRIVAL) {
            results = BusRouteSorter.sort(results, SortField.ARRIVAL, sortDirection)
        }
        return changed
    }
}
