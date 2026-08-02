package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.P2pRouteDetailCacheKey
import com.golink.busiscoming.data.model.ParsedRouteDetail
import com.golink.busiscoming.data.model.RouteDetailLeg

class RouteDetailCache(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS
) {
    private val entries = mutableMapOf<P2pRouteDetailCacheKey, CachedRouteDetail>()

    fun get(key: P2pRouteDetailCacheKey): List<RouteDetailLeg>? {
        return getEntry(key)?.legs
    }

    fun getDetail(key: P2pRouteDetailCacheKey): ParsedRouteDetail? = getEntry(key)?.detail

    fun getOriginWalkingDistanceMeters(key: P2pRouteDetailCacheKey): Int? {
        return getEntry(key)?.originWalkingDistanceMeters
    }

    private fun getEntry(key: P2pRouteDetailCacheKey): CachedRouteDetail? {
        val now = clock()
        synchronized(entries) {
            val entry = entries[key] ?: return null
            if (now - entry.cachedAtMillis >= ttlMillis) {
                entries.remove(key)
                return null
            }
            return entry
        }
    }

    fun put(key: P2pRouteDetailCacheKey, legs: List<RouteDetailLeg>) {
        put(key, legs, originWalkingDistanceMeters = null)
    }

    fun put(key: P2pRouteDetailCacheKey, detail: ParsedRouteDetail) {
        if (detail.legs.isEmpty()) return
        synchronized(entries) {
            entries[key] = CachedRouteDetail(detail = detail, cachedAtMillis = clock())
        }
    }

    fun put(key: P2pRouteDetailCacheKey, legs: List<RouteDetailLeg>, originWalkingDistanceMeters: Int?) {
        if (legs.isEmpty()) return
        synchronized(entries) {
            entries[key] = CachedRouteDetail(
                detail = ParsedRouteDetail(
                    legs = legs,
                    originWalking = originWalkingDistanceMeters?.let {
                        com.golink.busiscoming.data.model.RouteDetailWalkingSegment(
                            com.golink.busiscoming.data.model.RouteDetailWalkingKind.ORIGIN,
                            it
                        )
                    }
                ),
                cachedAtMillis = clock()
            )
        }
    }

    private data class CachedRouteDetail(
        val detail: ParsedRouteDetail,
        val cachedAtMillis: Long
    ) {
        val legs: List<RouteDetailLeg> get() = detail.legs
        val originWalkingDistanceMeters: Int? get() = detail.originWalking?.distanceMeters
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 86_400_000L
    }
}
