package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteRecoveryContext
import com.golink.busiscoming.data.model.ParsedRouteDetail
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailCompleteness
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CitybusRouteDetailRepository(
    private val parser: CitybusRouteDetailParser = CitybusRouteDetailParser,
    private val cache: RouteDetailCache = RouteDetailCache(),
    private val structureCache: RouteStructureCache = RouteStructureCache(),
    private val walkingCache: WalkingDistanceCache = WalkingDistanceCache(),
    private val sessionRegistry: CitybusSessionRegistry = CitybusSessionRuntime.registry,
    private val recoverySearcher: ((P2pRouteRecoveryContext, String) -> List<BusRouteOption>)? = null,
    private val detailFetcher: (URL, Map<String, String>) -> String = ::fetchRouteDetailHtml
) : RouteDetailRepository {
    private val resolvedRecoverySearcher: (P2pRouteRecoveryContext, String) -> List<BusRouteOption> =
        recoverySearcher ?: CitybusBusRouteRepository(sessionRegistry = sessionRegistry)::searchRouteCandidatesForRecovery

    override fun loadRouteDetail(route: BusRouteOption): RouteDetail {
        val originalQuery = route.routeDetailQuery ?: throw IOException("Route detail metadata is missing")
        if (originalQuery.recoveryContext == null) {
            val cached = cache.getDetail(originalQuery.cacheKey())
            if (cached != null) return toRouteDetail(route, originalQuery, cached)

            var parsed = fetchAndParse(originalQuery)
            if (parsed.completeness == RouteDetailCompleteness.SESSION_MISSING) {
                parsed = parsed.copy(completeness = RouteDetailCompleteness.PARTIAL)
            }
            if (parsed.completeness == RouteDetailCompleteness.COMPLETE) {
                cache.put(originalQuery.cacheKey(), parsed)
            }
            return toRouteDetail(route, originalQuery, parsed)
        }

        val cachedStructure = structureCache.get(originalQuery.structureCacheKey())
        val walkingKey = originalQuery.walkingDistanceCacheKey()
        val cachedWalking = walkingKey?.let(walkingCache::get)
        if (cachedStructure != null && cachedWalking != null) {
            return toRouteDetail(
                route,
                originalQuery,
                ParsedRouteDetail(
                    legs = cachedStructure.legs,
                    originWalking = cachedWalking?.originWalking,
                    transfers = cachedWalking?.transfers.orEmpty(),
                    destinationWalking = cachedWalking?.destinationWalking,
                    originName = cachedStructure.originName,
                    destinationName = cachedStructure.destinationName,
                    completeness = RouteDetailCompleteness.COMPLETE
                )
            )
        }

        var activeQuery = originalQuery
        var recoveryAttempted = false
        if (!hasMatchingSession(activeQuery)) {
            recoveryAttempted = true
            activeQuery = recoverQuery(activeQuery) ?: activeQuery
        }

        var parsed = fetchAndParse(activeQuery)
        if (parsed.completeness == RouteDetailCompleteness.SESSION_MISSING && !recoveryAttempted) {
            recoveryAttempted = true
            val recoveredQuery = recoverQuery(originalQuery)
            if (recoveredQuery != null) {
                activeQuery = recoveredQuery
                parsed = fetchAndParse(activeQuery)
            }
        }
        if (parsed.completeness == RouteDetailCompleteness.SESSION_MISSING) {
            parsed = parsed.copy(completeness = RouteDetailCompleteness.PARTIAL)
        }

        structureCache.put(originalQuery.structureCacheKey(), parsed)
        val activeWalkingKey = originalQuery.walkingDistanceCacheKey()
        if (activeWalkingKey != null) {
            walkingCache.put(activeWalkingKey, parsed)
        }
        return toRouteDetail(route, originalQuery, parsed)
    }

    fun buildDetailUrl(query: P2pRouteDetailQuery): URL {
        return URL(
            "$BASE_URL" +
                "?info=${encodeQueryValue(query.rawInfo)}" +
                "&ginfo=${encodeQueryValue(query.generalInfo)}" +
                "&lid=${encodeQueryValue(query.listId)}" +
                "&l=${encodeQueryValue(query.lang)}"
        )
    }

    fun requestHeaders(): Map<String, String> = emptyMap()

    private fun fetchAndParse(query: P2pRouteDetailQuery): ParsedRouteDetail {
        val url = buildDetailUrl(query)
        val response = detailFetcher(url, requestHeaders(query, url))
        return parser.parseDetail(response = response, plan = query.plan)
    }

    private fun requestHeaders(query: P2pRouteDetailQuery, url: URL): Map<String, String> {
        if (url.protocol != "https" || url.host != CITYBUS_HOST) return emptyMap()
        val context = query.recoveryContext ?: return emptyMap()
        val session = sessionRegistry.resolve(query.sessionRef) ?: return emptyMap()
        if (session.language != query.lang || session.recoveryContext != context) return emptyMap()
        return mapOf("Cookie" to "PHPSESSID=${session.phpSessionId}")
    }

    private fun hasMatchingSession(query: P2pRouteDetailQuery): Boolean {
        val context = query.recoveryContext ?: return false
        val session = sessionRegistry.resolve(query.sessionRef) ?: return false
        return session.language == query.lang && session.recoveryContext == context
    }

    private fun recoverQuery(original: P2pRouteDetailQuery): P2pRouteDetailQuery? {
        val context = original.recoveryContext ?: return null
        return runCatching { resolvedRecoverySearcher(context, original.lang) }
            .getOrDefault(emptyList())
            .asSequence()
            .mapNotNull { it.routeDetailQuery }
            .firstOrNull { candidate ->
                candidate.lang == original.lang &&
                    candidate.recoveryContext?.searchMode == context.searchMode &&
                    candidate.plan.fingerprint() == original.plan.fingerprint() &&
                    hasMatchingSession(candidate)
            }
    }

    private fun toRouteDetail(
        route: BusRouteOption,
        query: P2pRouteDetailQuery,
        parsed: ParsedRouteDetail
    ): RouteDetail {
        return RouteDetail(
            routeName = route.routeName,
            priceHkd = route.priceHkd,
            durationMinutes = route.durationMinutes,
            walkingDistanceMeters = route.walkingDistanceMeters,
            legs = parsed.legs,
            originWalking = parsed.originWalking,
            transfers = parsed.transfers,
            destinationWalking = parsed.destinationWalking,
            plannedDepartureTime = parsed.plannedDepartureTime,
            plannedArrivalTime = parsed.plannedArrivalTime ?: query.plannedArrivalTime(),
            originName = parsed.originName,
            destinationName = parsed.destinationName,
            completeness = parsed.completeness
        )
    }

    private fun encodeQueryValue(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun P2pRouteDetailQuery.plannedArrivalTime(): String? {
        return generalInfo.substringBefore("|*|").trim().takeIf { value ->
            value.matches(Regex("\\d{1,2}:\\d{2}"))
        }
    }

    companion object {
        private const val BASE_URL = "https://mobile.citybus.com.hk/nwp3/getp2pstopinroute.php"
        private const val CITYBUS_HOST = "mobile.citybus.com.hk"
    }
}

private const val ROUTE_DETAIL_TIMEOUT_MS = 20_000

private fun fetchRouteDetailHtml(url: URL, headers: Map<String, String>): String {
    val connection = url.openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = ROUTE_DETAIL_TIMEOUT_MS
        connection.readTimeout = ROUTE_DETAIL_TIMEOUT_MS
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

        val statusCode = connection.responseCode
        val responseBody = if (statusCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        }
        if (statusCode !in 200..299) {
            throw IOException("Citybus route detail query failed with HTTP $statusCode")
        }
        responseBody
    } finally {
        connection.disconnect()
    }
}
