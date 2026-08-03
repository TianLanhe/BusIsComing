package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.data.model.RouteGeometryCoordinate
import com.golink.busiscoming.data.model.RouteGeometryPoint
import com.golink.busiscoming.data.model.RouteGeometrySegment
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RouteGeometryRequest(
    val key: RouteGeometryKey,
    val boardingCoordinate: RouteGeometryCoordinate? = null,
    val alightingCoordinate: RouteGeometryCoordinate? = null
)

class RouteGeometryLoadHandle internal constructor(
    private val cancelAction: () -> Unit
) : AutoCloseable {
    override fun close() = cancelAction()
}

interface RouteGeometryDataSource {
    fun loadGeometries(
        requests: List<RouteGeometryRequest>,
        onResult: (RouteGeometryRequest, Result<RouteGeometrySegment>) -> Unit
    ): RouteGeometryLoadHandle
}

class CitybusRouteGeometryRepository(
    private val parser: CitybusRouteGeometryParser = CitybusRouteGeometryParser,
    private val cache: RouteGeometryCache = RouteGeometryCache(),
    private val geometryFetcher: (URL, Map<String, String>) -> String = ::fetchRouteGeometry
) : RouteGeometryDataSource {
    private val inFlight = mutableMapOf<RouteGeometryKey, FutureTask<RouteGeometrySegment>>()
    private val concurrencyPermits = Semaphore(MAX_CONCURRENT_REQUESTS, true)

    fun buildGeometryUrl(key: RouteGeometryKey): URL {
        require(key.isValid) { "Route geometry key is invalid" }
        return URL(
            "$BASE_URL?rdv=${encode(key.routeVariant)}" +
                "&start=${key.boardingSeq}&dest=${key.alightingSeq}"
        )
    }

    fun requestHeaders(): Map<String, String> = emptyMap()

    fun loadGeometry(
        key: RouteGeometryKey,
        boardingCoordinate: RouteGeometryCoordinate? = null,
        alightingCoordinate: RouteGeometryCoordinate? = null
    ): RouteGeometrySegment {
        require(key.isValid) { "Route geometry key is invalid" }
        val cached = cache.get(key)
        if (cached != null) {
            validateEndpoints(cached, boardingCoordinate, alightingCoordinate)
            return cached
        }
        var ownsTask = false
        val task = synchronized(inFlight) {
            inFlight[key] ?: FutureTask {
                fetchValidateAndCache(key, boardingCoordinate, alightingCoordinate)
            }.also {
                inFlight[key] = it
                ownsTask = true
            }
        }
        if (ownsTask) {
            try {
                task.run()
            } finally {
                synchronized(inFlight) {
                    if (inFlight[key] === task) inFlight.remove(key)
                }
            }
        }
        val segment = try {
            task.get()
        } catch (exception: ExecutionException) {
            val cause = exception.cause ?: exception
            when (cause) {
                is RuntimeException -> throw cause
                is Error -> throw cause
                else -> throw IOException("Citybus route geometry query failed", cause)
            }
        }
        validateEndpoints(segment, boardingCoordinate, alightingCoordinate)
        return segment
    }

    private fun fetchValidateAndCache(
        key: RouteGeometryKey,
        boardingCoordinate: RouteGeometryCoordinate?,
        alightingCoordinate: RouteGeometryCoordinate?
    ): RouteGeometrySegment {
        val segment = RouteGeometrySegment(
            key,
            CitybusRouteGeometryCoordinateNormalizer.toWgs84(
                parser.parse(geometryFetcher(buildGeometryUrl(key), requestHeaders()))
            )
        )
        validateEndpoints(segment, boardingCoordinate, alightingCoordinate)
        cache.put(segment)
        return segment
    }

    override fun loadGeometries(
        requests: List<RouteGeometryRequest>,
        onResult: (RouteGeometryRequest, Result<RouteGeometrySegment>) -> Unit
    ): RouteGeometryLoadHandle {
        if (requests.isEmpty()) return RouteGeometryLoadHandle {}
        val cancelled = AtomicBoolean(false)
        val executor = Executors.newFixedThreadPool(minOf(MAX_CONCURRENT_REQUESTS, requests.size))
        requests.forEach { request ->
            executor.execute {
                if (cancelled.get()) return@execute
                val result = runCatching {
                    concurrencyPermits.acquire()
                    try {
                        loadGeometry(request.key, request.boardingCoordinate, request.alightingCoordinate)
                    } finally {
                        concurrencyPermits.release()
                    }
                }
                if (!cancelled.get()) onResult(request, result)
            }
        }
        executor.shutdown()
        return RouteGeometryLoadHandle {
            if (cancelled.compareAndSet(false, true)) executor.shutdownNow()
        }
    }

    private fun validateEndpoints(
        segment: RouteGeometrySegment,
        boardingCoordinate: RouteGeometryCoordinate?,
        alightingCoordinate: RouteGeometryCoordinate?
    ) {
        if (boardingCoordinate != null &&
            distanceMeters(segment.points.first(), boardingCoordinate) > MAX_ENDPOINT_DISTANCE_METERS
        ) {
            throw CitybusRouteGeometryParseException("Geometry start is too far from boarding stop")
        }
        if (alightingCoordinate != null &&
            distanceMeters(segment.points.last(), alightingCoordinate) > MAX_ENDPOINT_DISTANCE_METERS
        ) {
            throw CitybusRouteGeometryParseException("Geometry end is too far from alighting stop")
        }
    }

    private fun distanceMeters(point: RouteGeometryPoint, coordinate: RouteGeometryCoordinate): Double {
        val latitudeDelta = Math.toRadians(coordinate.latitude - point.latitude)
        val longitudeDelta = Math.toRadians(coordinate.longitude - point.longitude)
        val startLatitude = Math.toRadians(point.latitude)
        val endLatitude = Math.toRadians(coordinate.latitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(startLatitude) * cos(endLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private companion object {
        const val BASE_URL = "https://mobile.citybus.com.hk/nwp3/getlinep2p.php"
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val MAX_ENDPOINT_DISTANCE_METERS = 2_000.0
        const val MAX_CONCURRENT_REQUESTS = 3
    }
}

private const val ROUTE_GEOMETRY_TIMEOUT_MS = 20_000

private fun fetchRouteGeometry(url: URL, headers: Map<String, String>): String {
    val connection = url.openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = ROUTE_GEOMETRY_TIMEOUT_MS
        connection.readTimeout = ROUTE_GEOMETRY_TIMEOUT_MS
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

        val statusCode = connection.responseCode
        val responseBody = if (statusCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        }
        if (statusCode !in 200..299) {
            throw IOException("Citybus route geometry query failed with HTTP $statusCode")
        }
        responseBody
    } finally {
        connection.disconnect()
    }
}
