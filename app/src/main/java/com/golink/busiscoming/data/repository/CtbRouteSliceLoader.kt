package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.CtbRouteSlice
import com.golink.busiscoming.data.model.StaticRouteVariant
import com.golink.busiscoming.data.model.StaticStopRecord
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

interface CtbRouteSliceSource {
    fun fetchRouteStops(route: String, direction: String): String
    fun fetchStop(stopId: String): String
}

interface CtbRouteSliceStore {
    fun loadCtbRouteSlice(route: String, direction: String): CtbRouteSlice?
    fun saveCtbRouteSlices(slices: List<CtbRouteSlice>)
}

class CtbRouteSliceLoader(
    private val source: CtbRouteSliceSource,
    private val store: CtbRouteSliceStore
) {
    private val routeFlights = ConcurrentHashMap<String, FutureTask<List<CtbRouteSlice>>>()
    private val stopFlights = ConcurrentHashMap<String, FutureTask<StaticStopRecord>>()
    private val cacheDayLock = Any()
    private var cacheDataDay: String? = null

    fun loadRoute(route: String, dataDay: String): List<CtbRouteSlice> {
        require(route.isNotBlank() && dataDay.isNotBlank())
        synchronized(cacheDayLock) {
            if (cacheDataDay != dataDay) {
                stopFlights.clear()
                cacheDataDay = dataDay
            }
        }
        val key = "$dataDay|$route"
        val created = FutureTask { loadRouteUnshared(route, dataDay) }
        val task = routeFlights.putIfAbsent(key, created) ?: created.also(FutureTask<List<CtbRouteSlice>>::run)
        return try {
            task.get()
        } catch (exception: ExecutionException) {
            val cause = exception.cause ?: exception
            if (cause is Exception) throw cause
            throw IOException("CTB route slice loading failed", cause)
        } finally {
            if (task.isDone) routeFlights.remove(key, task)
        }
    }

    private fun loadRouteUnshared(route: String, dataDay: String): List<CtbRouteSlice> {
        val routeStops = DIRECTIONS.associateWith { direction ->
            CrossOperatorStaticParsers.parseCtbRouteStops(
                source.fetchRouteStops(route, direction),
                direction
            ).also { records ->
                if (records.isEmpty() || records.any { it.route != route }) {
                    throw StaticDataValidationException("CTB route-stop response is incomplete")
                }
            }
        }
        val stopIds = routeStops.values.flatten().map { it.stopId }.distinct()
        val stops = stopIds.associateWith { stopId -> loadStop(stopId, dataDay) }
        val slices = DIRECTIONS.map { direction ->
            val variant = CrossOperatorStaticParsers.buildVariants(
                routeStops.getValue(direction),
                stops
            ).singleOrNull() ?: throw StaticDataValidationException("CTB direction has no unique variant")
            require(variant.operator == BusOperator.CTB)
            CtbRouteSlice(
                route = route,
                direction = direction,
                verifiedDataDay = dataDay,
                fingerprint = RouteSemanticFingerprint.of(variant),
                stops = variant.stops
            )
        }
        store.saveCtbRouteSlices(slices)
        return slices
    }

    private fun loadStop(stopId: String, dataDay: String): StaticStopRecord {
        val key = "$dataDay|$stopId"
        val created = FutureTask { CrossOperatorStaticParsers.parseCtbStop(source.fetchStop(stopId)) }
        val task = stopFlights.putIfAbsent(key, created) ?: created.also(FutureTask<StaticStopRecord>::run)
        return try {
            task.get()
        } catch (exception: ExecutionException) {
            stopFlights.remove(key, task)
            val cause = exception.cause ?: exception
            if (cause is Exception) throw cause
            throw IOException("CTB stop loading failed", cause)
        }
    }

    companion object {
        val DIRECTIONS = listOf("outbound", "inbound")
    }
}

class CitybusStaticDataHttpSource : CtbRouteSliceSource {
    override fun fetchRouteStops(route: String, direction: String): String {
        return fetch(URL("$BASE_URL/route-stop/CTB/$route/$direction"))
    }

    override fun fetchStop(stopId: String): String {
        return fetch(URL("$BASE_URL/stop/$stopId"))
    }

    private fun fetch(url: URL): String {
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Citybus static request failed with HTTP $status")
            readLimited(connection.inputStream, MAX_BODY_BYTES).toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE_URL = "https://rt.data.gov.hk/v2/transport/citybus"
        private const val TIMEOUT_MILLIS = 20_000
        private const val MAX_BODY_BYTES = 4 * 1024 * 1024
    }
}
