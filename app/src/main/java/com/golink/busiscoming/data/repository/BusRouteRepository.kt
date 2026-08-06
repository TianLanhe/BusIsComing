package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place

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

    fun searchRoutesProgressively(
        origin: Place,
        destination: Place,
        walkingTrigger: PedestrianRequestTrigger,
        callback: BusRouteQueryCallback
    ) = searchRoutesProgressively(origin, destination, callback)

    fun cancelProgressiveQueries() = Unit
}
