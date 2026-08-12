package com.golink.busiscoming.data.location

import com.golink.busiscoming.data.model.JourneyAxisEdge
import com.golink.busiscoming.data.model.JourneyAxisIdentity
import com.golink.busiscoming.data.model.JourneyAxisNode
import com.golink.busiscoming.data.model.JourneyAxisNodeKind
import com.golink.busiscoming.data.model.JourneyBusMembership
import com.golink.busiscoming.data.model.JourneyCoordinate
import com.golink.busiscoming.data.model.JourneyPath
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteDetailWalkingState
import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.data.model.RouteGeometrySegment
import com.golink.busiscoming.data.model.RouteJourneyAxis
import kotlin.math.abs

data class JourneyEndpoint(
    val name: String,
    val latitude: Double,
    val longitude: Double
) {
    fun coordinate(): JourneyCoordinate = JourneyCoordinate(latitude, longitude)
}

data class JourneyAxisBuildInput(
    val pageGeneration: Long,
    val structureIdentity: Int,
    val origin: JourneyEndpoint?,
    val destination: JourneyEndpoint?,
    val detail: RouteDetail,
    val geometries: Map<RouteGeometryKey, RouteGeometrySegment>,
    val walkingSegments: Map<String, RouteDetailWalkingState>
)

class RouteJourneyAxisBuilder {
    private var lastCacheKey: JourneyAxisCacheKey? = null
    private var lastAxis: RouteJourneyAxis? = null

    fun build(input: JourneyAxisBuildInput): RouteJourneyAxis {
        val cacheKey = cacheKey(input)
        if (cacheKey == lastCacheKey) return requireNotNull(lastAxis)

        val nodeMap = linkedMapOf<String, JourneyAxisNode>()
        input.origin?.takeIf { it.coordinate().isValidWgs84 }?.let { endpoint ->
            nodeMap[ORIGIN_ID] = JourneyAxisNode(
                id = ORIGIN_ID,
                label = endpoint.name,
                coordinate = endpoint.coordinate(),
                kind = JourneyAxisNodeKind.ORIGIN,
                summarySegmentId = "walk-origin",
                timelineTargetIds = setOf("origin")
            )
        }

        input.detail.legs.forEachIndexed { legIndex, leg ->
            val stops = legStops(leg)
            stops.forEachIndexed { stopIndex, stop ->
                val id = nodeId(input.detail, legIndex, stop)
                val existing = nodeMap[id]
                val membership = JourneyBusMembership(legIndex, stopIndex, (stops.size - 1).coerceAtLeast(0))
                val targetId = timelineStopId(legIndex, stop)
                nodeMap[id] = if (existing == null) {
                    JourneyAxisNode(
                        id = id,
                        label = stop.displayName,
                        coordinate = JourneyCoordinate(stop.latitude, stop.longitude),
                        kind = nodeKind(input.detail, legIndex, stop),
                        summarySegmentId = summarySegmentId(input.detail, legIndex, stop),
                        timelineTargetIds = setOf(targetId),
                        busMemberships = listOf(membership)
                    )
                } else {
                    existing.copy(
                        timelineTargetIds = existing.timelineTargetIds + targetId,
                        busMemberships = existing.busMemberships + membership
                    )
                }
            }
        }

        input.destination?.takeIf { it.coordinate().isValidWgs84 }?.let { endpoint ->
            nodeMap[DESTINATION_ID] = JourneyAxisNode(
                id = DESTINATION_ID,
                label = endpoint.name,
                coordinate = endpoint.coordinate(),
                kind = JourneyAxisNodeKind.DESTINATION,
                summarySegmentId = "walk-destination",
                timelineTargetIds = setOf("destination")
            )
        }

        val edges = mutableListOf<JourneyAxisEdge>()
        val firstBoardingId = input.detail.legs.firstOrNull()?.boardingStop?.let {
            nodeId(input.detail, 0, it)
        }
        if (ORIGIN_ID in nodeMap && firstBoardingId != null && firstBoardingId in nodeMap) {
            edges += walkingEdge(
                id = "walk:origin",
                fromNodeId = ORIGIN_ID,
                toNodeId = firstBoardingId,
                summarySegmentId = "walk-origin",
                state = input.walkingSegments["origin"]
            )
        }

        input.detail.legs.forEachIndexed { legIndex, leg ->
            val stops = legStops(leg)
            val geometryKey = RouteGeometryKey(
                leg.routeVariant,
                leg.boardingStop.sequence,
                leg.alightingStop.sequence
            )
            val partitions = partitionGeometry(stops, input.geometries[geometryKey])
            stops.zipWithNext().forEachIndexed { stopIndex, (from, to) ->
                val geometry = partitions?.getOrNull(stopIndex).orEmpty()
                edges += JourneyAxisEdge.Bus(
                    id = "bus:$legIndex:$stopIndex",
                    fromNodeId = nodeId(input.detail, legIndex, from),
                    toNodeId = nodeId(input.detail, legIndex, to),
                    summarySegmentId = "leg-$legIndex-card",
                    matchable = geometry.size >= 2,
                    legIndex = legIndex,
                    fromStopIndex = stopIndex,
                    stopEdgeCount = stops.size - 1,
                    geometry = geometry
                )
            }

            val nextLeg = input.detail.legs.getOrNull(legIndex + 1)
            val transfer = input.detail.transfers.getOrNull(legIndex)
            if (nextLeg != null && transfer?.type == RouteDetailTransferType.WALK_TO_TRANSFER_STOP) {
                edges += walkingEdge(
                    id = "walk:transfer:$legIndex",
                    fromNodeId = nodeId(input.detail, legIndex, leg.alightingStop),
                    toNodeId = nodeId(input.detail, legIndex + 1, nextLeg.boardingStop),
                    summarySegmentId = "walk-transfer-$legIndex",
                    state = input.walkingSegments["transfer:$legIndex"]
                )
            }
        }

        val lastLegIndex = input.detail.legs.lastIndex
        val lastAlightingId = input.detail.legs.lastOrNull()?.alightingStop?.let {
            nodeId(input.detail, lastLegIndex, it)
        }
        if (lastAlightingId != null && lastAlightingId in nodeMap && DESTINATION_ID in nodeMap) {
            edges += walkingEdge(
                id = "walk:destination",
                fromNodeId = lastAlightingId,
                toNodeId = DESTINATION_ID,
                summarySegmentId = "walk-destination",
                state = input.walkingSegments["destination"]
            )
        }

        val matchableNodeIds = edges.filter(JourneyAxisEdge::matchable)
            .flatMap { listOf(it.fromNodeId, it.toNodeId) }
            .toSet()
        val axis = RouteJourneyAxis(
            identity = JourneyAxisIdentity(
                input.pageGeneration,
                input.structureIdentity,
                cacheKey.hashCode()
            ),
            nodes = nodeMap.values.map { node -> node.copy(matchable = node.id in matchableNodeIds) },
            edges = edges
        )
        lastCacheKey = cacheKey
        lastAxis = axis
        return axis
    }

    private fun cacheKey(input: JourneyAxisBuildInput): JourneyAxisCacheKey {
        val geometryKeys = input.detail.legs.map { leg ->
            RouteGeometryKey(
                leg.routeVariant,
                leg.boardingStop.sequence,
                leg.alightingStop.sequence
            )
        }.toSet()
        val walkingSegmentIds = buildList {
            add("origin")
            input.detail.transfers.forEachIndexed { index, transfer ->
                if (transfer.type == RouteDetailTransferType.WALK_TO_TRANSFER_STOP) {
                    add("transfer:$index")
                }
            }
            add("destination")
        }
        return JourneyAxisCacheKey(
            pageGeneration = input.pageGeneration,
            structureIdentity = input.structureIdentity,
            origin = input.origin,
            destination = input.destination,
            stopsByLeg = input.detail.legs.map(::legStops),
            transferTypes = input.detail.transfers.map { it.type },
            geometries = input.geometries
                .filterKeys { it in geometryKeys }
                .mapValues { (_, segment) ->
                    segment.points.map { JourneyCoordinate(it.latitude, it.longitude) }
                },
            walkingPaths = walkingSegmentIds.associateWith { segmentId ->
                (input.walkingSegments[segmentId] as? RouteDetailWalkingState.CsdiSuccess)
                    ?.route
                    ?.paths
                    .orEmpty()
                    .map { path ->
                        path.points.map { JourneyCoordinate(it.latitude, it.longitude) }
                    }
            }
        )
    }

    private fun walkingEdge(
        id: String,
        fromNodeId: String,
        toNodeId: String,
        summarySegmentId: String,
        state: RouteDetailWalkingState?
    ): JourneyAxisEdge.Walking {
        var cumulative = 0.0
        val paths = (state as? RouteDetailWalkingState.CsdiSuccess)?.route?.paths.orEmpty()
            .mapNotNull { path ->
                val points = path.points.map { JourneyCoordinate(it.latitude, it.longitude) }
                if (points.size < 2 || points.any { !it.isValidWgs84 }) return@mapNotNull null
                val length = JourneyGeometry.pathLengthMeters(points)
                if (length <= 0.0) return@mapNotNull null
                JourneyPath(points, length, cumulative).also { cumulative += length }
            }
        return JourneyAxisEdge.Walking(
            id = id,
            fromNodeId = fromNodeId,
            toNodeId = toNodeId,
            summarySegmentId = summarySegmentId,
            matchable = paths.isNotEmpty(),
            paths = paths,
            totalPathLengthMeters = cumulative
        )
    }

    private fun partitionGeometry(
        stops: List<RouteDetailStop>,
        segment: RouteGeometrySegment?
    ): List<List<JourneyCoordinate>>? {
        val polyline = segment?.points?.map { JourneyCoordinate(it.latitude, it.longitude) }
            ?.takeIf { it.size >= 2 && it.all(JourneyCoordinate::isValidWgs84) }
            ?: return null
        val projections = stops.map { stop ->
            val coordinate = JourneyCoordinate(stop.latitude, stop.longitude)
            val candidates = JourneyGeometry.projections(coordinate, polyline)
                .sortedBy(JourneyProjection::distanceMeters)
            val best = candidates.firstOrNull() ?: return null
            val ambiguous = candidates.drop(1).any { candidate ->
                candidate.distanceMeters <= best.distanceMeters + PROJECTION_AMBIGUITY_METERS &&
                    abs(candidate.distanceAlongMeters - best.distanceAlongMeters) >=
                    PROJECTION_AMBIGUITY_ALONG_METERS
            }
            if (ambiguous || best.distanceMeters > MAX_STOP_PROJECTION_METERS) return null
            best
        }
        if (projections.zipWithNext().any { (first, second) ->
                second.distanceAlongMeters + MONOTONIC_TOLERANCE_METERS < first.distanceAlongMeters
            }
        ) return null
        return projections.zipWithNext().map { (start, end) ->
            JourneyGeometry.slice(polyline, start, end)
        }.takeIf { partitions -> partitions.all { it.size >= 2 } }
    }

    private fun legStops(leg: com.golink.busiscoming.data.model.RouteDetailLeg): List<RouteDetailStop> =
        listOf(leg.boardingStop) + leg.viaStops + leg.alightingStop

    private fun nodeId(detail: RouteDetail, legIndex: Int, stop: RouteDetailStop): String {
        val sameStopTransfer = when (stop.role) {
            RouteDetailStopRole.BOARDING -> detail.transfers.getOrNull(legIndex - 1)
                ?.type == RouteDetailTransferType.SAME_STOP
            RouteDetailStopRole.ALIGHTING -> detail.transfers.getOrNull(legIndex)
                ?.type == RouteDetailTransferType.SAME_STOP
            RouteDetailStopRole.VIA -> false
        }
        return if (sameStopTransfer) {
            "same-stop:${if (stop.role == RouteDetailStopRole.BOARDING) legIndex - 1 else legIndex}"
        } else {
            timelineStopId(legIndex, stop)
        }
    }

    private fun nodeKind(detail: RouteDetail, legIndex: Int, stop: RouteDetailStop): JourneyAxisNodeKind {
        if (nodeId(detail, legIndex, stop).startsWith("same-stop:")) {
            return JourneyAxisNodeKind.SAME_STOP_TRANSFER
        }
        return when (stop.role) {
            RouteDetailStopRole.BOARDING -> JourneyAxisNodeKind.BOARDING
            RouteDetailStopRole.VIA -> JourneyAxisNodeKind.VIA
            RouteDetailStopRole.ALIGHTING -> JourneyAxisNodeKind.ALIGHTING
        }
    }

    private fun summarySegmentId(detail: RouteDetail, legIndex: Int, stop: RouteDetailStop): String {
        return if (nodeId(detail, legIndex, stop).startsWith("same-stop:")) {
            "transfer-${if (stop.role == RouteDetailStopRole.BOARDING) legIndex - 1 else legIndex}"
        } else {
            "leg-$legIndex-card"
        }
    }

    private fun timelineStopId(legIndex: Int, stop: RouteDetailStop): String = when (stop.role) {
        RouteDetailStopRole.BOARDING -> "leg-$legIndex-board"
        RouteDetailStopRole.VIA -> "leg-$legIndex-via-${stop.sequence}"
        RouteDetailStopRole.ALIGHTING -> "leg-$legIndex-alight"
    }

    private companion object {
        const val ORIGIN_ID = "query-origin"
        const val DESTINATION_ID = "query-destination"
        const val MAX_STOP_PROJECTION_METERS = 100.0
        const val PROJECTION_AMBIGUITY_METERS = 12.0
        const val PROJECTION_AMBIGUITY_ALONG_METERS = 50.0
        const val MONOTONIC_TOLERANCE_METERS = 2.0
    }
}

private data class JourneyAxisCacheKey(
    val pageGeneration: Long,
    val structureIdentity: Int,
    val origin: JourneyEndpoint?,
    val destination: JourneyEndpoint?,
    val stopsByLeg: List<List<RouteDetailStop>>,
    val transferTypes: List<RouteDetailTransferType>,
    val geometries: Map<RouteGeometryKey, List<JourneyCoordinate>>,
    val walkingPaths: Map<String, List<List<JourneyCoordinate>>>
)
