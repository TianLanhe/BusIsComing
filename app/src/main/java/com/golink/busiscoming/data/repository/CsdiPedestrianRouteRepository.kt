package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.PedestrianCoordinate
import com.golink.busiscoming.data.model.PedestrianRoute
import com.golink.busiscoming.data.model.PedestrianRoutePath
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

data class CsdiPedestrianRequest(
    val start: PedestrianCoordinate,
    val end: PedestrianCoordinate
) {
    init {
        require(start.isValidWgs84 && end.isValidWgs84) { "Pedestrian endpoints must be valid WGS84 coordinates" }
    }

    val key: CsdiPedestrianRequestKey = CsdiPedestrianRequestKey.from(start, end)
}

@JvmInline
value class CsdiPedestrianRequestKey private constructor(private val value: String) {
    override fun toString(): String = value

    companion object {
        private const val TRAVEL_MODE = 3

        fun from(start: PedestrianCoordinate, end: PedestrianCoordinate): CsdiPedestrianRequestKey =
            CsdiPedestrianRequestKey(
                "${round6(start.latitude)},${round6(start.longitude)}->" +
                    "${round6(end.latitude)},${round6(end.longitude)};travelMode=$TRAVEL_MODE"
            )

        private fun round6(value: Double): String = String.format(Locale.ROOT, "%.6f", value)
    }
}

object CsdiPedestrianRequestBuilder {
    private const val BASE_URL = "https://mapapi.hkmapservice.gov.hk/PedRoute/NAServer/route/solve"

    fun buildUrl(request: CsdiPedestrianRequest): URL {
        val stops = JSONObject()
            .put(
                "features",
                JSONArray()
                    .put(feature("Start", request.start))
                    .put(feature("End", request.end))
            )
        val parameters = listOf(
            "stops" to stops.toString(),
            "travelMode" to "3",
            "directionsLengthUnits" to "esriNAUMeters",
            "directionsLanguage" to "en",
            "outSR" to "4326",
            "f" to "json",
            "returnZ" to "true",
            "directionStyleName" to "NA Campus"
        )
        return URL(
            BASE_URL + "?" + parameters.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
        )
    }

    private fun feature(name: String, coordinate: PedestrianCoordinate): JSONObject =
        JSONObject()
            .put("geometry", JSONObject().put("x", coordinate.longitude).put("y", coordinate.latitude))
            .put("attributes", JSONObject().put("Name", name))

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

enum class CsdiPedestrianFailureKind {
    NETWORK,
    TIMEOUT,
    HTTP_4XX,
    HTTP_5XX,
    NO_ROUTE,
    INVALID_RESPONSE,
    ENDPOINT_MISMATCH,
    CANCELLED,
    BACKOFF,
    QUEUE_FULL
}

class CsdiPedestrianParseException(
    message: String,
    val failureKind: CsdiPedestrianFailureKind
) : IllegalArgumentException(message)

sealed interface CsdiPedestrianResponse {
    data class Success(val route: PedestrianRoute) : CsdiPedestrianResponse
    data class Failure(val kind: CsdiPedestrianFailureKind) : CsdiPedestrianResponse
}

object CsdiPedestrianRouteParser {
    fun parse(body: String, request: CsdiPedestrianRequest): PedestrianRoute {
        return try {
            val root = JSONObject(body)
            val features = root.getJSONObject("routes").getJSONArray("features")
            if (features.length() == 0) {
                throw CsdiPedestrianParseException(
                    "CSDI response contains no route",
                    CsdiPedestrianFailureKind.NO_ROUTE
                )
            }
            val feature = features.getJSONObject(0)
            val attributes = feature.getJSONObject("attributes")
            val distance = attributes.getDouble("Total_Length")
            val time = attributes.getDouble("Total_Time")
            if (!distance.isFinite() || distance <= 0.0 || !time.isFinite() || time <= 0.0) {
                invalid("CSDI route distance and time must be finite and positive")
            }
            val rawPaths = feature.getJSONObject("geometry").getJSONArray("paths")
            if (rawPaths.length() == 0) invalid("CSDI route contains no path")
            val paths = buildList {
                repeat(rawPaths.length()) { pathIndex ->
                    val rawPath = rawPaths.getJSONArray(pathIndex)
                    if (rawPath.length() < 2) invalid("CSDI path needs at least two points")
                    add(
                        PedestrianRoutePath(
                            buildList {
                                repeat(rawPath.length()) { pointIndex ->
                                    val point = rawPath.getJSONArray(pointIndex)
                                    if (point.length() < 2) invalid("CSDI point has too few coordinates")
                                    val coordinate = PedestrianCoordinate(
                                        latitude = point.getDouble(1),
                                        longitude = point.getDouble(0)
                                    )
                                    if (!coordinate.isValidWgs84) invalid("CSDI point is not valid WGS84")
                                    add(coordinate)
                                }
                            }
                        )
                    )
                }
            }
            if (distanceMeters(paths.first().points.first(), request.start) > MAX_ENDPOINT_DEVIATION_METERS ||
                distanceMeters(paths.last().points.last(), request.end) > MAX_ENDPOINT_DEVIATION_METERS
            ) {
                throw CsdiPedestrianParseException(
                    "CSDI route endpoint is outside the accepted deviation",
                    CsdiPedestrianFailureKind.ENDPOINT_MISMATCH
                )
            }
            PedestrianRoute(distance, time, paths)
        } catch (exception: CsdiPedestrianParseException) {
            throw exception
        } catch (_: Exception) {
            invalid("CSDI route response is invalid")
        }
    }

    private fun invalid(message: String): Nothing =
        throw CsdiPedestrianParseException(message, CsdiPedestrianFailureKind.INVALID_RESPONSE)

    private fun distanceMeters(first: PedestrianCoordinate, second: PedestrianCoordinate): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MAX_ENDPOINT_DEVIATION_METERS = 30.0
}

interface CsdiPedestrianRouteSource {
    fun solve(
        request: CsdiPedestrianRequest,
        cancellationToken: PedestrianCancellationToken = PedestrianCancellationToken()
    ): CsdiPedestrianResponse
}

class HttpCsdiPedestrianRouteSource(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
    private val attemptTimeoutMillis: Int = DEFAULT_ATTEMPT_TIMEOUT_MILLIS
) : CsdiPedestrianRouteSource {
    init {
        require(attemptTimeoutMillis > 0) { "Attempt timeout must be positive" }
    }

    override fun solve(
        request: CsdiPedestrianRequest,
        cancellationToken: PedestrianCancellationToken
    ): CsdiPedestrianResponse {
        if (cancellationToken.isCancelled) {
            return CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.CANCELLED)
        }
        val connection = try {
            connectionFactory(CsdiPedestrianRequestBuilder.buildUrl(request))
        } catch (_: Exception) {
            return CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.NETWORK)
        }
        cancellationToken.onCancel(connection::disconnect)
        val timedOut = AtomicBoolean(false)
        val timeoutTask = TIMEOUT_EXECUTOR.schedule(
            {
                timedOut.set(true)
                connection.disconnect()
            },
            attemptTimeoutMillis.toLong(),
            TimeUnit.MILLISECONDS
        )
        val response = try {
            if (cancellationToken.isCancelled) {
                return CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.CANCELLED)
            }
            connection.requestMethod = "GET"
            connection.connectTimeout = attemptTimeoutMillis
            connection.readTimeout = attemptTimeoutMillis
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            when (connection.responseCode) {
                in 200..299 -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    try {
                        CsdiPedestrianResponse.Success(CsdiPedestrianRouteParser.parse(body, request))
                    } catch (exception: CsdiPedestrianParseException) {
                        CsdiPedestrianResponse.Failure(exception.failureKind)
                    }
                }
                in 400..499 -> CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.HTTP_4XX)
                in 500..599 -> CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.HTTP_5XX)
                else -> CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.NETWORK)
            }
        } catch (_: SocketTimeoutException) {
            CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.TIMEOUT)
        } catch (_: IOException) {
            if (cancellationToken.isCancelled) {
                CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.CANCELLED)
            } else if (timedOut.get()) {
                CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.TIMEOUT)
            } else {
                CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.NETWORK)
            }
        } catch (_: Exception) {
            CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.INVALID_RESPONSE)
        } finally {
            timeoutTask.cancel(false)
            connection.disconnect()
        }
        return if (
            timedOut.get() &&
            response != CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.CANCELLED)
        ) {
            CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.TIMEOUT)
        } else {
            response
        }
    }

    private companion object {
        const val DEFAULT_ATTEMPT_TIMEOUT_MILLIS = 8_000

        val TIMEOUT_EXECUTOR = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "csdi-attempt-timeout").apply { isDaemon = true }
        }.apply {
            removeOnCancelPolicy = true
        }
    }
}
