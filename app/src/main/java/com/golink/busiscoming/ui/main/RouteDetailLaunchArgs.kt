package com.golink.busiscoming.ui.main

import android.os.Bundle
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.EtaArrival
import com.golink.busiscoming.data.model.EtaUnavailableReason
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.P2pRouteRecoveryContext
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.WaitTimeState

data class RouteDetailQueryEndpoint(
    val name: String,
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        fun fromPlace(place: Place?): RouteDetailQueryEndpoint? {
            if (place == null || !place.latitude.isFinite() || !place.longitude.isFinite()) return null
            if (place.latitude !in -90.0..90.0 || place.longitude !in -180.0..180.0) return null
            return RouteDetailQueryEndpoint(place.name, place.latitude, place.longitude)
        }
    }
}

data class RouteDetailLaunchArgs(
    val routeName: String,
    val routeSegments: List<String>,
    val priceHkd: Double,
    val durationMinutes: Int,
    val walkingDistanceMeters: Int,
    val routeDetailQuery: P2pRouteDetailQuery?,
    val firstLegEtaQuery: FirstLegEtaQuery?,
    val waitTimeState: WaitTimeState,
    val queryOrigin: RouteDetailQueryEndpoint? = null,
    val queryDestination: RouteDetailQueryEndpoint? = null
) {
    val estimatedViaStopCount: Int
        get() = routeDetailQuery?.plan?.legs.orEmpty().sumOf { leg ->
            (leg.alightingSeq - leg.boardingSeq - 1).coerceAtLeast(0)
        }

    fun toPrimitiveValues(): Map<String, String> = buildMap {
        put("routeName", routeName)
        put("routeSegmentCount", routeSegments.size.toString())
        routeSegments.forEachIndexed { index, value -> put("routeSegment.$index", value) }
        put("priceHkd", priceHkd.toString())
        put("durationMinutes", durationMinutes.toString())
        put("walkingDistanceMeters", walkingDistanceMeters.toString())
        queryOrigin?.let { putEndpoint("query.origin", it) }
        queryDestination?.let { putEndpoint("query.destination", it) }
        routeDetailQuery?.let { query ->
            put("detail.present", "true")
            put("detail.rawInfo", query.rawInfo)
            put("detail.generalInfo", query.generalInfo)
            put("detail.listId", query.listId)
            put("detail.lang", query.lang)
            put("detail.plan.rawInfo", query.plan.rawInfo)
            put("detail.plan.lang", query.plan.lang)
            put("detail.plan.legCount", query.plan.legs.size.toString())
            query.plan.legs.forEachIndexed { index, leg -> putLeg("detail.plan.leg.$index", leg) }
            query.sessionRef?.let { put("detail.sessionRef", it) }
            query.recoveryContext?.let { context ->
                put("detail.recovery.present", "true")
                put("detail.recovery.originLatitude", context.originLatitude.toString())
                put("detail.recovery.originLongitude", context.originLongitude.toString())
                put("detail.recovery.destinationLatitude", context.destinationLatitude.toString())
                put("detail.recovery.destinationLongitude", context.destinationLongitude.toString())
                put("detail.recovery.searchMode", context.searchMode)
            }
        }
        firstLegEtaQuery?.let { query ->
            put("etaQuery.present", "true")
            put("etaQuery.company", query.company)
            put("etaQuery.variant", query.routeVariant)
            put("etaQuery.route", query.route)
            put("etaQuery.board", query.boardingSeq.toString())
            put("etaQuery.alight", query.alightingSeq.toString())
            put("etaQuery.bound", query.bound)
            put("etaQuery.path", query.directionPath)
            put("etaQuery.rawInfo", query.rawInfo)
            put("etaQuery.lang", query.lang)
        }
        when (val state = waitTimeState) {
            is WaitTimeState.Available -> {
                put("wait.type", "available")
                put("wait.count", state.arrivals.size.toString())
                state.arrivals.forEachIndexed { index, arrival ->
                    put("wait.$index.sequence", arrival.sequence.toString())
                    put("wait.$index.minutes", arrival.minutes.toString())
                }
            }
            WaitTimeState.Loading -> put("wait.type", "loading")
            WaitTimeState.NoArrivals -> put("wait.type", "none")
            is WaitTimeState.Unavailable -> {
                put("wait.type", "unavailable")
                put("wait.reason", state.reason.name)
            }
        }
    }

    fun toBundle(): Bundle = Bundle().apply {
        toPrimitiveValues().forEach { (key, value) -> putString(key, value) }
    }

    private fun MutableMap<String, String>.putLeg(prefix: String, leg: P2pRouteLeg) {
        put("$prefix.company", leg.company)
        put("$prefix.variant", leg.routeVariant)
        put("$prefix.route", leg.route)
        put("$prefix.board", leg.boardingSeq.toString())
        put("$prefix.alight", leg.alightingSeq.toString())
        put("$prefix.bound", leg.bound)
        leg.directionPath?.let { put("$prefix.path", it) }
    }

    private fun MutableMap<String, String>.putEndpoint(prefix: String, endpoint: RouteDetailQueryEndpoint) {
        put("$prefix.present", "true")
        put("$prefix.name", endpoint.name)
        put("$prefix.latitude", endpoint.latitude.toString())
        put("$prefix.longitude", endpoint.longitude.toString())
    }

    companion object {
        fun fromRoute(
            route: BusRouteOption,
            queryOrigin: Place? = null,
            queryDestination: Place? = null
        ): RouteDetailLaunchArgs = RouteDetailLaunchArgs(
            route.routeName,
            route.routeSegments,
            route.priceHkd,
            route.durationMinutes,
            route.walkingDistanceMeters,
            route.routeDetailQuery,
            route.firstLegEtaQuery,
            route.waitTimeState,
            RouteDetailQueryEndpoint.fromPlace(queryOrigin),
            RouteDetailQueryEndpoint.fromPlace(queryDestination)
        )

        fun fromBundle(bundle: Bundle): RouteDetailLaunchArgs? {
            val keys = bundle.keySet().associateWith { bundle.getString(it).orEmpty() }
            return fromPrimitiveValues(keys)
        }

        fun fromPrimitiveValues(values: Map<String, String>): RouteDetailLaunchArgs? {
            val routeName = values["routeName"] ?: return null
            val segments = (0 until (values["routeSegmentCount"]?.toIntOrNull() ?: 0))
                .mapNotNull { values["routeSegment.$it"] }
            val detailQuery = if (values["detail.present"] == "true") {
                val raw = values["detail.rawInfo"] ?: return null
                val legCount = values["detail.plan.legCount"]?.toIntOrNull() ?: 0
                val legs = (0 until legCount).mapNotNull { index -> values.readLeg("detail.plan.leg.$index") }
                P2pRouteDetailQuery(
                    raw,
                    values["detail.generalInfo"].orEmpty(),
                    values["detail.listId"].orEmpty(),
                    values["detail.lang"].orEmpty(),
                    P2pRoutePlan(values["detail.plan.rawInfo"].orEmpty(), values["detail.plan.lang"].orEmpty(), legs),
                    sessionRef = values["detail.sessionRef"],
                    recoveryContext = values.readRecoveryContext()
                )
            } else null
            val etaQuery = if (values["etaQuery.present"] == "true") {
                FirstLegEtaQuery(
                    values["etaQuery.company"].orEmpty(),
                    values["etaQuery.variant"].orEmpty(),
                    values["etaQuery.route"].orEmpty(),
                    values["etaQuery.board"]?.toIntOrNull() ?: 0,
                    values["etaQuery.alight"]?.toIntOrNull() ?: 0,
                    values["etaQuery.bound"].orEmpty(),
                    values["etaQuery.path"].orEmpty(),
                    values["etaQuery.rawInfo"].orEmpty(),
                    values["etaQuery.lang"].orEmpty()
                )
            } else null
            return RouteDetailLaunchArgs(
                routeName,
                segments,
                values["priceHkd"]?.toDoubleOrNull() ?: return null,
                values["durationMinutes"]?.toIntOrNull() ?: return null,
                values["walkingDistanceMeters"]?.toIntOrNull() ?: return null,
                detailQuery,
                etaQuery,
                values.readWaitState(),
                values.readEndpoint("query.origin"),
                values.readEndpoint("query.destination")
            )
        }

        private fun Map<String, String>.readEndpoint(prefix: String): RouteDetailQueryEndpoint? {
            if (get("$prefix.present") != "true") return null
            val latitude = get("$prefix.latitude")?.toDoubleOrNull() ?: return null
            val longitude = get("$prefix.longitude")?.toDoubleOrNull() ?: return null
            if (!latitude.isFinite() || !longitude.isFinite()) return null
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            return RouteDetailQueryEndpoint(get("$prefix.name").orEmpty(), latitude, longitude)
        }

        private fun Map<String, String>.readRecoveryContext(): P2pRouteRecoveryContext? {
            if (get("detail.recovery.present") != "true") return null
            val originLatitude = get("detail.recovery.originLatitude")?.toDoubleOrNull() ?: return null
            val originLongitude = get("detail.recovery.originLongitude")?.toDoubleOrNull() ?: return null
            val destinationLatitude = get("detail.recovery.destinationLatitude")?.toDoubleOrNull() ?: return null
            val destinationLongitude = get("detail.recovery.destinationLongitude")?.toDoubleOrNull() ?: return null
            val mode = get("detail.recovery.searchMode")?.takeIf { it in setOf("T", "F", "W") } ?: return null
            if (originLatitude !in -90.0..90.0 || destinationLatitude !in -90.0..90.0 ||
                originLongitude !in -180.0..180.0 || destinationLongitude !in -180.0..180.0
            ) {
                return null
            }
            return P2pRouteRecoveryContext(
                originLatitude,
                originLongitude,
                destinationLatitude,
                destinationLongitude,
                mode
            )
        }

        private fun Map<String, String>.readLeg(prefix: String): P2pRouteLeg? {
            val variant = get("$prefix.variant") ?: return null
            return P2pRouteLeg(
                get("$prefix.company").orEmpty(), variant, get("$prefix.route").orEmpty(),
                get("$prefix.board")?.toIntOrNull() ?: return null,
                get("$prefix.alight")?.toIntOrNull() ?: return null,
                get("$prefix.bound").orEmpty(), get("$prefix.path")
            )
        }

        private fun Map<String, String>.readWaitState(): WaitTimeState = when (get("wait.type")) {
            "available" -> WaitTimeState.Available(
                (0 until (get("wait.count")?.toIntOrNull() ?: 0)).map { index ->
                    EtaArrival(
                        get("wait.$index.sequence")?.toIntOrNull() ?: index + 1,
                        get("wait.$index.minutes")?.toIntOrNull() ?: 0
                    )
                }
            )
            "loading" -> WaitTimeState.Loading
            "none" -> WaitTimeState.NoArrivals
            "unavailable" -> WaitTimeState.Unavailable(
                runCatching { EtaUnavailableReason.valueOf(get("wait.reason").orEmpty()) }
                    .getOrDefault(EtaUnavailableReason.UNEXPECTED_ERROR)
            )
            else -> WaitTimeState.Unavailable(EtaUnavailableReason.UNEXPECTED_ERROR)
        }
    }
}
