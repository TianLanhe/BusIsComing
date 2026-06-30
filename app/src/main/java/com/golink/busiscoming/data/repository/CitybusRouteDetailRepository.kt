package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.RouteDetail
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CitybusRouteDetailRepository(
    private val parser: CitybusRouteDetailParser = CitybusRouteDetailParser,
    private val cache: RouteDetailCache = RouteDetailCache(),
    private val detailFetcher: (URL, Map<String, String>) -> String = ::fetchRouteDetailHtml
) : RouteDetailRepository {
    override fun loadRouteDetail(route: BusRouteOption): RouteDetail {
        val query = route.routeDetailQuery ?: throw IOException("Route detail metadata is missing")
        val cacheKey = query.cacheKey()
        val cachedLegs = cache.get(cacheKey)
        val cachedOriginWalkingDistanceMeters = cache.getOriginWalkingDistanceMeters(cacheKey)
        val detailResponse = if (cachedLegs == null) detailFetcher(buildDetailUrl(query), requestHeaders()) else null
        val legs = cachedLegs ?: run {
            val response = detailResponse ?: throw IOException("Route detail response is missing")
            val parsedLegs = parser.parse(
                response = response,
                plan = query.plan
            )
            cache.put(
                cacheKey,
                parsedLegs,
                originWalkingDistanceMeters = parser.parseOriginWalkingDistanceMeters(response)
            )
            parsedLegs
        }

        return RouteDetail(
            routeName = route.routeName,
            priceHkd = route.priceHkd,
            durationMinutes = route.durationMinutes,
            walkingDistanceMeters = route.walkingDistanceMeters,
            legs = legs,
            originWalkingDistanceMeters = cachedOriginWalkingDistanceMeters
                ?: detailResponse?.let { parser.parseOriginWalkingDistanceMeters(it) }
        )
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

    private fun encodeQueryValue(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    companion object {
        private const val BASE_URL = "https://mobile.citybus.com.hk/nwp3/getp2pstopinroute.php"
    }
}

private const val ROUTE_DETAIL_TIMEOUT_MS = 20_000

private fun fetchRouteDetailHtml(url: URL, headers: Map<String, String>): String {
    val connection = url.openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = ROUTE_DETAIL_TIMEOUT_MS
        connection.readTimeout = ROUTE_DETAIL_TIMEOUT_MS
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
            throw IOException("Citybus route detail query failed with HTTP $statusCode")
        }

        responseBody
    } finally {
        connection.disconnect()
    }
}
