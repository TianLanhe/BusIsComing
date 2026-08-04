package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.data.model.RouteGeometrySegment

class RouteGeometryCache(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS
) {
    private val entries = mutableMapOf<RouteGeometryKey, Entry>()

    fun get(key: RouteGeometryKey): RouteGeometrySegment? {
        val now = clock()
        synchronized(entries) {
            val entry = entries[key] ?: return null
            if (now - entry.cachedAtMillis >= ttlMillis) {
                entries.remove(key)
                return null
            }
            return entry.segment
        }
    }

    fun put(segment: RouteGeometrySegment) {
        if (segment.points.size < 2) return
        synchronized(entries) {
            entries[segment.key] = Entry(segment, clock())
        }
    }

    fun remove(key: RouteGeometryKey) {
        synchronized(entries) {
            entries.remove(key)
        }
    }

    private data class Entry(
        val segment: RouteGeometrySegment,
        val cachedAtMillis: Long
    )

    companion object {
        const val DEFAULT_TTL_MILLIS = 86_400_000L
    }
}
