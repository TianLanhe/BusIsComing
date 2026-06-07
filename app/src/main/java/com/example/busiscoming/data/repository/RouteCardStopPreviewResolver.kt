package com.example.busiscoming.data.repository

import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.RouteCardStopPreview
import com.example.busiscoming.data.model.RouteCardStopPreviewCacheKey

class RouteCardStopPreviewResolver(
    private val stopMapResolver: CitybusP2pStopMapResolver = CitybusP2pStopMapResolver(),
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
        val stopMap = stopMapResolver.resolveStopMap(query) ?: return null
        val boardingLegIndex = query.plan.legs.indexOf(boardingLeg).coerceAtLeast(0)
        val alightingLegIndex = query.plan.legs.indexOf(alightingLeg).coerceAtLeast(0)
        val boardingStop = stopMap.findStop(
            legIndex = boardingLegIndex,
            routeVariant = boardingLeg.routeVariant,
            sequence = boardingLeg.boardingSeq
        ) ?: return null
        val alightingStop = stopMap.findStop(
            legIndex = alightingLegIndex,
            routeVariant = alightingLeg.routeVariant,
            sequence = alightingLeg.alightingSeq
        ) ?: return null
        val preview = RouteCardStopPreview(
            boardingStopName = boardingStop.displayName,
            alightingStopName = alightingStop.displayName
        )

        synchronized(previewCache) {
            previewCache[key] = CachedPreview(preview, now)
        }
        return preview
    }

    private data class CachedPreview(
        val preview: RouteCardStopPreview,
        val cachedAtMillis: Long
    )

    companion object {
        private const val CACHE_TTL_MILLIS = 86_400_000L
    }
}
