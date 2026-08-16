package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.CrossOperatorEtaQuery
import com.golink.busiscoming.data.model.CrossOperatorMatchStatus
import com.golink.busiscoming.data.model.CrossOperatorRouteMatch
import com.golink.busiscoming.data.model.CrossOperatorStopPair
import com.golink.busiscoming.data.model.StaticRouteStop
import com.golink.busiscoming.data.model.StaticRouteVariant
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object HongKongDataDay {
    private const val CUTOFF_MILLIS = (5L * 60L + 15L) * 60_000L
    private val formatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
            }
        }
    }

    fun forInstant(timestampMillis: Long): String {
        return formatter.get()!!.format(Date(timestampMillis - CUTOFF_MILLIS))
    }
}

object RouteSemanticFingerprint {
    fun of(variant: StaticRouteVariant): String {
        val payload = buildString {
            append(variant.operator.code)
            append('|').append(variant.route)
            append('|').append(variant.direction)
            append('|').append(variant.serviceType)
            variant.stops.sortedBy { it.sequence }.forEach { stop ->
                append('\n').append(stop.sequence)
                append('|').append(stop.id)
                append('|').append(String.format(Locale.US, "%.7f", stop.latitude))
                append('|').append(String.format(Locale.US, "%.7f", stop.longitude))
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

class CrossOperatorRouteMatcher(
    private val gapCostMeters: Double = DEFAULT_GAP_COST_METERS,
    private val thresholdMetersPerStop: Double = DEFAULT_THRESHOLD_METERS_PER_STOP,
    private val algorithmVersion: Int = ALGORITHM_VERSION
) {
    fun match(
        ctb: StaticRouteVariant,
        candidates: List<StaticRouteVariant>
    ): CrossOperatorRouteMatch {
        val ranked = candidates
            .filter { it.route == ctb.route && it.stops.isNotEmpty() }
            .map { candidate -> align(ctb, candidate) }
            .sortedWith(
                compareBy<Alignment> { it.normalizedCost }
                    .thenBy { it.rawCost }
                    .thenBy { it.variant.operator.code }
                    .thenBy { it.variant.direction }
                    .thenBy { it.variant.serviceType.toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.variant.serviceType }
            )

        val winner = ranked.firstOrNull()
        if (winner == null || winner.normalizedCost > thresholdMetersPerStop) {
            return CrossOperatorRouteMatch(
                status = CrossOperatorMatchStatus.NO_MATCH,
                winner = winner?.variant,
                rawCost = winner?.rawCost ?: Double.POSITIVE_INFINITY,
                normalizedCost = winner?.normalizedCost ?: Double.POSITIVE_INFINITY,
                stopPairs = emptyList(),
                algorithmVersion = algorithmVersion,
                gapCostMeters = gapCostMeters,
                thresholdMetersPerStop = thresholdMetersPerStop
            )
        }
        return CrossOperatorRouteMatch(
            status = CrossOperatorMatchStatus.MATCHED,
            winner = winner.variant,
            rawCost = winner.rawCost,
            normalizedCost = winner.normalizedCost,
            stopPairs = winner.stopPairs,
            algorithmVersion = algorithmVersion,
            gapCostMeters = gapCostMeters,
            thresholdMetersPerStop = thresholdMetersPerStop
        )
    }

    private fun align(ctb: StaticRouteVariant, candidate: StaticRouteVariant): Alignment {
        val left = ctb.stops.sortedBy { it.sequence }
        val right = candidate.stops.sortedBy { it.sequence }
        val rows = left.size + 1
        val columns = right.size + 1
        val cost = Array(rows) { DoubleArray(columns) }
        val operation = Array(rows) { ByteArray(columns) }
        for (i in 1 until rows) {
            cost[i][0] = i * gapCostMeters
            operation[i][0] = SKIP_LEFT
        }
        for (j in 1 until columns) {
            cost[0][j] = j * gapCostMeters
            operation[0][j] = SKIP_RIGHT
        }
        for (i in 1 until rows) {
            for (j in 1 until columns) {
                val diagonal = cost[i - 1][j - 1] + distanceMeters(left[i - 1], right[j - 1])
                val skipLeft = cost[i - 1][j] + gapCostMeters
                val skipRight = cost[i][j - 1] + gapCostMeters
                val best = min(diagonal, min(skipLeft, skipRight))
                cost[i][j] = best
                operation[i][j] = when {
                    diagonal <= best + EPSILON -> DIAGONAL
                    skipLeft <= best + EPSILON -> SKIP_LEFT
                    else -> SKIP_RIGHT
                }
            }
        }

        val pairs = mutableListOf<CrossOperatorStopPair>()
        var i = left.size
        var j = right.size
        while (i > 0 || j > 0) {
            when (operation[i][j]) {
                DIAGONAL -> {
                    val ctbStop = left[i - 1]
                    val operatorStop = right[j - 1]
                    pairs += CrossOperatorStopPair(
                        ctbStopId = ctbStop.id,
                        operatorStopId = operatorStop.id,
                        operatorSequence = operatorStop.sequence,
                        distanceMeters = distanceMeters(ctbStop, operatorStop)
                    )
                    i -= 1
                    j -= 1
                }
                SKIP_LEFT -> i -= 1
                SKIP_RIGHT -> j -= 1
                else -> {
                    if (i > 0) i -= 1 else j -= 1
                }
            }
        }
        pairs.reverse()
        val rawCost = cost[left.size][right.size]
        return Alignment(
            variant = candidate,
            rawCost = rawCost,
            normalizedCost = rawCost / max(left.size, right.size),
            stopPairs = pairs
        )
    }

    private fun distanceMeters(first: StaticRouteStop, second: StaticRouteStop): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return EARTH_RADIUS_METERS * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private data class Alignment(
        val variant: StaticRouteVariant,
        val rawCost: Double,
        val normalizedCost: Double,
        val stopPairs: List<CrossOperatorStopPair>
    )

    companion object {
        const val DEFAULT_GAP_COST_METERS = 100.0
        const val DEFAULT_THRESHOLD_METERS_PER_STOP = 46.0
        const val ALGORITHM_VERSION = 1
        private const val EARTH_RADIUS_METERS = 6_371_000.0
        private const val EPSILON = 0.0000001
        private const val DIAGONAL: Byte = 1
        private const val SKIP_LEFT: Byte = 2
        private const val SKIP_RIGHT: Byte = 3
    }
}

object P2pCrossOperatorGate {
    fun resolve(
        match: CrossOperatorRouteMatch,
        boardingCtbStopId: String,
        alightingCtbStopId: String
    ): CrossOperatorEtaQuery? {
        if (match.status != CrossOperatorMatchStatus.MATCHED) return null
        val winner = match.winner ?: return null
        val boarding = match.stopPairs.filter { it.ctbStopId == boardingCtbStopId }.singleOrNull()
            ?: return null
        val alighting = match.stopPairs.filter { it.ctbStopId == alightingCtbStopId }.singleOrNull()
            ?: return null
        if (boarding.operatorSequence >= alighting.operatorSequence) return null
        return CrossOperatorEtaQuery(
            operator = winner.operator,
            route = winner.route,
            direction = winner.direction,
            serviceType = winner.serviceType,
            boardingStopId = boarding.operatorStopId,
            alightingStopId = alighting.operatorStopId
        )
    }
}

