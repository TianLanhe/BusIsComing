package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.FirstLegEtaQuery
import com.example.busiscomming.data.model.WaitTimeState
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class CitybusFirstLegEtaService(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val routeStopFetcher: (URL) -> String = ::fetchPublicApi,
    private val etaFetcher: (URL) -> String = ::fetchPublicApi,
    private val routeStopCacheTtlMillis: Long = ROUTE_STOP_CACHE_TTL_MILLIS
) {
    private val routeStopCache = mutableMapOf<RouteStopCacheKey, CachedRouteStops>()

    fun resolveWaitTime(query: FirstLegEtaQuery): WaitTimeState {
        return runCatching {
            val stopId = findStopId(query) ?: return WaitTimeState.Unavailable
            val etaResponse = etaFetcher(buildEtaUrl(query.company, stopId, query.route))
            val etaMillis = parseNearestEtaMillis(
                response = etaResponse,
                query = query,
                stopId = stopId
            ) ?: return WaitTimeState.Unavailable
            WaitTimeState.Available(calculateWaitMinutes(etaMillis))
        }.getOrElse {
            WaitTimeState.Unavailable
        }
    }

    fun buildRouteStopUrl(company: String, route: String, directionPath: String): URL {
        return URL("$BASE_URL/route-stop/$company/$route/$directionPath")
    }

    fun buildEtaUrl(company: String, stopId: String, route: String): URL {
        return URL("$BASE_URL/eta/$company/$stopId/$route")
    }

    fun calculateWaitMinutes(etaMillis: Long): Int {
        val remainingMillis = etaMillis - clock()
        if (remainingMillis <= 0) return 0
        return ((remainingMillis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
    }

    private fun findStopId(query: FirstLegEtaQuery): String? {
        val routeStops = routeStopsFor(query.company, query.route, query.directionPath)
        return routeStops[query.boardingSeq]
    }

    private fun routeStopsFor(company: String, route: String, directionPath: String): Map<Int, String> {
        val key = RouteStopCacheKey(company, route, directionPath)
        val now = clock()
        synchronized(routeStopCache) {
            val cached = routeStopCache[key]
            if (cached != null && now - cached.cachedAtMillis < routeStopCacheTtlMillis) {
                return cached.stopsBySeq
            }
        }

        val response = routeStopFetcher(buildRouteStopUrl(company, route, directionPath))
        val stopsBySeq = parseJsonObjects(response)
            .mapNotNull { fields ->
                val seq = fields["seq"]?.toIntOrNull() ?: return@mapNotNull null
                val stop = fields["stop"].orEmpty()
                if (stop.isBlank()) null else seq to stop
            }
            .toMap()

        synchronized(routeStopCache) {
            routeStopCache[key] = CachedRouteStops(stopsBySeq, now)
        }
        return stopsBySeq
    }

    private fun parseNearestEtaMillis(
        response: String,
        query: FirstLegEtaQuery,
        stopId: String
    ): Long? {
        val matchingEtaMillis = parseJsonObjects(response).mapNotNull { fields ->
            if (fields["route"] != query.route) return@mapNotNull null
            if (fields["stop"] != stopId) return@mapNotNull null
            if (fields["dir"] != query.bound) return@mapNotNull null
            if (fields["seq"]?.toIntOrNull() != query.boardingSeq) return@mapNotNull null
            fields["eta"]?.takeIf { it.isNotBlank() }?.toHongKongIsoMillis()
        }
        return matchingEtaMillis.minOrNull()
    }

    private fun String.toHongKongIsoMillis(): Long? {
        return try {
            ETA_DATE_FORMAT.get()!!.parse(this)?.time
        } catch (_: ParseException) {
            null
        }
    }

    private data class RouteStopCacheKey(
        val company: String,
        val route: String,
        val directionPath: String
    )

    private data class CachedRouteStops(
        val stopsBySeq: Map<Int, String>,
        val cachedAtMillis: Long
    )

    companion object {
        private const val BASE_URL = "https://rt.data.gov.hk/v2/transport/citybus"
        private const val ROUTE_STOP_CACHE_TTL_MILLIS = 86_400_000L
        private const val MILLIS_PER_MINUTE = 60_000L
        private val ETA_DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            }
        }
    }
}

private const val PUBLIC_API_TIMEOUT_MS = 10_000

private fun fetchPublicApi(url: URL): String {
    val connection = url.openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = PUBLIC_API_TIMEOUT_MS
        connection.readTimeout = PUBLIC_API_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")

        val statusCode = connection.responseCode
        val responseBody = if (statusCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        }

        if (statusCode !in 200..299) {
            throw IOException("Citybus public API failed with HTTP $statusCode")
        }

        responseBody
    } finally {
        connection.disconnect()
    }
}

private fun parseJsonObjects(response: String): List<Map<String, String>> {
    return JSON_OBJECT_PATTERN.findAll(response).map { objectMatch ->
        JSON_FIELD_PATTERN.findAll(objectMatch.groupValues[1])
            .associate { fieldMatch ->
                val value = fieldMatch.groupValues[2].ifBlank { fieldMatch.groupValues[3] }
                fieldMatch.groupValues[1] to value
            }
    }.toList()
}

private val JSON_OBJECT_PATTERN = Regex("""\{([^{}]*)\}""")
private val JSON_FIELD_PATTERN = Regex(""""([^"]+)"\s*:\s*(?:"([^"]*)"|(-?\d+))""")
