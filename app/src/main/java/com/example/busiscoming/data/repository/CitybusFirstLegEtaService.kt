package com.example.busiscoming.data.repository

import com.example.busiscoming.data.model.FirstLegEtaQuery
import com.example.busiscoming.data.model.EtaArrival
import com.example.busiscoming.data.model.P2pRouteLeg
import com.example.busiscoming.data.model.WaitTimeState
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CitybusFirstLegEtaService(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val etaFetcher: (URL) -> String = ::fetchCitybusPublicApi,
    private val stopMapResolver: CitybusP2pStopMapResolver = CitybusP2pStopMapResolver(clock = clock)
) {
    fun resolveWaitTime(query: FirstLegEtaQuery): WaitTimeState {
        return runCatching {
            val stopId = findStopId(query) ?: return WaitTimeState.Unavailable
            val etaResponse = etaFetcher(buildEtaUrl(query.company, stopId, query.route))
            val arrivals = parseArrivals(
                response = etaResponse,
                query = query,
                stopId = stopId
            ) ?: return WaitTimeState.Unavailable
            WaitTimeState.Available(arrivals)
        }.getOrElse {
            WaitTimeState.Unavailable
        }
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
        if (query.rawInfo.isBlank()) return null
        return stopMapResolver.findStopId(
            leg = P2pRouteLeg(
                company = query.company,
                routeVariant = query.routeVariant,
                route = query.route,
                boardingSeq = query.boardingSeq,
                alightingSeq = query.alightingSeq,
                bound = query.bound,
                directionPath = query.directionPath
            ),
            rawInfo = query.rawInfo,
            lang = query.lang,
            legIndex = 0
        )
    }

    /**
     * Historical helper retained for comparing the old public route-stop path during diagnostics.
     * Runtime ETA stopId resolution uses CitybusP2P `showstops2.php` instead.
     */
    fun buildHistoricalRouteStopUrl(company: String, route: String, directionPath: String): URL {
        return CitybusRouteStopResolver(clock = clock).buildRouteStopUrl(
            company = company,
            route = route,
            directionPath = directionPath
        )
    }

    private fun parseArrivals(
        response: String,
        query: FirstLegEtaQuery,
        stopId: String
    ): List<EtaArrival>? {
        val records = parseEtaRecords(response)
        val strictRecords = records
            .filter { it.matchesRouteStopAndDirection(query, stopId) }
            .filter { it.seq == query.boardingSeq }

        val matchedRecords = strictRecords.ifEmpty {
            records
            .filter { it.matchesRouteStopAndDirection(query, stopId) }
        }

        if (matchedRecords.isEmpty()) return null
        return matchedRecords
            .sortedWith(
                compareBy<EtaRecord> { it.etaSequence ?: Int.MAX_VALUE }
                    .thenBy { it.etaMillis }
            )
            .take(MAX_ETA_ARRIVALS)
            .mapIndexed { index, record ->
                EtaArrival(
                    sequence = record.etaSequence ?: index + 1,
                    minutes = calculateWaitMinutes(record.etaMillis),
                    etaMillis = record.etaMillis,
                    arrivalTimeText = formatArrivalTime(record.etaMillis),
                    destination = record.destination,
                    remark = record.remark,
                    dataTimestampMillis = record.dataTimestampMillis
                )
            }
            .takeIf { it.isNotEmpty() }
    }

    private fun parseEtaRecords(response: String): List<EtaRecord> {
        val responseTimestampMillis = parseResponseTimestamp(response)
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
                etaSequence = fields["eta_seq"]?.toIntOrNull(),
                etaMillis = etaMillis,
                destination = fields["dest_tc"]?.trim()?.takeIf { it.isNotBlank() },
                remark = fields["rmk_tc"]?.trim()?.takeIf { it.isNotBlank() },
                dataTimestampMillis = fields["data_timestamp"]
                    ?.takeIf { it.isNotBlank() }
                    ?.toHongKongIsoMillis()
                    ?: responseTimestampMillis
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

    private fun parseResponseTimestamp(response: String): Long? {
        val value = RESPONSE_TIMESTAMP_PATTERN.find(response)?.groupValues?.getOrNull(2)
            ?: return null
        return value.toHongKongIsoMillis()
    }

    private fun formatArrivalTime(etaMillis: Long): String {
        return ARRIVAL_TIME_FORMAT.get()!!.format(Date(etaMillis))
    }

    private data class EtaRecord(
        val route: String,
        val stop: String,
        val direction: String,
        val seq: Int?,
        val etaSequence: Int?,
        val etaMillis: Long,
        val destination: String?,
        val remark: String?,
        val dataTimestampMillis: Long?
    )

    companion object {
        private const val BASE_URL = "https://rt.data.gov.hk/v2/transport/citybus"
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MAX_ETA_ARRIVALS = 3
        private val RESPONSE_TIMESTAMP_PATTERN = Regex(
            """"(generated_timestamp|data_timestamp)"\s*:\s*"([^"]+)""""
        )
        private val ETA_DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            }
        }
        private val ARRIVAL_TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("HH:mm", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
                }
            }
        }
    }
}
