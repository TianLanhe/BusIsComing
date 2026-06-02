package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.BusRouteOption

interface BusRouteRepository {
    fun searchRoutes(origin: String, destination: String): List<BusRouteOption>
}
