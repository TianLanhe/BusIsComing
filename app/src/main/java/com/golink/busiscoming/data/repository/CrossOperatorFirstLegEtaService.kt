package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.CrossOperatorEtaQuery
import com.golink.busiscoming.data.model.EtaArrival
import com.golink.busiscoming.data.model.EtaUnavailableReason
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.WaitTimeState
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

data class FirstLegStopIdentity(
    val boardingCtbStopId: String,
    val alightingCtbStopId: String
)

fun interface FirstLegStopIdentityResolver {
    fun resolve(query: FirstLegEtaQuery): FirstLegStopIdentity?
}

sealed interface EtaSourceResult {
    data class Success(val arrivals: List<EtaArrival>) : EtaSourceResult
    data class Failure(val reason: EtaUnavailableReason) : EtaSourceResult
}

class KmbFirstLegEtaSource(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val fetcher: (URL) -> String = ::fetchKmbEta
) {
    fun query(query: CrossOperatorEtaQuery, language: String): EtaSourceResult {
        val response = try {
            fetcher(buildUrl(query))
        } catch (_: Exception) {
            return EtaSourceResult.Failure(EtaUnavailableReason.ETA_REQUEST_FAILED)
        }
        return try {
            val root = JSONObject(response)
            val data = root.optJSONArray("data")
                ?: return EtaSourceResult.Failure(EtaUnavailableReason.ETA_RESPONSE_INVALID)
            val rootTimestamp = root.optString("generated_timestamp").toIsoMillis()
            val arrivals = buildList {
                for (index in 0 until data.length()) {
                    val record = data.optJSONObject(index) ?: continue
                    val operator = BusOperator.fromCode(record.optString("co")) ?: continue
                    if (
                        operator != query.operator ||
                        record.optString("route") != query.route ||
                        record.optString("dir") != query.direction ||
                        record.optString("service_type") != query.serviceType
                    ) continue
                    val etaMillis = record.optString("eta").toIsoMillis() ?: continue
                    val sourceSequence = record.optInt("eta_seq", index + 1)
                    val destination = selectOfficialField(record, "dest", language)
                    val remark = selectOfficialField(record, "rmk", language)
                    add(
                        EtaArrival(
                            sequence = sourceSequence,
                            minutes = waitMinutes(etaMillis),
                            etaMillis = etaMillis,
                            arrivalTimeText = formatArrivalTime(etaMillis),
                            destination = destination?.first,
                            destinationLanguage = destination?.second,
                            remark = remark?.first,
                            remarkLanguage = remark?.second,
                            dataTimestampMillis = record.optString("data_timestamp").toIsoMillis()
                                ?: rootTimestamp,
                            operator = operator,
                            sourceSequence = sourceSequence
                        )
                    )
                }
            }
            EtaSourceResult.Success(
                arrivals.sortedWith(
                    compareBy<EtaArrival> { it.etaMillis ?: Long.MAX_VALUE }
                        .thenBy { it.sourceSequence }
                )
            )
        } catch (_: Exception) {
            EtaSourceResult.Failure(EtaUnavailableReason.ETA_RESPONSE_INVALID)
        }
    }

    fun buildUrl(query: CrossOperatorEtaQuery): URL {
        return URL("$BASE_URL/${query.boardingStopId}/${query.route}/${query.serviceType}")
    }

    private fun waitMinutes(etaMillis: Long): Int {
        val remaining = etaMillis - clock()
        if (remaining <= 0) return 0
        return ((remaining + 59_999L) / 60_000L).toInt()
    }

    private fun selectOfficialField(
        record: JSONObject,
        prefix: String,
        citybusLanguage: String
    ): Pair<String, String>? {
        val order = when (citybusLanguage) {
            "2" -> listOf("sc", "tc", "en")
            "1" -> listOf("en", "tc", "sc")
            else -> listOf("tc", "sc", "en")
        }
        return order.firstNotNullOfOrNull { language ->
            record.optString("${prefix}_$language").trim().takeIf(String::isNotBlank)
                ?.let { it to language }
        }
    }

    private fun String.toIsoMillis(): Long? {
        if (isBlank()) return null
        return try {
            ISO_FORMAT.get()!!.parse(this)?.time
        } catch (_: ParseException) {
            null
        }
    }

    private fun formatArrivalTime(timestamp: Long): String =
        TIME_FORMAT.get()!!.format(Date(timestamp))

    companion object {
        private const val BASE_URL = "https://data.etabus.gov.hk/v1/transport/kmb/eta"
        private val ISO_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        }
        private val TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("HH:mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
            }
        }
    }
}

object CrossOperatorEtaMerger {
    fun merge(citybus: WaitTimeState, partner: EtaSourceResult): WaitTimeState {
        val citybusArrivals = (citybus as? WaitTimeState.Available)?.arrivals.orEmpty()
        val partnerArrivals = (partner as? EtaSourceResult.Success)?.arrivals.orEmpty()
        val merged = (citybusArrivals + partnerArrivals)
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<EtaArrival>> { it.value.etaMillis ?: Long.MAX_VALUE }
                    .thenBy { it.value.operator.code }
                    .thenBy { it.value.sourceSequence }
                    .thenBy { it.index }
            )
            .mapIndexed { index, value -> value.value.copy(sequence = index + 1) }
        if (merged.isNotEmpty()) return WaitTimeState.Available(merged)
        val citybusSuccessfulEmpty = citybus == WaitTimeState.NoArrivals
        val partnerSuccessfulEmpty = partner is EtaSourceResult.Success
        return if (citybusSuccessfulEmpty && partnerSuccessfulEmpty) {
            WaitTimeState.NoArrivals
        } else {
            val reason = when {
                citybus is WaitTimeState.Unavailable -> citybus.reason
                partner is EtaSourceResult.Failure -> partner.reason
                else -> EtaUnavailableReason.UNEXPECTED_ERROR
            }
            WaitTimeState.Unavailable(reason)
        }
    }
}

class CrossOperatorFirstLegEtaService(
    private val citybusResolver: (FirstLegEtaQuery) -> WaitTimeState,
    private val stopIdentityResolver: (FirstLegEtaQuery) -> FirstLegStopIdentity?,
    private val mappingResolver: (
        FirstLegEtaQuery,
        boardingCtbStopId: String,
        alightingCtbStopId: String
    ) -> CrossOperatorMappingResolution,
    private val partnerSource: (CrossOperatorEtaQuery, String) -> EtaSourceResult
) {
    fun resolveWaitTime(query: FirstLegEtaQuery): WaitTimeState {
        return resolveWaitTimeProgressively(query) { }
    }

    fun resolveWaitTimeProgressively(
        query: FirstLegEtaQuery,
        onUpdate: (WaitTimeState) -> Unit
    ): WaitTimeState {
        val citybus = citybusResolver(query)
        onUpdate(citybus)
        val identity = stopIdentityResolver(query) ?: return citybus
        val mapping = mappingResolver(
            query,
            identity.boardingCtbStopId,
            identity.alightingCtbStopId
        ) as? CrossOperatorMappingResolution.Enabled ?: return citybus
        val merged = CrossOperatorEtaMerger.merge(
            citybus,
            partnerSource(mapping.query, query.lang)
        )
        onUpdate(merged)
        return merged
    }
}

class P2pFirstLegStopIdentityResolver(
    private val stopMapResolver: CitybusP2pStopMapResolver
) : FirstLegStopIdentityResolver {
    override fun resolve(query: FirstLegEtaQuery): FirstLegStopIdentity? {
        val leg = P2pRouteLeg(
            query.company,
            query.routeVariant,
            query.route,
            query.boardingSeq,
            query.alightingSeq,
            query.bound,
            query.directionPath
        )
        val stopMap = runCatching {
            stopMapResolver.resolveStopMap(
                query.rawInfo,
                query.lang,
                P2pRoutePlan(query.rawInfo, query.lang, listOf(leg))
            )
        }.getOrNull() ?: return null
        val boarding = stopMap.findStop(0, query.routeVariant, query.boardingSeq)?.stopId
            ?: return null
        val alighting = stopMap.findStop(0, query.routeVariant, query.alightingSeq)?.stopId
            ?: return null
        return FirstLegStopIdentity(boarding, alighting)
    }
}

private fun fetchKmbEta(url: URL): String {
    val connection = url.openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 20_000
        connection.readTimeout = 20_000
        val status = connection.responseCode
        if (status !in 200..299) throw IOException("KMB ETA failed with HTTP $status")
        readLimited(connection.inputStream, MAX_KMB_ETA_BODY_BYTES).toString(Charsets.UTF_8)
    } finally {
        connection.disconnect()
    }
}

private const val MAX_KMB_ETA_BODY_BYTES = 2 * 1024 * 1024
