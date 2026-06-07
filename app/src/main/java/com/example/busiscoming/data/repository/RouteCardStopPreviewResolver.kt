package com.example.busiscoming.data.repository

import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.P2pRouteLeg
import com.example.busiscoming.data.model.RouteCardStopPreview
import com.example.busiscoming.data.model.RouteCardStopPreviewCacheKey
import com.example.busiscoming.data.model.RouteDetailDisplayFormatter

class RouteCardStopPreviewResolver(
    private val routeStopResolver: CitybusRouteStopResolver = CitybusRouteStopResolver(),
    private val stopNameResolver: CitybusStopNameResolver = CitybusStopNameResolver(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val cacheTtlMillis: Long = CACHE_TTL_MILLIS
) {
    private val previewCache = mutableMapOf<RouteCardStopPreviewCacheKey, CachedPreview>()

    fun resolvePreview(route: BusRouteOption): RouteCardStopPreview? {
        return runCatching { resolvePreviewOrThrow(route) }.getOrNull()
    }

    private fun resolvePreviewOrThrow(route: BusRouteOption): RouteCardStopPreview? {
        val query = route.routeDetailQuery ?: return null
        val key = RouteCardStopPreviewCacheKey(query.rawInfo, query.lang)
        val now = clock()
        synchronized(previewCache) {
            val cached = previewCache[key]
            if (cached != null && now - cached.cachedAtMillis < cacheTtlMillis) {
                return cached.preview
            }
        }

        val boardingLeg = query.plan.previewBoardingLeg ?: return null
        val alightingLeg = query.plan.previewAlightingLeg ?: return null
        val boardingName = resolveStopName(boardingLeg, boardingLeg.boardingSeq, query.lang) ?: return null
        val alightingName = resolveStopName(alightingLeg, alightingLeg.alightingSeq, query.lang) ?: return null
        val preview = RouteCardStopPreview(
            boardingStopName = RouteDetailDisplayFormatter.stationDisplayName(boardingName),
            alightingStopName = RouteDetailDisplayFormatter.stationDisplayName(alightingName)
        )

        synchronized(previewCache) {
            previewCache[key] = CachedPreview(preview, now)
        }
        return preview
    }

    private fun resolveStopName(leg: P2pRouteLeg, sequence: Int, lang: String): String? {
        val directionPath = leg.directionPath ?: return null
        val stopId = routeStopResolver.findStopId(
            company = leg.company,
            route = leg.route,
            directionPath = directionPath,
            sequence = sequence
        ) ?: return null
        return stopNameResolver.resolveStopName(
            company = leg.company,
            stopId = stopId,
            lang = lang
        )
    }

    private data class CachedPreview(
        val preview: RouteCardStopPreview,
        val cachedAtMillis: Long
    )

    companion object {
        private const val CACHE_TTL_MILLIS = 86_400_000L
    }
}
