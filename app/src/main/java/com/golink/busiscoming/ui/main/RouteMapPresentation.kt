package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.data.model.RouteGeometrySegment
import com.golink.busiscoming.data.model.P2pRouteLeg

data class RouteMapCoordinate(
    val latitude: Double,
    val longitude: Double
)

enum class RouteMapMarkerRole {
    QUERY_ORIGIN,
    BOARDING,
    VIA,
    ALIGHTING,
    TRANSFER,
    QUERY_DESTINATION
}

data class RouteMapMarker(
    val stableId: String,
    val title: String,
    val position: RouteMapCoordinate,
    val role: RouteMapMarkerRole,
    val legIndexes: Set<Int> = emptySet(),
    val routeLabels: List<String> = emptyList(),
    val timelineStopIds: Set<String> = emptySet(),
    val showLabelByDefault: Boolean = true,
    val selected: Boolean = false
)

enum class RouteMapLineKind {
    BUS,
    WALKING
}

data class RouteMapLine(
    val stableId: String,
    val kind: RouteMapLineKind,
    val points: List<RouteMapCoordinate>,
    val legIndex: Int? = null,
    val colorSlot: Int? = null
)

data class RouteMapPresentation(
    val markers: List<RouteMapMarker>,
    val lines: List<RouteMapLine>,
    val boundsPoints: List<RouteMapCoordinate>
)

object RouteMapPresentationBuilder {
    fun build(
        detail: RouteDetail?,
        queryOrigin: RouteDetailQueryEndpoint?,
        queryDestination: RouteDetailQueryEndpoint?,
        geometries: Map<RouteGeometryKey, RouteGeometrySegment>,
        selectedMarkerId: String?,
        routePlan: List<P2pRouteLeg> = emptyList()
    ): RouteMapPresentation {
        val markers = mutableListOf<RouteMapMarker>()
        queryOrigin?.let { endpoint ->
            markers += endpointMarker("query-origin", endpoint, RouteMapMarkerRole.QUERY_ORIGIN, selectedMarkerId)
        }
        detail?.legs?.forEachIndexed { legIndex, leg ->
            val previousTransfer = detail.transfers.getOrNull(legIndex - 1)
            if (previousTransfer?.type != RouteDetailTransferType.SAME_STOP) {
                markers += stopMarker(legIndex, leg.route, leg.boardingStop, selectedMarkerId)
            }
            markers += leg.viaStops.map { stop -> stopMarker(legIndex, leg.route, stop, selectedMarkerId) }
            val nextTransfer = detail.transfers.getOrNull(legIndex)
            if (nextTransfer?.type == RouteDetailTransferType.SAME_STOP) {
                val nextLeg = detail.legs.getOrNull(legIndex + 1)
                if (nextLeg != null) {
                    markers += sameStopTransferMarker(
                        transferIndex = legIndex,
                        previousLegIndex = legIndex,
                        previousRoute = leg.route,
                        alightingStop = leg.alightingStop,
                        nextLegIndex = legIndex + 1,
                        nextRoute = nextLeg.route,
                        boardingStop = nextLeg.boardingStop,
                        selectedMarkerId = selectedMarkerId
                    )
                } else {
                    markers += stopMarker(legIndex, leg.route, leg.alightingStop, selectedMarkerId)
                }
            } else {
                markers += stopMarker(legIndex, leg.route, leg.alightingStop, selectedMarkerId)
            }
        }
        queryDestination?.let { endpoint ->
            markers += endpointMarker(
                "query-destination",
                endpoint,
                RouteMapMarkerRole.QUERY_DESTINATION,
                selectedMarkerId
            )
        }

        val lines = mutableListOf<RouteMapLine>()
        val geometryLegs = detail?.legs?.map { leg ->
            GeometryLeg(
                key = RouteGeometryKey(leg.routeVariant, leg.boardingStop.sequence, leg.alightingStop.sequence)
            )
        } ?: routePlan.map { leg ->
            GeometryLeg(
                key = RouteGeometryKey(leg.routeVariant, leg.boardingSeq, leg.alightingSeq)
            )
        }
        geometryLegs.forEachIndexed { legIndex, leg ->
            val key = leg.key
            geometries[key]?.let { geometry ->
                lines += RouteMapLine(
                    stableId = "bus:$legIndex:${key.routeVariant}:${key.boardingSeq}:${key.alightingSeq}",
                    kind = RouteMapLineKind.BUS,
                    points = geometry.points.map { RouteMapCoordinate(it.latitude, it.longitude) },
                    legIndex = legIndex,
                    colorSlot = legIndex
                )
            }
        }
        detail?.transfers?.forEachIndexed { transferIndex, transfer ->
            if (transfer.type == RouteDetailTransferType.WALK_TO_TRANSFER_STOP) {
                val previousLeg = detail.legs.getOrNull(transferIndex)
                val nextLeg = detail.legs.getOrNull(transferIndex + 1)
                if (previousLeg != null && nextLeg != null) {
                    lines += walkingLine(
                        stableId = "walk:transfer:$transferIndex",
                        from = previousLeg.alightingStop.coordinate(),
                        to = nextLeg.boardingStop.coordinate()
                    )
                }
            }
        }
        val firstBoarding = detail?.legs?.firstOrNull()?.boardingStop
        if (queryOrigin != null && firstBoarding != null) {
            lines += walkingLine(
                stableId = "walk:origin",
                from = RouteMapCoordinate(queryOrigin.latitude, queryOrigin.longitude),
                to = firstBoarding.coordinate()
            )
        }
        val lastAlighting = detail?.legs?.lastOrNull()?.alightingStop
        if (queryDestination != null && lastAlighting != null) {
            lines += walkingLine(
                stableId = "walk:destination",
                from = lastAlighting.coordinate(),
                to = RouteMapCoordinate(queryDestination.latitude, queryDestination.longitude)
            )
        }

        val boundsPoints = buildList {
            addAll(markers.map { it.position })
            addAll(lines.filter { it.kind == RouteMapLineKind.BUS }.flatMap { it.points })
        }
        return RouteMapPresentation(markers, lines, boundsPoints)
    }

    private data class GeometryLeg(
        val key: RouteGeometryKey
    )

    fun stopStableId(legIndex: Int, stop: RouteDetailStop): String {
        return RouteDetailTimelineStableIds.stop(legIndex, stop)
    }

    private fun endpointMarker(
        stableId: String,
        endpoint: RouteDetailQueryEndpoint,
        role: RouteMapMarkerRole,
        selectedMarkerId: String?
    ) = RouteMapMarker(
        stableId = stableId,
        title = endpoint.name,
        position = RouteMapCoordinate(endpoint.latitude, endpoint.longitude),
        role = role,
        selected = stableId == selectedMarkerId
    )

    private fun stopMarker(
        legIndex: Int,
        route: String,
        stop: RouteDetailStop,
        selectedMarkerId: String?
    ): RouteMapMarker {
        val stableId = stopStableId(legIndex, stop)
        return RouteMapMarker(
            stableId = stableId,
            title = stop.displayName,
            position = stop.coordinate(),
            role = when (stop.role) {
                RouteDetailStopRole.BOARDING -> RouteMapMarkerRole.BOARDING
                RouteDetailStopRole.VIA -> RouteMapMarkerRole.VIA
                RouteDetailStopRole.ALIGHTING -> RouteMapMarkerRole.ALIGHTING
            },
            legIndexes = setOf(legIndex),
            routeLabels = listOf(route),
            timelineStopIds = setOf(stableId),
            showLabelByDefault = stop.role != RouteDetailStopRole.VIA,
            selected = stableId == selectedMarkerId
        )
    }

    private fun sameStopTransferMarker(
        transferIndex: Int,
        previousLegIndex: Int,
        previousRoute: String,
        alightingStop: RouteDetailStop,
        nextLegIndex: Int,
        nextRoute: String,
        boardingStop: RouteDetailStop,
        selectedMarkerId: String?
    ): RouteMapMarker {
        val stableId = "transfer:$transferIndex"
        val timelineIds = setOf(
            stopStableId(previousLegIndex, alightingStop),
            stopStableId(nextLegIndex, boardingStop)
        )
        return RouteMapMarker(
            stableId = stableId,
            title = alightingStop.displayName,
            position = alightingStop.coordinate(),
            role = RouteMapMarkerRole.TRANSFER,
            legIndexes = setOf(previousLegIndex, nextLegIndex),
            routeLabels = listOf(previousRoute, nextRoute),
            timelineStopIds = timelineIds,
            selected = selectedMarkerId == stableId || selectedMarkerId in timelineIds
        )
    }

    private fun walkingLine(
        stableId: String,
        from: RouteMapCoordinate,
        to: RouteMapCoordinate
    ) = RouteMapLine(
        stableId = stableId,
        kind = RouteMapLineKind.WALKING,
        points = listOf(from, to)
    )

    private fun RouteDetailStop.coordinate(): RouteMapCoordinate {
        return RouteMapCoordinate(latitude, longitude)
    }
}
