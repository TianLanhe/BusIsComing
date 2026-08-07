package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.localization.AppLanguageRuntime
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.model.WalkingDistanceDisplayState
import com.golink.busiscoming.data.repository.BusRouteQueryCallback
import com.golink.busiscoming.data.repository.BusRouteRepository
import com.golink.busiscoming.data.repository.PedestrianRequestTrigger
import java.util.concurrent.Executor

enum class RouteQueryTrigger(val walkingTrigger: PedestrianRequestTrigger) {
    INITIAL(PedestrianRequestTrigger.INITIAL),
    MANUAL(PedestrianRequestTrigger.MANUAL),
    AUTOMATIC(PedestrianRequestTrigger.AUTOMATIC)
}

enum class RouteQuerySessionPhase {
    IN_FLIGHT,
    BASE_AVAILABLE,
    FAILED
}

data class RouteQuerySessionSnapshot(
    val queryId: Int,
    val origin: Place,
    val destination: Place,
    val trigger: RouteQueryTrigger,
    val phase: RouteQuerySessionPhase,
    val routes: List<BusRouteOption>,
    val automaticBaselineAvailable: Boolean,
    val failure: Throwable? = null
) {
    val networkCycleInProgress: Boolean
        get() = phase == RouteQuerySessionPhase.IN_FLIGHT
}

/**
 * Retains one logical route query independently from any Activity or Fragment view.
 *
 * Raw domain snapshots are replayed to a replacement observer after configuration changes. The
 * repository and its progressive CSDI subscriptions therefore remain alive until a genuinely new
 * query, explicit invalidation, or the owning ViewModel is cleared.
 */
class RouteQuerySession(
    private val repository: BusRouteRepository,
    private val executor: Executor,
    private val dispatch: (Runnable) -> Unit,
    private val languageVersion: () -> Long = { AppLanguageRuntime.snapshot().version }
) : AutoCloseable {
    private val lock = Any()
    private var generation = 0
    private var closed = false
    private var observer: ((RouteQuerySessionSnapshot) -> Unit)? = null
    private var snapshot: RouteQuerySessionSnapshot? = null
    private var automaticBaselineAvailable = false
    private var activeQueryLanguageVersion: Long? = null

    val latestSnapshot: RouteQuerySessionSnapshot?
        get() = synchronized(lock) { snapshot }

    fun observe(value: (RouteQuerySessionSnapshot) -> Unit) {
        val latest = synchronized(lock) {
            check(!closed) { "Route query session is closed" }
            observer = value
            snapshot
        }
        latest?.let(value)
    }

    fun clearObserver(value: (RouteQuerySessionSnapshot) -> Unit) {
        synchronized(lock) {
            if (observer === value) observer = null
        }
    }

    /** Returns a query id, or null when another base query is active or no baseline exists. */
    fun start(origin: Place, destination: Place, trigger: RouteQueryTrigger): Int? {
        val queryId: Int
        val queryLanguageVersion: Long
        val initialSnapshot: RouteQuerySessionSnapshot
        synchronized(lock) {
            check(!closed) { "Route query session is closed" }
            if (snapshot?.networkCycleInProgress == true) return null
            if (trigger == RouteQueryTrigger.AUTOMATIC && !automaticBaselineAvailable) return null
            generation += 1
            queryId = generation
            queryLanguageVersion = languageVersion()
            activeQueryLanguageVersion = queryLanguageVersion
            initialSnapshot = RouteQuerySessionSnapshot(
                queryId = queryId,
                origin = origin,
                destination = destination,
                trigger = trigger,
                phase = RouteQuerySessionPhase.IN_FLIGHT,
                routes = if (trigger == RouteQueryTrigger.INITIAL) emptyList() else snapshot?.routes.orEmpty(),
                automaticBaselineAvailable = automaticBaselineAvailable
            )
            snapshot = initialSnapshot
        }
        repository.cancelProgressiveQueries()
        publish(initialSnapshot)
        executor.execute {
            repository.searchRoutesProgressively(
                origin = origin,
                destination = destination,
                walkingTrigger = trigger.walkingTrigger,
                callback = callback(queryId, queryLanguageVersion)
            )
        }
        return queryId
    }

    /**
     * Replaces only an unfinished base request started under an obsolete language snapshot.
     * A base-available session is retained so its language-neutral walking subscription survives
     * configuration recreation and the replacement observer can reformat the raw state.
     */
    fun reconcileCurrentLanguage(): Int? {
        val queryId: Int
        val queryLanguageVersion: Long
        val nextSnapshot: RouteQuerySessionSnapshot
        synchronized(lock) {
            check(!closed) { "Route query session is closed" }
            val current = snapshot ?: return null
            queryLanguageVersion = languageVersion()
            if (
                current.phase != RouteQuerySessionPhase.IN_FLIGHT ||
                activeQueryLanguageVersion == queryLanguageVersion
            ) {
                return null
            }
            generation += 1
            queryId = generation
            activeQueryLanguageVersion = queryLanguageVersion
            nextSnapshot = current.copy(queryId = queryId, failure = null)
            snapshot = nextSnapshot
        }
        repository.cancelProgressiveQueries()
        publish(nextSnapshot)
        executor.execute {
            repository.searchRoutesProgressively(
                origin = nextSnapshot.origin,
                destination = nextSnapshot.destination,
                walkingTrigger = nextSnapshot.trigger.walkingTrigger,
                callback = callback(queryId, queryLanguageVersion)
            )
        }
        return queryId
    }

    fun invalidate(clearSnapshot: Boolean = true) {
        synchronized(lock) {
            generation += 1
            if (clearSnapshot) {
                snapshot = null
                automaticBaselineAvailable = false
                activeQueryLanguageVersion = null
            }
        }
        repository.cancelProgressiveQueries()
    }

    private fun callback(queryId: Int, queryLanguageVersion: Long) =
        object : BusRouteQueryCallback {
            override fun onInitialRoutes(routes: List<BusRouteOption>) {
                accept(queryId, queryLanguageVersion) { current ->
                    automaticBaselineAvailable = true
                    current.copy(
                        phase = RouteQuerySessionPhase.BASE_AVAILABLE,
                        routes = routes,
                        automaticBaselineAvailable = true,
                        failure = null
                    )
                }
            }

            override fun onRouteWaitTimeUpdated(routeId: String, waitTimeState: WaitTimeState) {
                updateRoute(queryId, queryLanguageVersion, routeId) {
                    it.copy(waitTimeState = waitTimeState)
                }
            }

            override fun onRouteStopPreviewUpdated(
                routeId: String,
                preview: RouteCardStopPreview
            ) {
                updateRoute(queryId, queryLanguageVersion, routeId) {
                    it.copy(stopPreview = preview)
                }
            }

            override fun onRouteWalkingDistanceUpdated(
                routeId: String,
                state: WalkingDistanceDisplayState
            ) {
                updateRoute(
                    queryId,
                    queryLanguageVersion,
                    routeId,
                    requireMatchingLanguage = false
                ) {
                    it.copy(walkingDistanceDisplayState = state)
                }
            }

            override fun onFailure(error: Throwable) {
                accept(queryId, queryLanguageVersion) { current ->
                    current.copy(
                        phase = RouteQuerySessionPhase.FAILED,
                        automaticBaselineAvailable = automaticBaselineAvailable,
                        failure = error
                    )
                }
            }
        }

    private fun updateRoute(
        queryId: Int,
        queryLanguageVersion: Long,
        routeId: String,
        requireMatchingLanguage: Boolean = true,
        transform: (BusRouteOption) -> BusRouteOption
    ) {
        accept(queryId, queryLanguageVersion, requireMatchingLanguage) { current ->
            var changed = false
            val routes = current.routes.map { route ->
                if (route.resultId == routeId) {
                    changed = true
                    transform(route)
                } else {
                    route
                }
            }
            if (changed) current.copy(routes = routes) else current
        }
    }

    private fun accept(
        queryId: Int,
        queryLanguageVersion: Long,
        requireMatchingLanguage: Boolean = true,
        reduce: (RouteQuerySessionSnapshot) -> RouteQuerySessionSnapshot
    ) {
        dispatch(Runnable {
            val next = synchronized(lock) {
                if (
                    closed ||
                    generation != queryId ||
                    (requireMatchingLanguage && languageVersion() != queryLanguageVersion)
                ) {
                    return@Runnable
                }
                val current = snapshot?.takeIf { it.queryId == queryId } ?: return@Runnable
                reduce(current).also { snapshot = it }
            }
            publish(next)
        })
    }

    private fun publish(value: RouteQuerySessionSnapshot) {
        val target = synchronized(lock) {
            if (snapshot?.queryId == value.queryId) observer else null
        }
        target?.invoke(value)
    }

    override fun close() {
        val shouldCancel = synchronized(lock) {
            if (closed) false else {
                closed = true
                generation += 1
                observer = null
                snapshot = null
                activeQueryLanguageVersion = null
                true
            }
        }
        if (shouldCancel) repository.cancelProgressiveQueries()
    }
}
