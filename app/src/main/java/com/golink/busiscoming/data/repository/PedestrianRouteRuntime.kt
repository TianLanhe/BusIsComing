package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.PedestrianRoute
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class PedestrianCancellationToken {
    private val cancelled = AtomicBoolean(false)
    private val actions = mutableListOf<() -> Unit>()

    val isCancelled: Boolean
        get() = cancelled.get()

    fun onCancel(action: () -> Unit) {
        val runImmediately = synchronized(actions) {
            if (cancelled.get()) true else false.also { actions += action }
        }
        if (runImmediately) runCatching(action)
    }

    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        val pending = synchronized(actions) {
            actions.toList().also { actions.clear() }
        }
        pending.forEach { runCatching(it) }
    }
}

enum class PedestrianRequestPriority(val rank: Int) {
    DETAIL(0),
    CARD(1)
}

enum class PedestrianRequestTrigger {
    INITIAL,
    MANUAL,
    REENTRY,
    AUTOMATIC
}

data class PedestrianRuntimeDiagnosticEvent(
    val flightId: Long,
    val action: String,
    val priority: PedestrianRequestPriority? = null,
    val attempt: Int? = null,
    val failureKind: CsdiPedestrianFailureKind? = null
)

class PedestrianSubscription internal constructor(
    private val cancelAction: () -> Unit
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) cancelAction()
    }
}

interface PedestrianRouteRequestRuntime {
    fun subscribe(
        request: CsdiPedestrianRequest,
        priority: PedestrianRequestPriority = PedestrianRequestPriority.CARD,
        trigger: PedestrianRequestTrigger = PedestrianRequestTrigger.INITIAL,
        callback: (CsdiPedestrianResponse) -> Unit
    ): PedestrianSubscription

    fun rememberCombination(plan: PlannedPedestrianRoute) = Unit
}

data class PedestrianRouteCombinationDescriptor(
    val segmentId: PedestrianSegmentId,
    val role: PedestrianSegmentRole,
    val requestKey: CsdiPedestrianRequestKey?,
    val sameStop: Boolean
)

class PedestrianRouteRuntime(
    private val source: CsdiPedestrianRouteSource = HttpCsdiPedestrianRouteSource(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryDelay: (Long) -> Unit = Thread::sleep,
    private val successTtlMillis: Long = DEFAULT_SUCCESS_TTL_MILLIS,
    private val combinationTtlMillis: Long = DEFAULT_SUCCESS_TTL_MILLIS,
    maxConcurrentAttempts: Int = MAX_CONCURRENT_ATTEMPTS,
    private val maxQueuedFlights: Int = DEFAULT_MAX_QUEUED_FLIGHTS,
    private val diagnosticObserver: (PedestrianRuntimeDiagnosticEvent) -> Unit = {}
) : PedestrianRouteRequestRuntime, AutoCloseable {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val flightIds = AtomicLong(0L)
    private val subscriptionIds = AtomicLong(0L)
    private val queueSequence = AtomicLong(0L)
    private val queue = PriorityBlockingQueue<QueuedFlight>()
    private val inFlight = mutableMapOf<CsdiPedestrianRequestKey, Flight>()
    private val successCache = mutableMapOf<CsdiPedestrianRequestKey, CachedSuccess>()
    private val combinationCache = mutableMapOf<PedestrianRoutePlanKey, CachedCombination>()
    private val failureBackoff = mutableMapOf<CsdiPedestrianRequestKey, FailureEligibility>()
    private val workers = Executors.newFixedThreadPool(maxConcurrentAttempts) { runnable ->
        Thread(runnable, "csdi-pedestrian").apply { isDaemon = true }
    }

    init {
        require(maxConcurrentAttempts in 1..MAX_CONCURRENT_ATTEMPTS) {
            "CSDI concurrency must be between 1 and $MAX_CONCURRENT_ATTEMPTS"
        }
        require(maxQueuedFlights > 0) { "CSDI queue must be bounded to a positive size" }
        repeat(maxConcurrentAttempts) { workers.execute(::workerLoop) }
    }

    override fun subscribe(
        request: CsdiPedestrianRequest,
        priority: PedestrianRequestPriority,
        trigger: PedestrianRequestTrigger,
        callback: (CsdiPedestrianResponse) -> Unit
    ): PedestrianSubscription {
        if (closed.get()) {
            callback(CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.CANCELLED))
            return PedestrianSubscription {}
        }
        val subscriptionId = subscriptionIds.incrementAndGet()
        var immediate: CsdiPedestrianResponse? = null
        var flightForSubscription: Flight? = null
        synchronized(lock) {
            val cached = successCache.removeExpiredOrGet(request.key, clock())
            if (cached != null) {
                immediate = CsdiPedestrianResponse.Success(cached.route)
                record(PedestrianRuntimeDiagnosticEvent(0L, "cache_hit"))
            } else if (trigger == PedestrianRequestTrigger.AUTOMATIC && isBackedOff(request.key, clock())) {
                immediate = CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.BACKOFF)
                record(PedestrianRuntimeDiagnosticEvent(0L, "backoff", failureKind = CsdiPedestrianFailureKind.BACKOFF))
            } else {
                val existing = inFlight[request.key]
                if (existing != null) {
                    existing.subscribers[subscriptionId] = callback
                    flightForSubscription = existing
                    if (priority.rank < existing.priority.rank && existing.state == FlightState.QUEUED) {
                        queue.remove(existing.queuedFlight)
                        existing.priority = priority
                        existing.queuedFlight = queued(existing)
                        queue.put(existing.queuedFlight)
                        record(PedestrianRuntimeDiagnosticEvent(existing.id, "priority_promote", priority))
                    } else {
                        record(PedestrianRuntimeDiagnosticEvent(existing.id, "single_flight_join", existing.priority))
                    }
                } else if (queue.size >= maxQueuedFlights) {
                    immediate = CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.QUEUE_FULL)
                    record(PedestrianRuntimeDiagnosticEvent(0L, "queue_full", failureKind = CsdiPedestrianFailureKind.QUEUE_FULL))
                } else {
                    val flight = Flight(
                        id = flightIds.incrementAndGet(),
                        request = request,
                        priority = priority
                    )
                    flight.subscribers[subscriptionId] = callback
                    flight.queuedFlight = queued(flight)
                    inFlight[request.key] = flight
                    flightForSubscription = flight
                    queue.put(flight.queuedFlight)
                    record(PedestrianRuntimeDiagnosticEvent(flight.id, "queued", priority))
                }
            }
        }
        immediate?.let(callback)
        val subscribedFlight = flightForSubscription
        return if (subscribedFlight == null) {
            PedestrianSubscription {}
        } else {
            PedestrianSubscription { unsubscribe(subscribedFlight, subscriptionId) }
        }
    }

    override fun rememberCombination(plan: PlannedPedestrianRoute) {
        val descriptors = plan.segments.map { segment ->
            PedestrianRouteCombinationDescriptor(
                segmentId = segment.id,
                role = segment.role,
                requestKey = (segment as? PedestrianSegment.Requestable)?.request?.key,
                sameStop = segment is PedestrianSegment.SameStop
            )
        }
        synchronized(lock) {
            combinationCache[plan.key] = CachedCombination(descriptors, clock())
        }
    }

    fun combination(key: PedestrianRoutePlanKey): List<PedestrianRouteCombinationDescriptor>? =
        synchronized(lock) {
            val cached = combinationCache[key] ?: return@synchronized null
            if (clock() - cached.cachedAtMillis >= combinationTtlMillis) {
                combinationCache.remove(key)
                null
            } else {
                cached.descriptors
            }
        }

    fun cachedSuccess(key: CsdiPedestrianRequestKey): PedestrianRoute? = synchronized(lock) {
        successCache.removeExpiredOrGet(key, clock())?.route
    }

    internal fun clearSuccessForTest(key: CsdiPedestrianRequestKey) {
        synchronized(lock) { successCache.remove(key) }
    }

    private fun workerLoop() {
        while (!closed.get()) {
            val queued = try {
                queue.take()
            } catch (_: InterruptedException) {
                return
            }
            val flight = queued.flight
            val mayRun = synchronized(lock) {
                val current = inFlight[flight.request.key]
                if (current !== flight || flight.state != FlightState.QUEUED || flight.subscribers.isEmpty()) {
                    false
                } else {
                    flight.state = FlightState.RUNNING
                    true
                }
            }
            if (!mayRun) continue
            record(PedestrianRuntimeDiagnosticEvent(flight.id, "attempt", flight.priority, attempt = 1))
            var result = source.solve(flight.request, flight.cancellationToken)
            if (result.isTransientFailure() && !flight.cancellationToken.isCancelled) {
                record(
                    PedestrianRuntimeDiagnosticEvent(
                        flight.id,
                        "retry",
                        flight.priority,
                        attempt = 1,
                        failureKind = (result as CsdiPedestrianResponse.Failure).kind
                    )
                )
                retryDelay(RETRY_DELAY_MILLIS)
                if (!flight.cancellationToken.isCancelled) {
                    record(PedestrianRuntimeDiagnosticEvent(flight.id, "attempt", flight.priority, attempt = 2))
                    result = source.solve(flight.request, flight.cancellationToken)
                }
            }
            complete(flight, result)
        }
    }

    private fun complete(flight: Flight, result: CsdiPedestrianResponse) {
        val callbacks: List<(CsdiPedestrianResponse) -> Unit>
        synchronized(lock) {
            val current = inFlight[flight.request.key]
            if (current !== flight || flight.cancellationToken.isCancelled || flight.subscribers.isEmpty()) {
                return
            }
            inFlight.remove(flight.request.key)
            flight.state = FlightState.COMPLETE
            when (result) {
                is CsdiPedestrianResponse.Success -> {
                    successCache[flight.request.key] = CachedSuccess(result.route, clock())
                    failureBackoff.remove(flight.request.key)
                }
                is CsdiPedestrianResponse.Failure -> {
                    if (result.kind !in NON_RECORDABLE_FAILURES) recordFailure(flight.request.key, clock())
                }
            }
            callbacks = flight.subscribers.values.toList()
            flight.subscribers.clear()
        }
        record(
            PedestrianRuntimeDiagnosticEvent(
                flight.id,
                if (result is CsdiPedestrianResponse.Success) "success" else "failure",
                flight.priority,
                failureKind = (result as? CsdiPedestrianResponse.Failure)?.kind
            )
        )
        callbacks.forEach { callback -> runCatching { callback(result) } }
    }

    private fun unsubscribe(flight: Flight, subscriptionId: Long) {
        synchronized(lock) {
            if (flight.subscribers.remove(subscriptionId) == null || flight.subscribers.isNotEmpty()) return
            if (inFlight[flight.request.key] === flight) inFlight.remove(flight.request.key)
            if (flight.state == FlightState.QUEUED) queue.remove(flight.queuedFlight)
            flight.cancellationToken.cancel()
            flight.state = FlightState.CANCELLED
            record(PedestrianRuntimeDiagnosticEvent(flight.id, "cancel_last_consumer", flight.priority))
        }
    }

    private fun isBackedOff(key: CsdiPedestrianRequestKey, now: Long): Boolean =
        failureBackoff[key]?.nextAllowedAtMillis?.let { now < it } == true

    private fun recordFailure(key: CsdiPedestrianRequestKey, now: Long) {
        val previousCount = failureBackoff[key]?.consecutiveFailureCount ?: 0
        val count = previousCount + 1
        val multiplier = 1L shl minOf(count - 1, 3)
        val delay = minOf(INITIAL_BACKOFF_MILLIS * multiplier, MAX_BACKOFF_MILLIS)
        failureBackoff[key] = FailureEligibility(count, now + delay)
    }

    private fun MutableMap<CsdiPedestrianRequestKey, CachedSuccess>.removeExpiredOrGet(
        key: CsdiPedestrianRequestKey,
        now: Long
    ): CachedSuccess? {
        val cached = this[key] ?: return null
        if (now - cached.cachedAtMillis >= successTtlMillis) {
            remove(key)
            record(PedestrianRuntimeDiagnosticEvent(0L, "cache_expired"))
            return null
        }
        return cached
    }

    private fun queued(flight: Flight): QueuedFlight =
        QueuedFlight(flight, flight.priority.rank, queueSequence.incrementAndGet())

    private fun record(event: PedestrianRuntimeDiagnosticEvent) {
        runCatching { diagnosticObserver(event) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) {
            inFlight.values.forEach { it.cancellationToken.cancel() }
            inFlight.clear()
            queue.clear()
        }
        workers.shutdownNow()
    }

    private data class CachedSuccess(val route: PedestrianRoute, val cachedAtMillis: Long)
    private data class CachedCombination(
        val descriptors: List<PedestrianRouteCombinationDescriptor>,
        val cachedAtMillis: Long
    )
    private data class FailureEligibility(
        val consecutiveFailureCount: Int,
        val nextAllowedAtMillis: Long
    )

    private class Flight(
        val id: Long,
        val request: CsdiPedestrianRequest,
        var priority: PedestrianRequestPriority,
        val cancellationToken: PedestrianCancellationToken = PedestrianCancellationToken(),
        val subscribers: LinkedHashMap<Long, (CsdiPedestrianResponse) -> Unit> = linkedMapOf(),
        var state: FlightState = FlightState.QUEUED
    ) {
        lateinit var queuedFlight: QueuedFlight
    }

    private data class QueuedFlight(
        val flight: Flight,
        val priorityRank: Int,
        val sequence: Long
    ) : Comparable<QueuedFlight> {
        override fun compareTo(other: QueuedFlight): Int =
            compareValuesBy(this, other, QueuedFlight::priorityRank, QueuedFlight::sequence)
    }

    private enum class FlightState {
        QUEUED,
        RUNNING,
        COMPLETE,
        CANCELLED
    }

    private fun CsdiPedestrianResponse.isTransientFailure(): Boolean =
        this is CsdiPedestrianResponse.Failure && kind in TRANSIENT_FAILURES

    private companion object {
        const val MAX_CONCURRENT_ATTEMPTS = 5
        const val DEFAULT_MAX_QUEUED_FLIGHTS = 128
        const val DEFAULT_SUCCESS_TTL_MILLIS = 86_400_000L
        const val RETRY_DELAY_MILLIS = 300L
        const val INITIAL_BACKOFF_MILLIS = 5 * 60_000L
        const val MAX_BACKOFF_MILLIS = 30 * 60_000L
        val TRANSIENT_FAILURES = setOf(
            CsdiPedestrianFailureKind.NETWORK,
            CsdiPedestrianFailureKind.TIMEOUT,
            CsdiPedestrianFailureKind.HTTP_5XX
        )
        val NON_RECORDABLE_FAILURES = setOf(
            CsdiPedestrianFailureKind.CANCELLED,
            CsdiPedestrianFailureKind.BACKOFF,
            CsdiPedestrianFailureKind.QUEUE_FULL
        )
    }
}

object PedestrianRouteProcessRuntime {
    val shared: PedestrianRouteRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PedestrianRouteRuntime()
    }
}
