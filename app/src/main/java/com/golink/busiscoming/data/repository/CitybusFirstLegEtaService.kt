package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.EtaArrival
import com.golink.busiscoming.data.model.EtaUnavailableReason
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.WaitTimeState
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
        if (!query.hasRequiredFirstLegData()) {
            return WaitTimeState.Unavailable(EtaUnavailableReason.MISSING_FIRST_LEG_DATA)
        }
        val leg = query.toRouteLeg()
        val stopMap = try {
            stopMapResolver.resolveStopMap(
                rawInfo = query.rawInfo,
                lang = query.lang,
                plan = P2pRoutePlan(query.rawInfo, query.lang, listOf(leg))
            )
        } catch (_: Exception) {
            return WaitTimeState.Unavailable(EtaUnavailableReason.STOP_MAP_REQUEST_FAILED)
        } ?: return WaitTimeState.Unavailable(EtaUnavailableReason.STOP_MAP_RESPONSE_INVALID)

        val stopId = stopMap.findStop(0, leg.routeVariant, leg.boardingSeq)?.stopId
            ?: return WaitTimeState.Unavailable(EtaUnavailableReason.BOARDING_STOP_NOT_FOUND)
        val etaResponse = try {
            etaFetcher(buildEtaUrl(query.company, stopId, query.route))
        } catch (_: Exception) {
            return WaitTimeState.Unavailable(EtaUnavailableReason.ETA_REQUEST_FAILED)
        }
        if (!hasValidEtaDataArray(etaResponse)) {
            return WaitTimeState.Unavailable(EtaUnavailableReason.ETA_RESPONSE_INVALID)
        }

        val arrivals = parseArrivals(
            response = etaResponse,
            query = query,
            stopId = stopId
        )
        return if (arrivals.isEmpty()) {
            WaitTimeState.NoArrivals
        } else {
            WaitTimeState.Available(arrivals)
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
    ): List<EtaArrival> {
        val records = parseEtaRecords(response, query.lang)
        val strictRecords = records
            .filter { it.matchesRouteStopAndDirection(query, stopId) }
            .filter { it.seq == query.boardingSeq }

        val matchedRecords = strictRecords.ifEmpty {
            records
            .filter { it.matchesRouteStopAndDirection(query, stopId) }
        }

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
                    destinationLanguage = record.destinationLanguage,
                    remark = record.remark,
                    remarkLanguage = record.remarkLanguage,
                    dataTimestampMillis = record.dataTimestampMillis
                )
            }
    }

    private fun FirstLegEtaQuery.hasRequiredFirstLegData(): Boolean {
        return company.isNotBlank() &&
            routeVariant.isNotBlank() &&
            route.isNotBlank() &&
            boardingSeq > 0 &&
            bound.isNotBlank() &&
            rawInfo.isNotBlank() &&
            lang.isNotBlank()
    }

    private fun FirstLegEtaQuery.toRouteLeg(): P2pRouteLeg {
        return P2pRouteLeg(
            company = company,
            routeVariant = routeVariant,
            route = route,
            boardingSeq = boardingSeq,
            alightingSeq = alightingSeq,
            bound = bound,
            directionPath = directionPath
        )
    }

    private fun hasValidEtaDataArray(response: String): Boolean {
        val trimmed = response.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        val dataArrayStart = DATA_ARRAY_PATTERN.find(trimmed)?.range?.last ?: return false
        var depth = 0
        var inString = false
        var escaped = false
        for (index in dataArrayStart until trimmed.length) {
            val char = trimmed[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '[' -> depth += 1
                ']' -> {
                    depth -= 1
                    if (depth == 0) return true
                }
            }
        }
        return false
    }

    private fun parseEtaRecords(response: String, language: String): List<EtaRecord> {
        val responseTimestampMillis = parseResponseTimestamp(response)
        return parseCitybusJsonObjects(response).mapNotNull { fields ->
            val etaMillis = fields["eta"]
                ?.takeIf { it.isNotBlank() }
                ?.toHongKongIsoMillis()
                ?: return@mapNotNull null

            val destination = selectOfficialField(fields, "dest", language)
            val remark = selectOfficialField(fields, "rmk", language)
            EtaRecord(
                route = fields["route"].orEmpty(),
                stop = fields["stop"].orEmpty(),
                direction = fields["dir"].orEmpty(),
                seq = fields["seq"]?.toIntOrNull(),
                etaSequence = fields["eta_seq"]?.toIntOrNull(),
                etaMillis = etaMillis,
                destination = destination?.value,
                destinationLanguage = destination?.language,
                remark = remark?.value,
                remarkLanguage = remark?.language,
                dataTimestampMillis = fields["data_timestamp"]
                    ?.takeIf { it.isNotBlank() }
                    ?.toHongKongIsoMillis()
                    ?: responseTimestampMillis
            )
        }
    }

    private fun selectOfficialField(
        fields: Map<String, String>,
        prefix: String,
        citybusLanguage: String
    ): OfficialFieldValue? {
        val languageOrder = when (citybusLanguage) {
            "2" -> listOf("sc", "tc", "en")
            "1" -> listOf("en", "tc", "sc")
            else -> listOf("tc", "sc", "en")
        }
        return languageOrder.firstNotNullOfOrNull { language ->
            fields["${prefix}_$language"]
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { OfficialFieldValue(it, language) }
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
        val destinationLanguage: String?,
        val remark: String?,
        val remarkLanguage: String?,
        val dataTimestampMillis: Long?
    )

    private data class OfficialFieldValue(
        val value: String,
        val language: String
    )

    companion object {
        private const val BASE_URL = "https://rt.data.gov.hk/v2/transport/citybus"
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MAX_ETA_ARRIVALS = 3
        private val DATA_ARRAY_PATTERN = Regex(""""data"\s*:\s*\[""")
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
