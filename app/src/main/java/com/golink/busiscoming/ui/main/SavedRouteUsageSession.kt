package com.golink.busiscoming.ui.main

class SavedRouteUsageSession(
    selectedRouteId: Long? = null,
    recordedRouteId: Long? = null
) {
    var selectedRouteId: Long? = selectedRouteId
        private set

    var recordedRouteId: Long? = recordedRouteId
        private set

    fun selectSavedRoute(routeId: Long) {
        if (selectedRouteId == routeId) return
        selectedRouteId = routeId
        recordedRouteId = null
    }

    fun onTemporaryQuery() = Unit

    fun consumeUsageRecord(routeId: Long): Boolean {
        selectSavedRoute(routeId)
        if (recordedRouteId == routeId) return false
        recordedRouteId = routeId
        return true
    }
}
