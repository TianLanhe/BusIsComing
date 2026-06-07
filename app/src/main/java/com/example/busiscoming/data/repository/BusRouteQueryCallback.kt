package com.example.busiscoming.data.repository

import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.RouteCardStopPreview
import com.example.busiscoming.data.model.WaitTimeState

interface BusRouteQueryCallback {
    fun onInitialRoutes(routes: List<BusRouteOption>)
    fun onRouteWaitTimeUpdated(routeId: String, waitTimeState: WaitTimeState)
    fun onRouteStopPreviewUpdated(routeId: String, preview: RouteCardStopPreview) = Unit
    fun onFailure(error: Throwable)
}
