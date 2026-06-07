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
    data class Available(val minutes: Int) : WaitTimeState()
    object Unavailable : WaitTimeState()
}

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
    val directionPath: String
) {
    fun requestKey(): FirstLegEtaRequestKey {
        return FirstLegEtaRequestKey(
            company = company,
            route = route,
            boardingSeq = boardingSeq,
            bound = bound,
            directionPath = directionPath
        )
    }
}

data class FirstLegEtaRequestKey(
    val company: String,
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
    fun toFirstLegEtaQuery(): FirstLegEtaQuery? {
        val resolvedDirectionPath = directionPath ?: return null
        return FirstLegEtaQuery(
            company = company,
            routeVariant = routeVariant,
            route = route,
            boardingSeq = boardingSeq,
            alightingSeq = alightingSeq,
            bound = bound,
            directionPath = resolvedDirectionPath
        )
    }
}

data class RouteCardStopPreview(
    val boardingStopName: String,
    val alightingStopName: String
) {
    fun displayText(): String {
        return "上車 $boardingStopName  \u2192  下車 $alightingStopName"
    }
}

data class RouteCardStopPreviewCacheKey(
    val rawInfo: String,
    val lang: String
)

data class RouteDetail(
    val routeName: String,
    val priceHkd: Double,
    val durationMinutes: Int,
    val walkingDistanceMeters: Int,
    val legs: List<RouteDetailLeg>
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
