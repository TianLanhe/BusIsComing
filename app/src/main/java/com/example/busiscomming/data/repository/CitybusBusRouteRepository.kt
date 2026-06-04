package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.Place
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

class CitybusBusRouteRepository(
    private val parser: CitybusRouteParser = CitybusRouteParser,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val routeFetcher: (URL, Map<String, String>) -> String = ::fetchRouteHtml
) : BusRouteRepository {
    override fun searchRoutes(origin: Place, destination: Place): List<BusRouteOption> {
        val queryTime = formatQueryTime(clock())
        val headers = requestHeaders()
        val executor = Executors.newFixedThreadPool(SEARCH_MODES.size)
        return try {
            val futures = executor.invokeAll(
                SEARCH_MODES.map { mode ->
                    Callable {
                        val url = buildRouteUrl(origin, destination, queryTime, mode)
                        parser.parse(routeFetcher(url, headers))
                    }
                }
            )

            val failures = mutableListOf<Throwable>()
            val successfulResults = futures.mapNotNull { future ->
                try {
                    future.get()
                } catch (exception: ExecutionException) {
                    failures += exception.cause ?: exception
                    null
                }
            }

            if (successfulResults.isEmpty()) {
                throw IOException("Citybus route query failed for all m1 modes", failures.firstOrNull())
            }

            aggregateResults(successfulResults.flatten())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Citybus route query was interrupted", exception)
        } finally {
            executor.shutdownNow()
        }
    }

    fun buildRouteUrl(
        origin: Place,
        destination: Place,
        queryTime: String,
        searchMode: String = DEFAULT_SEARCH_MODE
    ): URL {
        return URL(
            "$BASE_URL" +
                "?slat=${origin.latitude}" +
                "&slon=${origin.longitude}" +
                "&elat=${destination.latitude}" +
                "&elon=${destination.longitude}" +
                "&t=${encodeQueryValue(queryTime)}" +
                "&ws=$WALKING_SEARCH_RATIO" +
                "&leg=2" +
                "&m1=$searchMode" +
                "&l=0"
        )
    }

    fun formatQueryTime(timestampMillis: Long): String {
        return SimpleDateFormat(QUERY_TIME_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone(HONG_KONG_TIME_ZONE)
        }.format(Date(timestampMillis))
    }

    fun requestHeaders(): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "Accept-Language" to "zh-CN,zh;q=0.9",
        "Connection" to "keep-alive",
        "Cookie" to CITYBUS_COOKIE,
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

    private fun aggregateResults(routes: List<BusRouteOption>): List<BusRouteOption> {
        return routes
            .distinctBy {
                RouteDedupKey(
                    routeSegments = it.routeSegments,
                    priceHkd = it.priceHkd,
                    durationMinutes = it.durationMinutes,
                    walkingDistanceMeters = it.walkingDistanceMeters
                )
            }
            .sortedBy { it.durationMinutes }
    }

    private data class RouteDedupKey(
        val routeSegments: List<String>,
        val priceHkd: Double,
        val durationMinutes: Int,
        val walkingDistanceMeters: Int
    )

    companion object {
        private const val BASE_URL = "https://mobile.citybus.com.hk/nwp3/ppsearch_p3.php"
        private const val DEFAULT_SEARCH_MODE = "T"
        private const val WALKING_SEARCH_RATIO = "1.3"
        private const val QUERY_TIME_PATTERN = "yyyy-MM-dd HH:mm"
        private const val HONG_KONG_TIME_ZONE = "Asia/Hong_Kong"
        private val SEARCH_MODES = listOf("T", "F", "W")
        private const val CITYBUS_COOKIE =
            "ETWEBID=6a1ecbeae8d60; PPFARE=1; LANG=TC; PHPSESSID=ev7984lo8uibj4kc3njbroe1a1; " +
                "6a1ecbeae877d=d390nlreb7ht18na2b0rser0p4; " +
                "__gads=ID=eb0f3fa592b3fb75:T=1780403182:RT=1780403182:S=ALNI_MbRV20ICxcKynl5JHsVVzonsmksRg; " +
                "__gpi=UID=000014428d894430:T=1780403182:RT=1780403182:S=ALNI_MZPUJxgA6yobYJEFzH6ZlTYJCvQPQ; " +
                "__eoi=ID=7a95018b76510f87:T=1780403182:RT=1780403182:S=AA-AfjZ0fHzxpoZ9Md5XFTPKrNaP; " +
                "FCCDCF=%5Bnull%2Cnull%2Cnull%2Cnull%2Cnull%2Cnull%2C%5B%5B32%2C%22%5B%5C%22f897bbf6-4b5c-4104-b988-23436f7e52a3%5C%22%2C%5B1780403183%2C593000000%5D%5D%22%5D%5D%5D; " +
                "FCNEC=%5B%5B%22AKsRol-vI493XkrY6BdozwK6Zzo0kHea2mP7U7t1uRYa1Oxoe2DvhTjl9jqZcYe99qqTASH2soAL2TF26nHoFYWErzON7fnHdgzFv05zqtKAGptTV1-GPtlQc1lAWF23mLnk3pHgrkcbz3IZfOaMmyhRO1VieNQfcw%3D%3D%22%5D%5D"
    }
}

private const val TIMEOUT_MS = 20_000

private fun fetchRouteHtml(url: URL, headers: Map<String, String>): String {
    val connection = url.openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
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
            throw IOException("Citybus route query failed with HTTP $statusCode")
        }

        responseBody
    } finally {
        connection.disconnect()
    }
}
