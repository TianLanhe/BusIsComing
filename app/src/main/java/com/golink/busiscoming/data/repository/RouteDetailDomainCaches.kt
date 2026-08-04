package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.ParsedRouteDetail
import com.golink.busiscoming.data.model.RouteDetailCompleteness
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailTransfer
import com.golink.busiscoming.data.model.RouteDetailWalkingSegment

data class RouteStructureCacheKey(
    val planFingerprint: String,
    val language: String
)

data class WalkingDistanceCacheKey(
    val endpointContext: String,
    val planFingerprint: String
)

data class RouteStructureSnapshot(
    val legs: List<RouteDetailLeg>,
    val originName: String?,
    val destinationName: String?
)

data class WalkingDistanceSnapshot(
    val originWalking: RouteDetailWalkingSegment,
    val transfers: List<RouteDetailTransfer>,
    val destinationWalking: RouteDetailWalkingSegment
)

class RouteStructureCache(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = RouteDetailCache.DEFAULT_TTL_MILLIS
) {
    private val entries = mutableMapOf<RouteStructureCacheKey, TimedValue<RouteStructureSnapshot>>()

    fun get(key: RouteStructureCacheKey): RouteStructureSnapshot? = synchronized(entries) {
        entries.readValid(key, clock(), ttlMillis)
    }

    fun put(key: RouteStructureCacheKey, detail: ParsedRouteDetail) {
        if (detail.legs.isEmpty()) return
        synchronized(entries) {
            entries[key] = TimedValue(
                RouteStructureSnapshot(detail.legs, detail.originName, detail.destinationName),
                clock()
            )
        }
    }
}

class WalkingDistanceCache(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = RouteDetailCache.DEFAULT_TTL_MILLIS
) {
    private val entries = mutableMapOf<WalkingDistanceCacheKey, TimedValue<WalkingDistanceSnapshot>>()

    fun get(key: WalkingDistanceCacheKey): WalkingDistanceSnapshot? = synchronized(entries) {
        entries.readValid(key, clock(), ttlMillis)
    }

    fun put(key: WalkingDistanceCacheKey, detail: ParsedRouteDetail) {
        if (detail.completeness != RouteDetailCompleteness.COMPLETE) return
        val origin = detail.originWalking?.takeIf { it.distanceMeters != null } ?: return
        val destination = detail.destinationWalking?.takeIf { it.distanceMeters != null } ?: return
        synchronized(entries) {
            entries[key] = TimedValue(
                WalkingDistanceSnapshot(origin, detail.transfers, destination),
                clock()
            )
        }
    }
}

fun P2pRouteDetailQuery.structureCacheKey(): RouteStructureCacheKey {
    return RouteStructureCacheKey(plan.fingerprint(), lang)
}

fun P2pRouteDetailQuery.walkingDistanceCacheKey(): WalkingDistanceCacheKey? {
    val context = recoveryContext ?: return null
    return WalkingDistanceCacheKey(context.walkingContextKey(), plan.fingerprint())
}

private data class TimedValue<T>(val value: T, val cachedAtMillis: Long)

private fun <K, V> MutableMap<K, TimedValue<V>>.readValid(
    key: K,
    now: Long,
    ttlMillis: Long
): V? {
    val entry = this[key] ?: return null
    if (now - entry.cachedAtMillis >= ttlMillis) {
        remove(key)
        return null
    }
    return entry.value
}
