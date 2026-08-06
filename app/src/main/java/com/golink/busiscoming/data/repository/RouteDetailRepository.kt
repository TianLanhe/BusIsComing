package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.RouteDetail

interface RouteDetailRepository {
    fun loadCachedRouteDetail(route: BusRouteOption): RouteDetail? = null

    fun loadRouteDetail(route: BusRouteOption): RouteDetail
}
