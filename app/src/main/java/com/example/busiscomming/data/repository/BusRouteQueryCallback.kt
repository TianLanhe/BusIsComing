package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.WaitTimeState

interface BusRouteQueryCallback {
    fun onInitialRoutes(routes: List<BusRouteOption>)
    fun onRouteWaitTimeUpdated(routeId: String, waitTimeState: WaitTimeState)
    fun onFailure(error: Throwable)
}
