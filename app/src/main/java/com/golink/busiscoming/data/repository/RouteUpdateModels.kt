package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfig

enum class RouteUpdateFailureStage {
    AFTER_ROUTE_UPDATE
}

fun interface RouteUpdateFailureInjector {
    fun invoke(stage: RouteUpdateFailureStage)

    companion object {
        val NONE = RouteUpdateFailureInjector { }
    }
}

data class RouteEndpointSnapshot(
    val origin: Place,
    val destination: Place
) {
    companion object {
        fun from(config: RouteConfig): RouteEndpointSnapshot {
            return RouteEndpointSnapshot(
                origin = config.origin,
                destination = config.destination
            )
        }
    }
}
