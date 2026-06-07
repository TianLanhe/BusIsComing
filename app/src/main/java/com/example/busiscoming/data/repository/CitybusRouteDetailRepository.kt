package com.example.busiscoming.data.repository

import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.P2pRouteDetailQuery
import com.example.busiscoming.data.model.RouteDetail
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
        val legs = cache.get(cacheKey) ?: run {
            val parsedLegs = parser.parse(
                response = detailFetcher(buildDetailUrl(query), requestHeaders()),
                plan = query.plan
            )
            cache.put(cacheKey, parsedLegs)
            parsedLegs
        }

        return RouteDetail(
            routeName = route.routeName,
            priceHkd = route.priceHkd,
            durationMinutes = route.durationMinutes,
            walkingDistanceMeters = route.walkingDistanceMeters,
            legs = legs
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

    fun requestHeaders(): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "Accept-Language" to "zh-CN,zh;q=0.9",
        "Connection" to "keep-alive",
        "Referer" to "https://mobile.citybus.com.hk/nwp3/",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin",
        "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36",
        "sec-ch-ua" to "\"Chromium\";v=\"148\", \"Google Chrome\";v=\"148\", \"Not/A)Brand\";v=\"99\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"macOS\""
    )

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
