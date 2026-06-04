package com.example.busiscomming.data.model

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
    val resultId: String = buildResultId(routeSegments, priceHkd, durationMinutes, walkingDistanceMeters)
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

private fun buildResultId(
    routeSegments: List<String>,
    priceHkd: Double,
    durationMinutes: Int,
    walkingDistanceMeters: Int
): String {
    return listOf(
        routeSegments.joinToString("|"),
        priceHkd.toString(),
        durationMinutes.toString(),
        walkingDistanceMeters.toString()
    ).joinToString("::")
}
