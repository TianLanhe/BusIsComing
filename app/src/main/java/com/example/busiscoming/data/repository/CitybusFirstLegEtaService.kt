package com.example.busiscoming.data.repository

import com.example.busiscoming.data.model.FirstLegEtaQuery
import com.example.busiscoming.data.model.WaitTimeState
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class CitybusFirstLegEtaService(
    private val clock: () -> Long = { System.currentTimeMillis() },
    routeStopFetcher: (URL) -> String = ::fetchCitybusPublicApi,
    private val etaFetcher: (URL) -> String = ::fetchCitybusPublicApi,
    routeStopCacheTtlMillis: Long = ROUTE_STOP_CACHE_TTL_MILLIS,
    private val routeStopResolver: CitybusRouteStopResolver = CitybusRouteStopResolver(
        clock = clock,
        routeStopFetcher = routeStopFetcher,
        cacheTtlMillis = routeStopCacheTtlMillis
    )
) {
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
        return routeStopResolver.buildRouteStopUrl(company, route, directionPath)
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
        return routeStopResolver.findStopId(
            company = query.company,
            route = query.route,
            directionPath = query.directionPath,
            sequence = query.boardingSeq
        )
    }

    private fun parseNearestEtaMillis(
        response: String,
        query: FirstLegEtaQuery,
        stopId: String
    ): Long? {
        val records = parseEtaRecords(response)
        val strictEtaMillis = records
            .filter { it.matchesRouteStopAndDirection(query, stopId) }
            .filter { it.seq == query.boardingSeq }
            .map { it.etaMillis }

        return strictEtaMillis.minOrNull() ?: records
            .filter { it.matchesRouteStopAndDirection(query, stopId) }
            .map { it.etaMillis }
            .minOrNull()
    }

    private fun parseEtaRecords(response: String): List<EtaRecord> {
        return parseCitybusJsonObjects(response).mapNotNull { fields ->
            val etaMillis = fields["eta"]
                ?.takeIf { it.isNotBlank() }
                ?.toHongKongIsoMillis()
                ?: return@mapNotNull null

            EtaRecord(
                route = fields["route"].orEmpty(),
                stop = fields["stop"].orEmpty(),
                direction = fields["dir"].orEmpty(),
                seq = fields["seq"]?.toIntOrNull(),
                etaMillis = etaMillis
            )
        }
    }

    private fun EtaRecord.matchesRouteStopAndDirection(query: FirstLegEtaQuery, stopId: String): Boolean {
        return route == query.route && stop == stopId && direction == query.bound
    }

    private fun String.toHongKongIsoMillis(): Long? {
        return try {
            ETA_DATE_FORMAT.get()!!.parse(this)?.time
        } catch (_: ParseException) {
            null
        }
    }

    private data class EtaRecord(
        val route: String,
        val stop: String,
        val direction: String,
        val seq: Int?,
        val etaMillis: Long
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
