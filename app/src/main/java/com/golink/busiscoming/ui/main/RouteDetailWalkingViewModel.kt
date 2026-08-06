package com.golink.busiscoming.ui.main

import androidx.lifecycle.ViewModel
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pStopMap
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailWalkingState
import com.golink.busiscoming.data.repository.PedestrianRequestTrigger
import com.golink.busiscoming.data.repository.PedestrianRouteRequestRuntime
import com.golink.busiscoming.data.repository.RouteDetailWalkingSession
import java.util.concurrent.Executors

data class RouteDetailWalkingSnapshot(
    val sessionGeneration: Int,
    val segments: Map<String, RouteDetailWalkingState>
)

class RouteDetailWalkingViewModel : ViewModel() {
    private val lock = Any()
    private val sourceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "route-detail-walking-source").apply { isDaemon = true }
    }
    private var query: P2pRouteDetailQuery? = null
    private var runtime: PedestrianRouteRequestRuntime? = null
    private var stopMap: P2pStopMap? = null
    private var stopMapLoaded = false
    private var detail: RouteDetail? = null
    private var sessionDetail: RouteDetail? = null
    private var session: RouteDetailWalkingSession? = null
    private var generation = 0
    private var latestSnapshot: RouteDetailWalkingSnapshot? = null
    private var observer: ((RouteDetailWalkingSnapshot) -> Unit)? = null

    fun initialize(
        query: P2pRouteDetailQuery,
        stopMapLoader: (P2pRouteDetailQuery) -> P2pStopMap?,
        runtime: PedestrianRouteRequestRuntime
    ) {
        synchronized(lock) {
            if (this.query != null) return
            this.query = query
            this.runtime = runtime
        }
        sourceExecutor.execute {
            val resolved = runCatching { stopMapLoader(query) }.getOrNull()
            synchronized(lock) {
                stopMap = resolved
                stopMapLoaded = true
                restartLocked(PedestrianRequestTrigger.REENTRY, force = false)
            }
        }
    }

    fun updateDetail(value: RouteDetail) {
        synchronized(lock) {
            detail = value
            restartLocked(PedestrianRequestTrigger.REENTRY, force = false)
        }
    }

    fun retry() {
        synchronized(lock) {
            restartLocked(PedestrianRequestTrigger.MANUAL, force = true)
        }
    }

    fun observe(value: (RouteDetailWalkingSnapshot) -> Unit) {
        val latest = synchronized(lock) {
            observer = value
            latestSnapshot
        }
        latest?.let(value)
    }

    fun clearObserver(value: (RouteDetailWalkingSnapshot) -> Unit) {
        synchronized(lock) {
            if (observer === value) observer = null
        }
    }

    private fun restartLocked(trigger: PedestrianRequestTrigger, force: Boolean) {
        val currentQuery = query ?: return
        val currentRuntime = runtime ?: return
        val currentDetail = detail ?: return
        if (!stopMapLoaded || (!force && currentDetail == sessionDetail)) return
        session?.close()
        generation += 1
        val sessionGeneration = generation
        sessionDetail = currentDetail
        latestSnapshot = null
        session = RouteDetailWalkingSession(
            query = currentQuery,
            stopMap = stopMap,
            detail = currentDetail,
            pedestrianRuntime = currentRuntime,
            trigger = trigger
        ) { segments -> publish(sessionGeneration, segments) }.also(RouteDetailWalkingSession::start)
    }

    private fun publish(sessionGeneration: Int, segments: Map<String, RouteDetailWalkingState>) {
        val target: ((RouteDetailWalkingSnapshot) -> Unit)?
        val snapshot: RouteDetailWalkingSnapshot
        synchronized(lock) {
            if (sessionGeneration != generation) return
            snapshot = RouteDetailWalkingSnapshot(sessionGeneration, segments)
            latestSnapshot = snapshot
            target = observer
        }
        target?.invoke(snapshot)
    }

    override fun onCleared() {
        synchronized(lock) {
            observer = null
            session?.close()
            session = null
        }
        sourceExecutor.shutdownNow()
    }
}
