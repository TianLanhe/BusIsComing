package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.ParsedRouteDetail
import com.golink.busiscoming.data.model.RouteDetailCompleteness
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailTransfer
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteDetailWalkingKind
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
    val destinationName: String?,
    val transferTypes: List<RouteDetailTransferType>
)

data class WalkingDistanceSnapshot(
    val originDistanceMeters: Int,
    val transferDistanceMeters: List<Int?>,
    val destinationDistanceMeters: Int
) {
    fun originWalking(): RouteDetailWalkingSegment =
        RouteDetailWalkingSegment(RouteDetailWalkingKind.ORIGIN, originDistanceMeters)

    fun transfers(types: List<RouteDetailTransferType>): List<RouteDetailTransfer> =
        types.mapIndexed { index, type ->
            RouteDetailTransfer(
                type = type,
                walking = if (type == RouteDetailTransferType.SAME_STOP) {
                    null
                } else {
                    RouteDetailWalkingSegment(
                        RouteDetailWalkingKind.TRANSFER,
                        transferDistanceMeters.getOrNull(index)
                    )
                }
            )
        }

    fun destinationWalking(): RouteDetailWalkingSegment =
        RouteDetailWalkingSegment(RouteDetailWalkingKind.DESTINATION, destinationDistanceMeters)
}

class RouteDetailCacheOwner(
    val structureCache: RouteStructureCache = RouteStructureCache(),
    val walkingCache: WalkingDistanceCache = WalkingDistanceCache()
)

class RouteStructureCache(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = DEFAULT_ROUTE_DETAIL_DOMAIN_TTL_MILLIS
) {
    private val entries = mutableMapOf<RouteStructureCacheKey, TimedValue<RouteStructureSnapshot>>()

    fun get(key: RouteStructureCacheKey): RouteStructureSnapshot? = read(
        entries,
        key,
        clock(),
        ttlMillis,
        category = "structure_cache"
    )

    fun put(key: RouteStructureCacheKey, plan: P2pRoutePlan, detail: ParsedRouteDetail): Boolean {
        if (RouteDetailStructureValidator.validate(plan, detail.legs) != RouteDetailStructureValidationResult.Valid) {
            return false
        }
        val snapshot = RouteStructureSnapshot(
            legs = detail.legs.map { leg ->
                leg.copy(
                    fareHkd = null,
                    plannedBoardingTime = null,
                    plannedAlightingTime = null
                )
            },
            originName = detail.originName,
            destinationName = detail.destinationName,
            transferTypes = detail.transfers.map { it.type }
        )
        synchronized(entries) {
            val now = clock()
            val existing = readValid(entries, key, now, ttlMillis)
            if (existing != null && snapshot.qualityScore() < existing.qualityScore()) return false
            entries[key] = TimedValue(snapshot, now)
        }
        return true
    }

    private fun RouteStructureSnapshot.qualityScore(): Int {
        return legs.count { !it.directionText.isNullOrBlank() } +
            listOf(originName, destinationName).count { !it.isNullOrBlank() }
    }
}

class WalkingDistanceCache(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = DEFAULT_ROUTE_DETAIL_DOMAIN_TTL_MILLIS
) {
    private val entries = mutableMapOf<WalkingDistanceCacheKey, TimedValue<WalkingDistanceSnapshot>>()

    fun get(key: WalkingDistanceCacheKey): WalkingDistanceSnapshot? = read(
        entries,
        key,
        clock(),
        ttlMillis,
        category = "walking_cache"
    )

    fun put(key: WalkingDistanceCacheKey, detail: ParsedRouteDetail): Boolean {
        if (detail.completeness != RouteDetailCompleteness.COMPLETE) return false
        val origin = detail.originWalking?.distanceMeters ?: return false
        val destination = detail.destinationWalking?.distanceMeters ?: return false
        val transferDistances = detail.transfers.map { transfer ->
            when (transfer.type) {
                RouteDetailTransferType.SAME_STOP -> null
                RouteDetailTransferType.WALK_TO_TRANSFER_STOP -> transfer.walking?.distanceMeters ?: return false
            }
        }
        synchronized(entries) {
            entries[key] = TimedValue(
                WalkingDistanceSnapshot(origin, transferDistances, destination),
                clock()
            )
        }
        return true
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

private const val DEFAULT_ROUTE_DETAIL_DOMAIN_TTL_MILLIS = 86_400_000L

private fun <K, V> readValid(
    entries: MutableMap<K, TimedValue<V>>,
    key: K,
    now: Long,
    ttlMillis: Long
): V? {
    val entry = entries[key] ?: return null
    if (now - entry.cachedAtMillis >= ttlMillis) {
        entries.remove(key)
        return null
    }
    return entry.value
}

private fun <K : Any, V> read(
    entries: MutableMap<K, TimedValue<V>>,
    key: K,
    now: Long,
    ttlMillis: Long,
    category: String
): V? {
    val result: V?
    val action: String
    synchronized(entries) {
        val entry = entries[key]
        when {
            entry == null -> {
                result = null
                action = "miss"
            }
            now - entry.cachedAtMillis >= ttlMillis -> {
                entries.remove(key)
                result = null
                action = "expired"
            }
            else -> {
                result = entry.value
                action = "hit"
            }
        }
    }
    RouteDetailDiagnostics.record(
        RouteDetailDiagnosticEvent(
            category = category,
            action = action,
            safeKeyHash = RouteDetailDiagnostics.safeHash(key)
        )
    )
    return result
}
