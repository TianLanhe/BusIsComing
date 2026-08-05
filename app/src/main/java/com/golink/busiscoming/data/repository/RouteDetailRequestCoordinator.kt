package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteRecoveryContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

fun interface SingleFlightRequestHandle {
    fun cancel()
}

class SingleFlightRequestCoordinator<K : Any, V>(
    private val executor: ExecutorService
) {
    private val lock = Any()
    private val nextConsumerId = AtomicLong()
    private val flights = mutableMapOf<K, Flight>()

    fun request(
        key: K,
        work: () -> V,
        callback: (Result<V>) -> Unit
    ): SingleFlightRequestHandle {
        val consumerId = nextConsumerId.incrementAndGet()
        val flight: Flight
        var shouldStart = false
        synchronized(lock) {
            flight = flights[key] ?: Flight().also { created ->
                flights[key] = created
                shouldStart = true
            }
            flight.consumers[consumerId] = callback
        }
        RouteDetailDiagnostics.record(
            RouteDetailDiagnosticEvent(
                category = "single_flight",
                action = if (shouldStart) "start" else "join",
                safeKeyHash = RouteDetailDiagnostics.safeHash(key)
            )
        )

        if (shouldStart) {
            val future = executor.submit {
                complete(key, flight, runCatching(work))
            }
            var cancelFuture = false
            synchronized(lock) {
                flight.future = future
                cancelFuture = flight.cancelled
            }
            if (cancelFuture) future.cancel(true)
        }

        return SingleFlightRequestHandle {
            var futureToCancel: Future<*>? = null
            var lastConsumerCancelled = false
            synchronized(lock) {
                if (flight.completed) return@SingleFlightRequestHandle
                flight.consumers.remove(consumerId)
                if (flight.consumers.isEmpty()) {
                    flight.cancelled = true
                    if (flights[key] === flight) flights.remove(key)
                    futureToCancel = flight.future
                    lastConsumerCancelled = true
                }
            }
            if (lastConsumerCancelled) {
                RouteDetailDiagnostics.record(
                    RouteDetailDiagnosticEvent(
                        category = "single_flight",
                        action = "last_consumer_cancelled",
                        safeKeyHash = RouteDetailDiagnostics.safeHash(key)
                    )
                )
            }
            futureToCancel?.cancel(true)
        }
    }

    private fun complete(key: K, flight: Flight, result: Result<V>) {
        val callbacks: List<(Result<V>) -> Unit>
        synchronized(lock) {
            if (flight.completed) return
            flight.completed = true
            if (flights[key] === flight) flights.remove(key)
            callbacks = flight.consumers.values.toList()
            flight.consumers.clear()
        }
        callbacks.forEach { callback -> callback(result) }
        RouteDetailDiagnostics.record(
            RouteDetailDiagnosticEvent(
                category = "single_flight",
                action = if (result.isSuccess) "success" else "failure",
                safeKeyHash = RouteDetailDiagnostics.safeHash(key),
                reason = result.exceptionOrNull()?.javaClass?.simpleName
            )
        )
    }

    private inner class Flight {
        val consumers = linkedMapOf<Long, (Result<V>) -> Unit>()
        var future: Future<*>? = null
        var cancelled: Boolean = false
        var completed: Boolean = false
    }
}

class RouteDetailRequestIdentity private constructor(
    private val value: Value
) {
    override fun equals(other: Any?): Boolean =
        other is RouteDetailRequestIdentity && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String {
        return "RouteDetailRequestIdentity(hash=${hashCode().toUInt().toString(16)})"
    }

    companion object {
        fun from(query: P2pRouteDetailQuery): RouteDetailRequestIdentity {
            return RouteDetailRequestIdentity(
                Value(
                    rawInfo = query.rawInfo,
                    generalInfo = query.generalInfo,
                    listId = query.listId,
                    language = query.lang,
                    planFingerprint = query.plan.fingerprint(),
                    recoveryContext = query.recoveryContext,
                    sessionReference = query.sessionRef
                )
            )
        }
    }

    private data class Value(
        val rawInfo: String,
        val generalInfo: String,
        val listId: String,
        val language: String,
        val planFingerprint: String,
        val recoveryContext: P2pRouteRecoveryContext?,
        val sessionReference: String?
    )
}
