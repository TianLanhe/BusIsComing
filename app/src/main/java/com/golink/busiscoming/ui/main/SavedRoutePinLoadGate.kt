package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.RoutePinRecord

class SavedRoutePinLoadGate {
    private var pending: Pending? = null

    fun begin(
        queryId: Int,
        journeyId: Long,
        baselineMutationGenerations: Map<String, Long> = emptyMap()
    ) {
        pending = Pending(
            queryId = queryId,
            journeyId = journeyId,
            baselineMutationGenerations = baselineMutationGenerations.toMap()
        )
    }

    fun acceptRoutes(queryId: Int, routes: List<BusRouteOption>): Completion? {
        val state = pending?.takeIf { it.queryId == queryId } ?: return null
        state.routes = routes
        state.routesReady = true
        return completeIfReady(state)
    }

    fun acceptPins(
        queryId: Int,
        journeyId: Long,
        result: Result<List<RoutePinRecord>>
    ): Completion? {
        val state = pending?.takeIf {
            it.queryId == queryId && it.journeyId == journeyId
        } ?: return null
        state.pins = result.getOrDefault(emptyList())
        state.pinReadFailed = result.isFailure
        state.pinsReady = true
        return completeIfReady(state)
    }

    fun invalidate() {
        pending = null
    }

    private fun completeIfReady(state: Pending): Completion? {
        if (!state.routesReady || !state.pinsReady || state.delivered) return null
        state.delivered = true
        return Completion(
            queryId = state.queryId,
            journeyId = state.journeyId,
            routes = state.routes,
            pins = state.pins,
            pinReadFailed = state.pinReadFailed,
            baselineMutationGenerations = state.baselineMutationGenerations
        )
    }

    data class Completion(
        val queryId: Int,
        val journeyId: Long,
        val routes: List<BusRouteOption>,
        val pins: List<RoutePinRecord>,
        val pinReadFailed: Boolean,
        val baselineMutationGenerations: Map<String, Long>
    )

    private data class Pending(
        val queryId: Int,
        val journeyId: Long,
        val baselineMutationGenerations: Map<String, Long>,
        var routes: List<BusRouteOption> = emptyList(),
        var pins: List<RoutePinRecord> = emptyList(),
        var routesReady: Boolean = false,
        var pinsReady: Boolean = false,
        var pinReadFailed: Boolean = false,
        var delivered: Boolean = false
    )
}
