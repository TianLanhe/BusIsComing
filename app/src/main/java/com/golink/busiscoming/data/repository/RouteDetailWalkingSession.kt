package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pStopMap
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailWalkingState
import java.util.concurrent.atomic.AtomicBoolean

class RouteDetailWalkingSession(
    private val query: P2pRouteDetailQuery,
    private val stopMap: P2pStopMap?,
    private val detail: RouteDetail,
    private val pedestrianRuntime: PedestrianRouteRequestRuntime,
    private val trigger: PedestrianRequestTrigger = PedestrianRequestTrigger.REENTRY,
    private val onSnapshot: (Map<String, RouteDetailWalkingState>) -> Unit
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val states = linkedMapOf<String, RouteDetailWalkingState>()
    private val subscriptions = mutableMapOf<PedestrianSegmentId, PedestrianSubscription>()

    fun start() {
        if (!started.compareAndSet(false, true) || closed.get()) return
        val plan = PedestrianSegmentPlanner.plan(query, stopMap, detail)
        pedestrianRuntime.rememberCombination(plan)
        synchronized(this) {
            plan.segments.forEach { segment ->
                states[segment.id.value] = when (segment) {
                    is PedestrianSegment.Requestable -> RouteDetailWalkingState.Loading
                    is PedestrianSegment.SameStop -> RouteDetailWalkingState.SameStop
                    is PedestrianSegment.Unavailable -> RouteDetailWalkingState.CitybusFallback(
                        segment.citybusFallbackDistanceMeters
                    )
                }
            }
            publishLocked()
        }
        plan.segments.filterIsInstance<PedestrianSegment.Requestable>().forEach { segment ->
            if (closed.get()) return@forEach
            val handle = pedestrianRuntime.subscribe(
                request = segment.request,
                priority = PedestrianRequestPriority.DETAIL,
                trigger = trigger
            ) { response -> onResponse(segment, response) }
            synchronized(this) {
                if (closed.get()) handle.close() else subscriptions[segment.id] = handle
            }
        }
    }

    private fun onResponse(segment: PedestrianSegment.Requestable, response: CsdiPedestrianResponse) {
        synchronized(this) {
            if (closed.get() || segment.id.value !in states) return
            states[segment.id.value] = when (response) {
                is CsdiPedestrianResponse.Success -> RouteDetailWalkingState.CsdiSuccess(response.route)
                is CsdiPedestrianResponse.Failure -> RouteDetailWalkingState.CitybusFallback(
                    segment.citybusFallbackDistanceMeters
                )
            }
            publishLocked()
        }
    }

    private fun publishLocked() {
        if (!closed.get()) onSnapshot(LinkedHashMap(states))
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(this) {
            subscriptions.values.forEach(PedestrianSubscription::close)
            subscriptions.clear()
        }
    }
}
