package com.example.busiscoming.data.repository

import com.example.busiscoming.data.model.P2pRouteDetailCacheKey
import com.example.busiscoming.data.model.RouteDetailLeg

class RouteDetailCache(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS
) {
    private val entries = mutableMapOf<P2pRouteDetailCacheKey, CachedRouteDetail>()

    fun get(key: P2pRouteDetailCacheKey): List<RouteDetailLeg>? {
        val now = clock()
        synchronized(entries) {
            val entry = entries[key] ?: return null
            if (now - entry.cachedAtMillis >= ttlMillis) {
                entries.remove(key)
                return null
            }
            return entry.legs
        }
    }

    fun put(key: P2pRouteDetailCacheKey, legs: List<RouteDetailLeg>) {
        if (legs.isEmpty()) return
        synchronized(entries) {
            entries[key] = CachedRouteDetail(
                legs = legs,
                cachedAtMillis = clock()
            )
        }
    }

    private data class CachedRouteDetail(
        val legs: List<RouteDetailLeg>,
        val cachedAtMillis: Long
    )

    companion object {
        const val DEFAULT_TTL_MILLIS = 86_400_000L
    }
}
