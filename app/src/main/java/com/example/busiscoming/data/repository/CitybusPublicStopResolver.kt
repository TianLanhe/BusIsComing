package com.example.busiscoming.data.repository

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class CitybusRouteStopResolver(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val routeStopFetcher: (URL) -> String = ::fetchCitybusPublicApi,
    private val cacheTtlMillis: Long = CACHE_TTL_MILLIS
) {
    private val routeStopCache = mutableMapOf<RouteStopCacheKey, CachedRouteStops>()

    fun buildRouteStopUrl(company: String, route: String, directionPath: String): URL {
        return URL("$BASE_URL/route-stop/$company/$route/$directionPath")
    }

    fun findStopId(
        company: String,
        route: String,
        directionPath: String,
        sequence: Int
    ): String? {
        return routeStopsFor(company, route, directionPath)[sequence]
    }

    fun routeStopsFor(company: String, route: String, directionPath: String): Map<Int, String> {
        val key = RouteStopCacheKey(company, route, directionPath)
        val now = clock()
        synchronized(routeStopCache) {
            val cached = routeStopCache[key]
            if (cached != null && now - cached.cachedAtMillis < cacheTtlMillis) {
                return cached.stopsBySeq
            }
        }

        val response = routeStopFetcher(buildRouteStopUrl(company, route, directionPath))
        val stopsBySeq = parseCitybusJsonObjects(response)
            .mapNotNull { fields ->
                val seq = fields["seq"]?.toIntOrNull() ?: return@mapNotNull null
                val stop = fields["stop"].orEmpty()
                if (stop.isBlank()) null else seq to stop
            }
            .toMap()

        if (stopsBySeq.isNotEmpty()) {
            synchronized(routeStopCache) {
                routeStopCache[key] = CachedRouteStops(stopsBySeq, now)
            }
        }
        return stopsBySeq
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
}

class CitybusStopNameResolver(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val stopFetcher: (URL) -> String = ::fetchCitybusPublicApi,
    private val cacheTtlMillis: Long = CACHE_TTL_MILLIS
) {
    private val stopNameCache = mutableMapOf<StopNameCacheKey, CachedStopName>()

    fun buildStopUrl(stopId: String): URL {
        return URL("$BASE_URL/stop/$stopId")
    }

    fun resolveStopName(company: String, stopId: String, lang: String): String? {
        val key = StopNameCacheKey(company, stopId, lang)
        val now = clock()
        synchronized(stopNameCache) {
            val cached = stopNameCache[key]
            if (cached != null && now - cached.cachedAtMillis < cacheTtlMillis) {
                return cached.name
            }
        }

        val response = stopFetcher(buildStopUrl(stopId))
        val name = parseStopName(response, lang)?.takeIf { it.isNotBlank() } ?: return null
        synchronized(stopNameCache) {
            stopNameCache[key] = CachedStopName(name, now)
        }
        return name
    }

    private fun parseStopName(response: String, lang: String): String? {
        val preferredKey = when (lang) {
            "1", "en", "EN" -> "name_en"
            "2", "sc", "SC" -> "name_sc"
            else -> "name_tc"
        }
        val fallbackKeys = listOf(preferredKey, "name_tc", "name_en", "name_sc").distinct()
        return parseCitybusJsonObjects(response)
            .firstNotNullOfOrNull { fields ->
                fallbackKeys.firstNotNullOfOrNull { key -> fields[key]?.trim()?.takeIf { it.isNotBlank() } }
            }
    }

    private data class StopNameCacheKey(
        val company: String,
        val stopId: String,
        val lang: String
    )

    private data class CachedStopName(
        val name: String,
        val cachedAtMillis: Long
    )
}

internal fun fetchCitybusPublicApi(url: URL): String {
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

internal fun parseCitybusJsonObjects(response: String): List<Map<String, String>> {
    return JSON_OBJECT_PATTERN.findAll(response).map { objectMatch ->
        JSON_FIELD_PATTERN.findAll(objectMatch.groupValues[1])
            .associate { fieldMatch ->
                val value = fieldMatch.groupValues[2].ifBlank { fieldMatch.groupValues[3] }
                fieldMatch.groupValues[1] to value
            }
    }.toList()
}

private const val BASE_URL = "https://rt.data.gov.hk/v2/transport/citybus"
private const val PUBLIC_API_TIMEOUT_MS = 10_000
private const val CACHE_TTL_MILLIS = 86_400_000L
private val JSON_OBJECT_PATTERN = Regex("""\{([^{}]*)\}""")
private val JSON_FIELD_PATTERN = Regex(""""([^"]+)"\s*:\s*(?:"([^"]*)"|(-?\d+))""")
