package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.data.model.RouteGeometrySegment
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.model.RouteDetailWalkingState
import com.golink.busiscoming.data.repository.RouteDetailDiagnosticEvent
import com.golink.busiscoming.data.repository.RouteDetailDiagnostics

sealed interface ProgressiveValue<out T> {
    data object Idle : ProgressiveValue<Nothing>
    data object Loading : ProgressiveValue<Nothing>
    data class Success<T>(val value: T) : ProgressiveValue<T>
    data class Refreshing<T>(val previous: T) : ProgressiveValue<T>
    data class Failure<T>(val previous: T?, val reason: String) : ProgressiveValue<T>

    fun valueOrNull(): T? = when (this) {
        Idle, Loading -> null
        is Success -> value
        is Refreshing -> previous
        is Failure -> previous
    }
}

enum class RouteDetailCameraOwner {
    PAGE,
    USER
}

data class RouteDetailCameraSnapshot(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float,
    val bearing: Float = 0f,
    val tilt: Float = 0f
)

data class RouteDetailInteractionState(
    val expandedLegIndexes: Set<Int> = emptySet(),
    val selectedMarkerId: String? = null,
    val selectedTimelineId: String? = null,
    val firstVisibleListPosition: Int = 0,
    val firstVisibleListOffset: Int = 0,
    val cameraSnapshot: RouteDetailCameraSnapshot? = null,
    val cameraOwner: RouteDetailCameraOwner = RouteDetailCameraOwner.PAGE,
    val initialFitDone: Boolean = false
)

data class RouteDetailPageState(
    val pageGeneration: Long,
    val detailGeneration: Int = 0,
    val detail: ProgressiveValue<RouteDetail> = ProgressiveValue.Idle,
    val etaGeneration: Int = 0,
    val eta: ProgressiveValue<WaitTimeState> = ProgressiveValue.Idle,
    val mapGeneration: Int = 0,
    val map: ProgressiveValue<Unit> = ProgressiveValue.Idle,
    val walkingGeneration: Int = 0,
    val walkingSegments: Map<String, RouteDetailWalkingState> = emptyMap(),
    val geometryGenerations: Map<RouteGeometryKey, Int>,
    val geometries: Map<RouteGeometryKey, ProgressiveValue<RouteGeometrySegment>>,
    val interaction: RouteDetailInteractionState = RouteDetailInteractionState(),
    val active: Boolean = true
) {
    val successfulGeometries: Map<RouteGeometryKey, RouteGeometrySegment>
        get() = geometries.mapNotNull { (key, state) ->
            state.valueOrNull()?.let { key to it }
        }.toMap()

    companion object {
        fun initial(
            pageGeneration: Long,
            expectedGeometryKeys: Set<RouteGeometryKey>
        ): RouteDetailPageState = RouteDetailPageState(
            pageGeneration = pageGeneration,
            geometryGenerations = expectedGeometryKeys.associateWith { 0 },
            geometries = expectedGeometryKeys.associateWith { ProgressiveValue.Idle }
        )
    }
}

sealed interface RouteDetailPageEvent {
    val pageGeneration: Long

    data class DetailCacheAvailable(
        override val pageGeneration: Long,
        val detail: RouteDetail
    ) : RouteDetailPageEvent

    data class DetailStarted(override val pageGeneration: Long, val generation: Int) : RouteDetailPageEvent
    data class DetailSucceeded(
        override val pageGeneration: Long,
        val generation: Int,
        val detail: RouteDetail
    ) : RouteDetailPageEvent
    data class DetailFailed(
        override val pageGeneration: Long,
        val generation: Int,
        val reason: String
    ) : RouteDetailPageEvent

    data class EtaStarted(override val pageGeneration: Long, val generation: Int) : RouteDetailPageEvent
    data class EtaSucceeded(
        override val pageGeneration: Long,
        val generation: Int,
        val state: WaitTimeState
    ) : RouteDetailPageEvent
    data class EtaFailed(
        override val pageGeneration: Long,
        val generation: Int,
        val reason: String
    ) : RouteDetailPageEvent

    data class MapReady(override val pageGeneration: Long, val generation: Int) : RouteDetailPageEvent
    data class MapFailed(
        override val pageGeneration: Long,
        val generation: Int,
        val reason: String
    ) : RouteDetailPageEvent

    data class GeometryStarted(
        override val pageGeneration: Long,
        val key: RouteGeometryKey,
        val generation: Int
    ) : RouteDetailPageEvent
    data class GeometrySucceeded(
        override val pageGeneration: Long,
        val key: RouteGeometryKey,
        val generation: Int,
        val geometry: RouteGeometrySegment
    ) : RouteDetailPageEvent
    data class GeometryFailed(
        override val pageGeneration: Long,
        val key: RouteGeometryKey,
        val generation: Int,
        val reason: String
    ) : RouteDetailPageEvent

    data class WalkingStarted(
        override val pageGeneration: Long,
        val generation: Int,
        val initialSegments: Map<String, RouteDetailWalkingState>
    ) : RouteDetailPageEvent

    data class WalkingSegmentChanged(
        override val pageGeneration: Long,
        val generation: Int,
        val segmentId: String,
        val state: RouteDetailWalkingState
    ) : RouteDetailPageEvent

    data class InteractionChanged(
        override val pageGeneration: Long,
        val interaction: RouteDetailInteractionState
    ) : RouteDetailPageEvent

    data class Destroyed(override val pageGeneration: Long) : RouteDetailPageEvent
}

object RouteDetailPageReducer {
    fun reduce(state: RouteDetailPageState, event: RouteDetailPageEvent): RouteDetailPageState {
        if (!state.active || event.pageGeneration != state.pageGeneration) {
            RouteDetailDiagnostics.record(
                RouteDetailDiagnosticEvent(
                    category = "reducer",
                    action = "stale_page_rejected",
                    safeKeyHash = RouteDetailDiagnostics.safeHash(event.pageGeneration)
                )
            )
            return state
        }
        return when (event) {
            is RouteDetailPageEvent.DetailCacheAvailable -> reduceDetailCache(state, event.detail)
            is RouteDetailPageEvent.DetailStarted -> {
                if (event.generation <= state.detailGeneration) state else state.copy(
                    detailGeneration = event.generation,
                    detail = state.detail.asLoading()
                )
            }
            is RouteDetailPageEvent.DetailSucceeded -> {
                if (event.generation != state.detailGeneration) {
                    recordStaleDomain("detail", event.generation)
                    state
                } else state.copy(detail = ProgressiveValue.Success(event.detail))
            }
            is RouteDetailPageEvent.DetailFailed -> {
                if (event.generation != state.detailGeneration || state.detail is ProgressiveValue.Success) state else state.copy(
                    detail = ProgressiveValue.Failure(state.detail.valueOrNull(), event.reason)
                )
            }
            is RouteDetailPageEvent.EtaStarted -> {
                if (event.generation <= state.etaGeneration) state else state.copy(
                    etaGeneration = event.generation,
                    eta = state.eta.asLoading()
                )
            }
            is RouteDetailPageEvent.EtaSucceeded -> {
                if (event.generation != state.etaGeneration) state else state.copy(
                    eta = ProgressiveValue.Success(event.state)
                )
            }
            is RouteDetailPageEvent.EtaFailed -> {
                if (event.generation != state.etaGeneration || state.eta is ProgressiveValue.Success) state else state.copy(
                    eta = ProgressiveValue.Failure(state.eta.valueOrNull(), event.reason)
                )
            }
            is RouteDetailPageEvent.MapReady -> {
                if (event.generation < state.mapGeneration) state else state.copy(
                    mapGeneration = event.generation,
                    map = ProgressiveValue.Success(Unit)
                )
            }
            is RouteDetailPageEvent.MapFailed -> {
                if (event.generation != state.mapGeneration || state.map is ProgressiveValue.Success) state else state.copy(
                    map = ProgressiveValue.Failure(state.map.valueOrNull(), event.reason)
                )
            }
            is RouteDetailPageEvent.GeometryStarted -> reduceGeometryStarted(state, event)
            is RouteDetailPageEvent.GeometrySucceeded -> reduceGeometrySucceeded(state, event)
            is RouteDetailPageEvent.GeometryFailed -> reduceGeometryFailed(state, event)
            is RouteDetailPageEvent.WalkingStarted -> {
                if (event.generation <= state.walkingGeneration) state else state.copy(
                    walkingGeneration = event.generation,
                    walkingSegments = event.initialSegments
                )
            }
            is RouteDetailPageEvent.WalkingSegmentChanged -> {
                if (event.generation != state.walkingGeneration ||
                    event.segmentId !in state.walkingSegments
                ) {
                    state
                } else {
                    state.copy(
                        walkingSegments = state.walkingSegments + (event.segmentId to event.state)
                    )
                }
            }
            is RouteDetailPageEvent.InteractionChanged -> state.copy(interaction = event.interaction)
            is RouteDetailPageEvent.Destroyed -> state.copy(active = false)
        }
    }

    private fun recordStaleDomain(domain: String, generation: Int) {
        RouteDetailDiagnostics.record(
            RouteDetailDiagnosticEvent(
                category = "reducer",
                action = "stale_domain_rejected",
                generation = generation,
                reason = domain
            )
        )
    }

    private fun reduceDetailCache(state: RouteDetailPageState, detail: RouteDetail): RouteDetailPageState {
        val updated = when (state.detail) {
            ProgressiveValue.Idle -> ProgressiveValue.Success(detail)
            ProgressiveValue.Loading -> ProgressiveValue.Refreshing(detail)
            is ProgressiveValue.Failure -> ProgressiveValue.Failure(detail, state.detail.reason)
            is ProgressiveValue.Refreshing, is ProgressiveValue.Success -> return state
        }
        return state.copy(detail = updated)
    }

    private fun reduceGeometryStarted(
        state: RouteDetailPageState,
        event: RouteDetailPageEvent.GeometryStarted
    ): RouteDetailPageState {
        val currentGeneration = state.geometryGenerations[event.key] ?: return state
        if (event.generation <= currentGeneration) return state
        val current = state.geometries.getValue(event.key)
        return state.copy(
            geometryGenerations = state.geometryGenerations + (event.key to event.generation),
            geometries = state.geometries + (event.key to current.asLoading())
        )
    }

    private fun reduceGeometrySucceeded(
        state: RouteDetailPageState,
        event: RouteDetailPageEvent.GeometrySucceeded
    ): RouteDetailPageState {
        if (state.geometryGenerations[event.key] != event.generation) return state
        return state.copy(
            geometries = state.geometries + (event.key to ProgressiveValue.Success(event.geometry))
        )
    }

    private fun reduceGeometryFailed(
        state: RouteDetailPageState,
        event: RouteDetailPageEvent.GeometryFailed
    ): RouteDetailPageState {
        if (state.geometryGenerations[event.key] != event.generation) return state
        val current = state.geometries.getValue(event.key)
        if (current is ProgressiveValue.Success) return state
        return state.copy(
            geometries = state.geometries +
                (event.key to ProgressiveValue.Failure(current.valueOrNull(), event.reason))
        )
    }

    private fun <T> ProgressiveValue<T>.asLoading(): ProgressiveValue<T> {
        return valueOrNull()?.let(ProgressiveValue<T>::Refreshing) ?: ProgressiveValue.Loading
    }
}
