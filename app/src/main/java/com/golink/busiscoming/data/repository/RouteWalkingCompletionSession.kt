package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pStopMap
import com.golink.busiscoming.data.model.PedestrianRoute
import com.golink.busiscoming.data.model.PedestrianRouteRounding
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.WalkingDistanceDisplayState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

class RouteWalkingCompletionSession(
    routes: List<BusRouteOption>,
    private val stopMapLoader: (P2pRouteDetailQuery) -> P2pStopMap?,
    private val detailLoader: (BusRouteOption) -> RouteDetail,
    private val pedestrianRuntime: PedestrianRouteRequestRuntime,
    private val sourceExecutor: ExecutorService,
    private val trigger: PedestrianRequestTrigger = PedestrianRequestTrigger.INITIAL,
    private val onUpdate: (String, WalkingDistanceDisplayState) -> Unit
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val candidates = routes.mapNotNull { route ->
        route.routeDetailQuery
            ?.takeIf { it.recoveryContext != null && it.plan.legs.isNotEmpty() }
            ?.let { Candidate(route, it) }
    }
    private val sourceTasks = mutableListOf<Future<*>>()

    fun start() {
        if (closed.get()) return
        candidates.forEach { candidate ->
            sourceTasks += sourceExecutor.submit {
                val stopMap = runCatching { stopMapLoader(candidate.query) }.getOrNull()
                candidate.onStopMapLoaded(stopMap)
            }
            sourceTasks += sourceExecutor.submit {
                val detail = runCatching { detailLoader(candidate.route) }.getOrNull()
                candidate.onDetailLoaded(detail)
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(sourceTasks) { sourceTasks.forEach { it.cancel(true) } }
        candidates.forEach(Candidate::close)
    }

    private inner class Candidate(
        val route: BusRouteOption,
        val query: P2pRouteDetailQuery
    ) {
        private var stopMap: P2pStopMap? = null
        private var detail: RouteDetail? = null
        private var stopMapFinished = false
        private var detailFinished = false
        private var terminal = false
        private var latestPlan: PlannedPedestrianRoute? = null
        private val successfulSegments = mutableMapOf<PedestrianSegmentId, SegmentSuccess>()
        private val subscriptions = mutableMapOf<PedestrianSegmentId, SegmentSubscription>()

        fun onStopMapLoaded(value: P2pStopMap?) = synchronized(this) {
            if (closed.get() || terminal) return@synchronized
            stopMap = value
            stopMapFinished = true
            if (value == null) {
                publishFallback()
            } else {
                replan()
            }
        }

        fun onDetailLoaded(value: RouteDetail?) = synchronized(this) {
            if (closed.get() || terminal) return@synchronized
            detail = value
            detailFinished = true
            if (value == null && query.plan.legs.size > 1) {
                publishFallback()
            } else if (stopMapFinished && stopMap != null) {
                replan()
            }
        }

        fun close() = synchronized(this) {
            terminal = true
            closeSubscriptions()
        }

        private fun replan() {
            if (terminal || closed.get()) return
            val plan = PedestrianSegmentPlanner.plan(query, stopMap, detail)
            latestPlan = plan
            pedestrianRuntime.rememberCombination(plan)
            val validIds = plan.segments.mapTo(hashSetOf()) { it.id }
            subscriptions.keys.filterNot(validIds::contains).forEach { id ->
                subscriptions.remove(id)?.handle?.close()
                successfulSegments.remove(id)
            }
            val unavailable = plan.segments.filterIsInstance<PedestrianSegment.Unavailable>().firstOrNull()
            if (unavailable != null) {
                publishFallback()
                return
            }
            plan.segments.filterIsInstance<PedestrianSegment.Requestable>().forEach { segment ->
                if (terminal || closed.get()) return@forEach
                val existing = subscriptions[segment.id]
                if (existing?.requestKey == segment.request.key) return@forEach
                existing?.handle?.close()
                successfulSegments.remove(segment.id)
                val handle = pedestrianRuntime.subscribe(
                    request = segment.request,
                    priority = PedestrianRequestPriority.CARD,
                    trigger = trigger
                ) { response ->
                    onSegmentResponse(segment, response)
                }
                if (terminal || closed.get()) {
                    handle.close()
                } else {
                    subscriptions[segment.id] = SegmentSubscription(segment.request.key, handle)
                }
            }
            publishSuccessIfComplete()
        }

        private fun onSegmentResponse(
            segment: PedestrianSegment.Requestable,
            response: CsdiPedestrianResponse
        ) = synchronized(this) {
            if (terminal || closed.get()) return@synchronized
            when (response) {
                is CsdiPedestrianResponse.Success -> {
                    successfulSegments[segment.id] = SegmentSuccess(segment.request.key, response.route)
                    publishSuccessIfComplete()
                }
                is CsdiPedestrianResponse.Failure -> publishFallback()
            }
        }

        private fun publishSuccessIfComplete() {
            if (terminal || closed.get()) return
            val plan = latestPlan ?: return
            val structureComplete = query.plan.legs.size == 1 || (detailFinished && detail != null)
            if (!structureComplete) return
            val required = plan.segments.filterIsInstance<PedestrianSegment.Requestable>()
            if (required.isEmpty()) return
            val routes = required.map { segment ->
                successfulSegments[segment.id]
                    ?.takeIf { it.requestKey == segment.request.key }
                    ?.route
                    ?: return
            }
            terminal = true
            closeSubscriptions()
            onUpdate(
                route.resultId,
                WalkingDistanceDisplayState.CsdiSuccess(
                    PedestrianRouteRounding.totalDistanceMeters(routes.map { it.rawDistanceMeters })
                )
            )
        }

        private fun publishFallback() {
            if (terminal || closed.get()) return
            terminal = true
            closeSubscriptions()
            onUpdate(
                route.resultId,
                WalkingDistanceDisplayState.CitybusFallback(route.walkingDistanceMeters)
            )
        }

        private fun closeSubscriptions() {
            subscriptions.values.forEach { it.handle.close() }
            subscriptions.clear()
        }
    }

    private data class SegmentSuccess(
        val requestKey: CsdiPedestrianRequestKey,
        val route: PedestrianRoute
    )

    private data class SegmentSubscription(
        val requestKey: CsdiPedestrianRequestKey,
        val handle: PedestrianSubscription
    )
}
