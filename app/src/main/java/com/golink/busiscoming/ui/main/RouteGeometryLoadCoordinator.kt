package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.data.repository.RouteGeometryFailurePolicy

enum class RouteGeometryLoadState {
    LOADING,
    CANDIDATE,
    LOADED,
    FAILED
}

enum class RouteGeometryRetryDecision {
    AUTO_RETRY,
    FAILED
}

class RouteGeometryLoadCoordinator(keys: List<RouteGeometryKey>) {
    private val states = linkedMapOf<RouteGeometryKey, RouteGeometryLoadState>()
    private val automaticRetryCounts = mutableMapOf<RouteGeometryKey, Int>()

    init {
        keys.distinct().forEach { states[it] = RouteGeometryLoadState.LOADING }
    }

    fun state(key: RouteGeometryKey): RouteGeometryLoadState? = states[key]

    fun onCandidate(key: RouteGeometryKey, endpointsAvailable: Boolean) {
        if (key !in states) return
        states[key] = if (endpointsAvailable) RouteGeometryLoadState.LOADED else RouteGeometryLoadState.CANDIDATE
    }

    fun onValidated(key: RouteGeometryKey) {
        if (key in states) states[key] = RouteGeometryLoadState.LOADED
    }

    fun onFailure(
        key: RouteGeometryKey,
        throwable: Throwable,
        allowAutoRetry: Boolean = true
    ): RouteGeometryRetryDecision {
        val retries = automaticRetryCounts.getOrDefault(key, 0)
        return if (
            allowAutoRetry &&
            retries < MAX_AUTOMATIC_RETRIES &&
            RouteGeometryFailurePolicy.shouldAutoRetry(throwable)
        ) {
            automaticRetryCounts[key] = retries + 1
            states[key] = RouteGeometryLoadState.LOADING
            RouteGeometryRetryDecision.AUTO_RETRY
        } else {
            states[key] = RouteGeometryLoadState.FAILED
            RouteGeometryRetryDecision.FAILED
        }
    }

    fun beginManualRetry(keys: Set<RouteGeometryKey>) {
        keys.filter { states[it] == RouteGeometryLoadState.FAILED }.forEach { key ->
            states[key] = RouteGeometryLoadState.LOADING
            automaticRetryCounts[key] = 0
        }
    }

    fun failedKeys(): Set<RouteGeometryKey> {
        return states.filterValues { it == RouteGeometryLoadState.FAILED }.keys
    }

    fun loadingCount(): Int = states.values.count { it == RouteGeometryLoadState.LOADING }

    private companion object {
        const val MAX_AUTOMATIC_RETRIES = 1
    }
}
