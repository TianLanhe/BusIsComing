package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.data.model.WaitTimeState

interface BusRouteQueryCallback {
    fun onInitialRoutes(routes: List<BusRouteOption>)
    fun onRouteWaitTimeUpdated(routeId: String, waitTimeState: WaitTimeState)
    fun onRouteStopPreviewUpdated(routeId: String, preview: RouteCardStopPreview) = Unit
    fun onFailure(error: Throwable)
}
