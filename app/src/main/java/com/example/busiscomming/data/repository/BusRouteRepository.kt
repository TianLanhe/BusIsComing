package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.Place

interface BusRouteRepository {
    fun searchRoutes(origin: Place, destination: Place): List<BusRouteOption>

    fun searchRoutesProgressively(
        origin: Place,
        destination: Place,
        callback: BusRouteQueryCallback
    ) {
        runCatching { searchRoutes(origin, destination) }
            .onSuccess { callback.onInitialRoutes(it) }
            .onFailure { callback.onFailure(it) }
    }

    fun cancelProgressiveQueries() = Unit
}
