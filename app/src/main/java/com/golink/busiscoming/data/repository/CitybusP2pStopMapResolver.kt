package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.P2pStopMap
import com.golink.busiscoming.data.model.P2pStopMapCacheKey
import com.golink.busiscoming.data.model.P2pStopMapStop
import com.golink.busiscoming.data.model.RouteDetailDisplayFormatter
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CitybusP2pStopMapResolver(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val stopMapFetcher: (URL, Map<String, String>) -> String = ::fetchCitybusStopMapHtml,
    private val cacheTtlMillis: Long = CACHE_TTL_MILLIS
) {
    private val stopMapCache = mutableMapOf<P2pStopMapCacheKey, CachedStopMap>()

    fun buildStopMapUrl(rawInfo: String, lang: String): URL {
        return URL(
            "$BASE_URL" +
                "?r=${encodeQueryValue(rawInfo)}" +
                "&l=${encodeQueryValue(lang)}"
        )
    }

    fun resolveStopMap(query: P2pRouteDetailQuery): P2pStopMap? {
        return resolveStopMap(query.rawInfo, query.lang, query.plan)
    }

    fun resolveStopMap(rawInfo: String, lang: String, plan: P2pRoutePlan): P2pStopMap? {
        if (rawInfo.isBlank()) return null
        val key = P2pStopMapCacheKey(rawInfo, lang)
        val now = clock()
        synchronized(stopMapCache) {
            val cached = stopMapCache[key]
            if (cached != null && now - cached.cachedAtMillis < cacheTtlMillis) {
                return cached.stopMap
            }
        }

        val response = stopMapFetcher(buildStopMapUrl(rawInfo, lang), requestHeaders())
        val stopMap = CitybusP2pStopMapParser.parse(response, rawInfo, lang, plan)
            .takeIf { it.stops.isNotEmpty() }
            ?: return null

        synchronized(stopMapCache) {
            stopMapCache[key] = CachedStopMap(stopMap, now)
        }
        return stopMap
    }

    fun findStopId(leg: P2pRouteLeg, rawInfo: String, lang: String, legIndex: Int = 0): String? {
        val plan = P2pRoutePlan(rawInfo = rawInfo, lang = lang, legs = listOf(leg))
        return resolveStopMap(rawInfo, lang, plan)
            ?.findStop(legIndex, leg.routeVariant, leg.boardingSeq)
            ?.stopId
    }

    fun requestHeaders(): Map<String, String> = emptyMap()

    private fun encodeQueryValue(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private data class CachedStopMap(
        val stopMap: P2pStopMap,
        val cachedAtMillis: Long
    )

    companion object {
        private const val BASE_URL = "https://mobile.citybus.com.hk/nwp3/showstops2.php"
        private const val CACHE_TTL_MILLIS = 86_400_000L
    }
}

object CitybusP2pStopMapParser {
    fun parse(response: String, rawInfo: String, lang: String, plan: P2pRoutePlan): P2pStopMap {
        val stops = extractFunctionArguments(response)
            .mapNotNull { arguments -> parseStop(arguments, plan) }
            .toList()
        return P2pStopMap(rawInfo = rawInfo, lang = lang, stops = stops)
    }

    private fun parseStop(argumentList: String, plan: P2pRoutePlan): P2pStopMapStop? {
        val args = parseFunctionArgs(argumentList)
        if (args.size < 8) return null

        val stopId = args[0].trim().takeIf { it.isNotBlank() } ?: return null
        val longitude = args[1].trim().toDoubleOrNull() ?: return null
        val latitude = args[2].trim().toDoubleOrNull() ?: return null
        val markerType = args[3].trim()
        val sequence = args[4].trim().toIntOrNull() ?: return null
        val rawName = args[5].trim().removeStopSequencePrefix().takeIf { it.isNotBlank() } ?: return null
        val routeVariant = args[6].trim().takeIf { it.isNotBlank() } ?: return null
        val bound = args[7].trim()
        val legIndex = plan.legs.indexOfFirst { it.routeVariant == routeVariant }
            .takeIf { it >= 0 }
            ?: 0

        return P2pStopMapStop(
            legIndex = legIndex,
            company = plan.legs.getOrNull(legIndex)?.company.orEmpty(),
            routeVariant = routeVariant,
            publicRoute = routeVariant.substringBefore("-"),
            bound = bound,
            sequence = sequence,
            stopId = stopId,
            rawName = rawName,
            displayName = RouteDetailDisplayFormatter.stationDisplayName(rawName),
            latitude = latitude,
            longitude = longitude,
            markerType = markerType
        )
    }

    private fun parseFunctionArgs(argumentList: String): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var index = 0
        while (index < argumentList.length) {
            val char = argumentList[index]
            when (val activeQuote = quote) {
                null -> when {
                    char == '\'' || char == '"' -> quote = char
                    char == ',' -> {
                        args += current.toString().trim()
                        current.setLength(0)
                    }
                    else -> current.append(char)
                }
                else -> when {
                    escaped -> {
                        current.append(decodeJavaScriptEscape(char))
                        escaped = false
                    }
                    char == '\\' -> escaped = true
                    char == activeQuote -> quote = null
                    else -> current.append(char)
                }
            }
            index += 1
        }
        if (quote != null || escaped) return emptyList()
        if (current.isNotEmpty() || argumentList.endsWith(",")) {
            args += current.toString().trim()
        }
        return args
    }

    private fun extractFunctionArguments(response: String): Sequence<String> = sequence {
        var searchFrom = 0
        while (searchFrom < response.length) {
            val callStart = response.indexOf(ADD_STOP_ON_MAP_PREFIX, searchFrom)
            if (callStart < 0) break

            val argumentsStart = callStart + ADD_STOP_ON_MAP_PREFIX.length
            var quote: Char? = null
            var escaped = false
            var parenthesisDepth = 1
            var index = argumentsStart
            var foundEnd = false
            while (index < response.length) {
                val char = response[index]
                when (val activeQuote = quote) {
                    null -> when {
                        char == '\'' || char == '"' -> quote = char
                        char == '(' -> parenthesisDepth += 1
                        char == ')' -> {
                            parenthesisDepth -= 1
                            if (parenthesisDepth == 0) {
                                yield(response.substring(argumentsStart, index))
                                searchFrom = index + 1
                                foundEnd = true
                                break
                            }
                        }
                    }
                    else -> when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == activeQuote -> quote = null
                    }
                }
                index += 1
            }
            if (!foundEnd) break
        }
    }

    private fun decodeJavaScriptEscape(char: Char): Char {
        return when (char) {
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'v' -> '\u000B'
            else -> char
        }
    }

    private fun String.removeStopSequencePrefix(): String {
        return replace(STOP_SEQUENCE_PREFIX_PATTERN, "").trim()
    }

    private const val ADD_STOP_ON_MAP_PREFIX = "addstoponmap("
    private val STOP_SEQUENCE_PREFIX_PATTERN = Regex("""^\d+\s*-\s*""")
}

private const val STOP_MAP_TIMEOUT_MS = 20_000

private fun fetchCitybusStopMapHtml(url: URL, headers: Map<String, String>): String {
    val connection = url.openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = STOP_MAP_TIMEOUT_MS
        connection.readTimeout = STOP_MAP_TIMEOUT_MS
        headers.forEach { (name, value) ->
            connection.setRequestProperty(name, value)
        }

        val statusCode = connection.responseCode
        val responseBody = if (statusCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        }

        if (statusCode !in 200..299) {
            throw IOException("Citybus P2P stop map query failed with HTTP $statusCode")
        }

        responseBody
    } finally {
        connection.disconnect()
    }
}
