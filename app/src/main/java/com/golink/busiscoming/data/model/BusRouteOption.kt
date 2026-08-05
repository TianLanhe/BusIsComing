package com.golink.busiscoming.data.model

import java.util.Locale

data class BusRouteOption(
    val routeName: String,
    val routeSegments: List<String>,
    val priceHkd: Double,
    val durationMinutes: Int,
    val arrivalMinutes: Int,
    val transferCount: Int,
    val walkingDistanceMeters: Int,
    val waitTimeState: WaitTimeState = WaitTimeState.Available(arrivalMinutes),
    val firstLegEtaQuery: FirstLegEtaQuery? = null,
    val routeDetailQuery: P2pRouteDetailQuery? = null,
    val stopPreview: RouteCardStopPreview? = null,
    val resultId: String = buildBusRouteResultId(
        routeSegments,
        priceHkd,
        durationMinutes,
        walkingDistanceMeters
    )
)

sealed class WaitTimeState {
    object Loading : WaitTimeState()
    class Available(val arrivals: List<EtaArrival>) : WaitTimeState() {
        constructor(minutes: Int) : this(listOf(EtaArrival(sequence = 1, minutes = minutes)))

        val minutes: Int
            get() = arrivals.firstOrNull()?.minutes ?: 0

        val nextArrival: EtaArrival?
            get() = arrivals.getOrNull(1)

        override fun equals(other: Any?): Boolean {
            return other is Available && minutes == other.minutes
        }

        override fun hashCode(): Int {
            return minutes
        }

        override fun toString(): String {
            return "Available(minutes=$minutes, arrivals=$arrivals)"
        }
    }
    object NoArrivals : WaitTimeState()
    data class Unavailable(val reason: EtaUnavailableReason) : WaitTimeState()
}

enum class EtaUnavailableReason {
    MISSING_FIRST_LEG_DATA,
    STOP_MAP_REQUEST_FAILED,
    STOP_MAP_RESPONSE_INVALID,
    BOARDING_STOP_NOT_FOUND,
    ETA_REQUEST_FAILED,
    ETA_RESPONSE_INVALID,
    UNEXPECTED_ERROR
}

data class EtaArrival(
    val sequence: Int,
    val minutes: Int,
    val etaMillis: Long? = null,
    val arrivalTimeText: String = "",
    val destination: String? = null,
    val destinationLanguage: String? = null,
    val remark: String? = null,
    val remarkLanguage: String? = null,
    val dataTimestampMillis: Long? = null
)

fun WaitTimeState.toDisplayText(): String {
    return when (this) {
        is WaitTimeState.Available -> minutes.toString()
        WaitTimeState.Loading -> "..."
        WaitTimeState.NoArrivals,
        is WaitTimeState.Unavailable -> "-"
    }
}

data class FirstLegEtaQuery(
    val company: String,
    val routeVariant: String,
    val route: String,
    val boardingSeq: Int,
    val alightingSeq: Int,
    val bound: String,
    val directionPath: String,
    val rawInfo: String = "",
    val lang: String = "0"
) {
    fun requestKey(): FirstLegEtaRequestKey {
        return FirstLegEtaRequestKey(
            company = company,
            routeVariant = routeVariant,
            route = route,
            boardingSeq = boardingSeq,
            bound = bound,
            directionPath = directionPath
        )
    }
}

data class FirstLegEtaRequestKey(
    val company: String,
    val routeVariant: String,
    val route: String,
    val boardingSeq: Int,
    val bound: String,
    val directionPath: String
)

data class P2pRouteDetailQuery(
    val rawInfo: String,
    val generalInfo: String,
    val listId: String,
    val lang: String,
    val plan: P2pRoutePlan,
    val sessionRef: String? = null,
    val recoveryContext: P2pRouteRecoveryContext? = null
) {
    fun cacheKey(): P2pRouteDetailCacheKey {
        return P2pRouteDetailCacheKey(rawInfo = rawInfo, lang = lang)
    }
}

data class P2pRouteRecoveryContext(
    val originLatitude: Double,
    val originLongitude: Double,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val searchMode: String
) {
    fun walkingContextKey(): String {
        return listOf(
            originLatitude.toP2pCoordinateKey(),
            originLongitude.toP2pCoordinateKey(),
            destinationLatitude.toP2pCoordinateKey(),
            destinationLongitude.toP2pCoordinateKey()
        ).joinToString(":")
    }

    private fun Double.toP2pCoordinateKey(): String = String.format(Locale.US, "%.6f", this)
}

data class P2pRouteDetailCacheKey(
    val rawInfo: String,
    val lang: String
)

data class P2pRoutePlan(
    val rawInfo: String = "",
    val lang: String = "0",
    val legs: List<P2pRouteLeg>
) {
    val previewBoardingLeg: P2pRouteLeg?
        get() = legs.firstOrNull()

    val previewAlightingLeg: P2pRouteLeg?
        get() = legs.lastOrNull()

    fun fingerprint(): String {
        val routeChain = legs.joinToString(">") { it.route }
        val segmentIdentity = legs.joinToString(">") { leg ->
            "${leg.routeVariant}:${leg.boardingSeq}:${leg.alightingSeq}"
        }
        return "$routeChain|$segmentIdentity"
    }
}

data class P2pRouteLeg(
    val company: String,
    val routeVariant: String,
    val route: String,
    val boardingSeq: Int,
    val alightingSeq: Int,
    val bound: String,
    val directionPath: String?
) {
    fun toFirstLegEtaQuery(rawInfo: String, lang: String): FirstLegEtaQuery? {
        val resolvedDirectionPath = directionPath ?: return null
        return FirstLegEtaQuery(
            company = company,
            routeVariant = routeVariant,
            route = route,
            boardingSeq = boardingSeq,
            alightingSeq = alightingSeq,
            bound = bound,
            directionPath = resolvedDirectionPath,
            rawInfo = rawInfo,
            lang = lang
        )
    }
}

data class RouteCardStopPreview(
    val boardingStopName: String,
    val alightingStopName: String
) {
    fun displayText(): String {
        return "$boardingStopName  \u2192  $alightingStopName"
    }
}

data class RouteCardStopPreviewCacheKey(
    val rawInfo: String,
    val lang: String
)

data class P2pStopMap(
    val rawInfo: String,
    val lang: String,
    val stops: List<P2pStopMapStop>
) {
    fun findStop(legIndex: Int, routeVariant: String, sequence: Int): P2pStopMapStop? {
        return stops.firstOrNull { stop ->
            stop.legIndex == legIndex &&
                stop.routeVariant == routeVariant &&
                stop.sequence == sequence
        } ?: stops.firstOrNull { stop ->
            stop.routeVariant == routeVariant && stop.sequence == sequence
        }
    }
}

data class P2pStopMapStop(
    val legIndex: Int,
    val company: String,
    val routeVariant: String,
    val publicRoute: String,
    val bound: String,
    val sequence: Int,
    val stopId: String,
    val rawName: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val markerType: String
)

data class P2pStopMapCacheKey(
    val rawInfo: String,
    val lang: String
)

data class RouteDetail(
    val routeName: String,
    val priceHkd: Double,
    val durationMinutes: Int,
    val walkingDistanceMeters: Int,
    val legs: List<RouteDetailLeg>,
    val originWalking: RouteDetailWalkingSegment? = null,
    val transfers: List<RouteDetailTransfer> = emptyList(),
    val destinationWalking: RouteDetailWalkingSegment? = null,
    val plannedDepartureTime: String? = null,
    val plannedArrivalTime: String? = null,
    val originName: String? = null,
    val destinationName: String? = null,
    val completeness: RouteDetailCompleteness = RouteDetailCompleteness.PARTIAL
) {
    val originWalkingDistanceMeters: Int?
        get() = originWalking?.distanceMeters

    val totalViaStopCount: Int
        get() = legs.sumOf { it.viaStops.size + 1 }

    val hasCompleteWalkingDistance: Boolean
        get() = originWalking?.distanceMeters != null &&
            destinationWalking?.distanceMeters != null &&
            transfers.size == (legs.size - 1).coerceAtLeast(0) &&
            transfers.all { transfer ->
                transfer.type == RouteDetailTransferType.SAME_STOP ||
                    transfer.walking?.distanceMeters != null
            }

    val completeWalkingDistanceMeters: Int?
        get() = if (hasCompleteWalkingDistance) {
            requireNotNull(originWalking?.distanceMeters) +
                transfers.sumOf { it.walking?.distanceMeters ?: 0 } +
                requireNotNull(destinationWalking?.distanceMeters)
        } else {
            null
        }

    val displayWalkingDistanceMeters: Int
        get() = completeWalkingDistanceMeters ?: walkingDistanceMeters
}

data class RouteDetailWalkingSegment(
    val kind: RouteDetailWalkingKind,
    val distanceMeters: Int?
)

enum class RouteDetailWalkingKind {
    ORIGIN,
    TRANSFER,
    DESTINATION
}

data class RouteDetailTransfer(
    val type: RouteDetailTransferType,
    val walking: RouteDetailWalkingSegment? = null
)

enum class RouteDetailTransferType {
    WALK_TO_TRANSFER_STOP,
    SAME_STOP
}

data class ParsedRouteDetail(
    val legs: List<RouteDetailLeg>,
    val originWalking: RouteDetailWalkingSegment? = null,
    val transfers: List<RouteDetailTransfer> = emptyList(),
    val destinationWalking: RouteDetailWalkingSegment? = null,
    val plannedDepartureTime: String? = null,
    val plannedArrivalTime: String? = null,
    val originName: String? = null,
    val destinationName: String? = null,
    val completeness: RouteDetailCompleteness = RouteDetailCompleteness.PARTIAL
)

enum class RouteDetailCompleteness {
    COMPLETE,
    PARTIAL,
    SESSION_MISSING
}

data class RouteDetailLeg(
    val route: String,
    val routeVariant: String,
    val directionText: String?,
    val boardingStop: RouteDetailStop,
    val viaStops: List<RouteDetailStop>,
    val alightingStop: RouteDetailStop,
    val fareHkd: Double? = null,
    val plannedBoardingTime: String? = null,
    val plannedAlightingTime: String? = null
)

data class RouteDetailStop(
    val rawName: String,
    val displayName: String,
    val stopId: String,
    val sequence: Int,
    val latitude: Double,
    val longitude: Double,
    val routeVariant: String,
    val role: RouteDetailStopRole
)

enum class RouteDetailStopRole {
    BOARDING,
    VIA,
    ALIGHTING
}

class RouteDetailExpansionState(legCount: Int) {
    private val expandedLegIndexes = BooleanArray(legCount.coerceAtLeast(0))

    fun isExpanded(legIndex: Int): Boolean {
        return expandedLegIndexes.getOrNull(legIndex) ?: false
    }

    fun toggle(legIndex: Int) {
        if (legIndex !in expandedLegIndexes.indices) return
        expandedLegIndexes[legIndex] = !expandedLegIndexes[legIndex]
    }
}

object RouteDetailDisplayFormatter {
    fun stationDisplayName(rawName: String): String {
        return rawName.substringBefore(",").trim().ifBlank { rawName.trim() }
    }

    fun directionLabel(directionText: String?): String? {
        return directionText?.trim()?.takeIf { it.isNotBlank() }
    }
}

fun buildBusRouteResultId(
    routeSegments: List<String>,
    priceHkd: Double,
    durationMinutes: Int,
    walkingDistanceMeters: Int,
    rawInfo: String? = null
): String {
    return listOf(
        routeSegments.joinToString("|"),
        priceHkd.toString(),
        durationMinutes.toString(),
        walkingDistanceMeters.toString(),
        rawInfo.orEmpty()
    ).joinToString("::")
}
