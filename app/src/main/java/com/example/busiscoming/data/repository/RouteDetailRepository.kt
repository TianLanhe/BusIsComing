package com.example.busiscoming.data.repository

import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.RouteDetail

interface RouteDetailRepository {
    fun loadRouteDetail(route: BusRouteOption): RouteDetail
}
