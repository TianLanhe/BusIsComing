package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.data.model.RouteGeometrySegment
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
    private val generations = mutableMapOf<RouteGeometryKey, Int>()
    private val candidates = mutableMapOf<RouteGeometryKey, RouteGeometrySegment>()
    private val automaticRetryCounts = mutableMapOf<RouteGeometryKey, Int>()

    init {
        keys.distinct().forEach {
            states[it] = RouteGeometryLoadState.LOADING
            generations[it] = 0
        }
    }

    fun state(key: RouteGeometryKey): RouteGeometryLoadState? = states[key]

    fun beginGeneration(key: RouteGeometryKey, generation: Int) {
        val current = generations[key] ?: return
        if (generation <= current) return
        generations[key] = generation
        states[key] = RouteGeometryLoadState.LOADING
        candidates.remove(key)
    }

    fun onCandidate(
        key: RouteGeometryKey,
        generation: Int,
        segment: RouteGeometrySegment,
        endpointsAvailable: Boolean
    ): RouteGeometrySegment? {
        if (generations[key] != generation) return null
        return if (endpointsAvailable) {
            candidates.remove(key)
            states[key] = RouteGeometryLoadState.LOADED
            segment
        } else {
            candidates[key] = segment
            states[key] = RouteGeometryLoadState.CANDIDATE
            null
        }
    }

    fun candidate(key: RouteGeometryKey, generation: Int): RouteGeometrySegment? {
        return candidates[key].takeIf { generations[key] == generation }
    }

    fun onCandidate(key: RouteGeometryKey, endpointsAvailable: Boolean) {
        if (key !in states) return
        states[key] = if (endpointsAvailable) RouteGeometryLoadState.LOADED else RouteGeometryLoadState.CANDIDATE
    }

    fun onValidated(key: RouteGeometryKey, generation: Int): RouteGeometrySegment? {
        if (generations[key] != generation) return null
        val candidate = candidates.remove(key) ?: return null
        states[key] = RouteGeometryLoadState.LOADED
        return candidate
    }

    fun onValidated(key: RouteGeometryKey) {
        if (key in states) {
            candidates.remove(key)
            states[key] = RouteGeometryLoadState.LOADED
        }
    }

    fun onFailure(
        key: RouteGeometryKey,
        generation: Int,
        throwable: Throwable,
        allowAutoRetry: Boolean = true
    ): RouteGeometryRetryDecision {
        if (generations[key] != generation) return RouteGeometryRetryDecision.FAILED
        return onCurrentFailure(key, throwable, allowAutoRetry)
    }

    fun onFailure(
        key: RouteGeometryKey,
        throwable: Throwable,
        allowAutoRetry: Boolean = true
    ): RouteGeometryRetryDecision {
        return onCurrentFailure(key, throwable, allowAutoRetry)
    }

    private fun onCurrentFailure(
        key: RouteGeometryKey,
        throwable: Throwable,
        allowAutoRetry: Boolean
    ): RouteGeometryRetryDecision {
        val retries = automaticRetryCounts.getOrDefault(key, 0)
        return if (
            allowAutoRetry &&
            retries < MAX_AUTOMATIC_RETRIES &&
            RouteGeometryFailurePolicy.shouldAutoRetry(throwable)
        ) {
            automaticRetryCounts[key] = retries + 1
            candidates.remove(key)
            states[key] = RouteGeometryLoadState.LOADING
            RouteGeometryRetryDecision.AUTO_RETRY
        } else {
            candidates.remove(key)
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

    fun loadingCount(): Int = states.values.count {
        it == RouteGeometryLoadState.LOADING || it == RouteGeometryLoadState.CANDIDATE
    }

    private companion object {
        const val MAX_AUTOMATIC_RETRIES = 1
    }
}
