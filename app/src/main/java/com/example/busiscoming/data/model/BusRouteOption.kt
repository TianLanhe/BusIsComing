package com.example.busiscoming.data.model

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
    object Unavailable : WaitTimeState()
}

data class EtaArrival(
    val sequence: Int,
    val minutes: Int,
    val etaMillis: Long? = null,
    val arrivalTimeText: String = "",
    val destination: String? = null,
    val remark: String? = null,
    val dataTimestampMillis: Long? = null
)

fun WaitTimeState.toDisplayText(): String {
    return when (this) {
        is WaitTimeState.Available -> minutes.toString()
        WaitTimeState.Loading -> "..."
        WaitTimeState.Unavailable -> "-"
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
    val plan: P2pRoutePlan
) {
    fun cacheKey(): P2pRouteDetailCacheKey {
        return P2pRouteDetailCacheKey(rawInfo = rawInfo, lang = lang)
    }
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
    val originWalkingDistanceMeters: Int? = null
)

data class RouteDetailLeg(
    val route: String,
    val routeVariant: String,
    val directionText: String?,
    val boardingStop: RouteDetailStop,
    val viaStops: List<RouteDetailStop>,
    val alightingStop: RouteDetailStop
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
        val normalized = directionText
            ?.trim()
            ?.removePrefix("往")
            ?.removeSuffix("方向")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return "往 ${normalized}方向"
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
