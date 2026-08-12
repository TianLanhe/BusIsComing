package com.golink.busiscoming.data.location

import com.golink.busiscoming.data.model.JourneyAxisEdge
import com.golink.busiscoming.data.model.JourneyAxisNode
import com.golink.busiscoming.data.model.JourneyCoordinate
import com.golink.busiscoming.data.model.JourneyPosition
import com.golink.busiscoming.data.model.RouteJourneyAxis
import kotlin.math.abs
import kotlin.math.max

data class JourneyLocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val elapsedRealtimeMillis: Long
) {
    fun coordinate(): JourneyCoordinate = JourneyCoordinate(latitude, longitude)
}

class RouteJourneyPositionMatcher {
    fun match(
        axis: RouteJourneyAxis,
        fix: JourneyLocationFix,
        nowElapsedRealtimeMillis: Long
    ): JourneyPosition {
        val accuracy = fix.accuracyMeters?.toDouble() ?: return JourneyPosition.Unreliable
        if (!fix.coordinate().isValidWgs84 || accuracy < 0.0 || accuracy > MAX_ACCURACY_METERS) {
            return JourneyPosition.Unreliable
        }
        val age = nowElapsedRealtimeMillis - fix.elapsedRealtimeMillis
        if (age < 0L || age > MAX_FIX_AGE_MILLIS) return JourneyPosition.Unreliable

        val coordinate = fix.coordinate()
        val nodeCandidates = axis.nodes.filter(JourneyAxisNode::matchable).map { node ->
            Candidate.Node(node, JourneyGeometry.distanceMeters(coordinate, node.coordinate))
        }
        val edgeCandidates = axis.edges.filter(JourneyAxisEdge::matchable).mapNotNull { edge ->
            edgeCandidate(edge, coordinate)
        }
        val allCandidates = (nodeCandidates + edgeCandidates).sortedBy(Candidate::distanceMeters)
        val nearest = allCandidates.firstOrNull() ?: return JourneyPosition.Unreliable
        val maximumDistance = max(MIN_AXIS_DISTANCE_METERS, accuracy)
        if (nearest.distanceMeters > maximumDistance) return JourneyPosition.Unreliable
        val ambiguityMargin = max(MIN_AMBIGUITY_MARGIN_METERS, accuracy / 2.0)
        val nonLocalSecond = allCandidates.drop(1).firstOrNull { !areLocal(nearest, it, axis) }
        if (nonLocalSecond != null &&
            nonLocalSecond.distanceMeters - nearest.distanceMeters < ambiguityMargin
        ) return JourneyPosition.Unreliable

        val nodeRadius = max(NODE_SNAP_METERS, accuracy)
        val nearbyNodes = nodeCandidates.filter { it.distanceMeters <= nodeRadius }
            .sortedBy(Candidate.Node::distanceMeters)
        if (nearbyNodes.size == 1) return nearbyNodes.single().toPosition()
        if (nearbyNodes.size >= 2) {
            val first = nearbyNodes[0].node
            val second = nearbyNodes[1].node
            val connecting = axis.edges.firstOrNull { edge ->
                edge.matchable && setOf(edge.fromNodeId, edge.toNodeId) == setOf(first.id, second.id)
            }
            if (connecting != null) {
                return edgeCandidates.firstOrNull { it.edge.id == connecting.id }?.toPosition(axis)
                    ?: JourneyPosition.Unreliable
            }
            return JourneyPosition.Unreliable
        }

        return edgeCandidates.minByOrNull(Candidate.Edge::distanceMeters)
            ?.takeIf { it.distanceMeters <= maximumDistance }
            ?.toPosition(axis)
            ?: JourneyPosition.Unreliable
    }

    private fun edgeCandidate(edge: JourneyAxisEdge, coordinate: JourneyCoordinate): Candidate.Edge? {
        return when (edge) {
            is JourneyAxisEdge.Bus -> {
                val projection = JourneyGeometry.project(coordinate, edge.geometry) ?: return null
                Candidate.Edge(edge, projection.distanceMeters, projection)
            }
            is JourneyAxisEdge.Walking -> {
                val projections = edge.paths.mapIndexedNotNull { index, path ->
                    JourneyGeometry.project(coordinate, path.points)?.let { index to it }
                }.sortedBy { it.second.distanceMeters }
                val best = projections.firstOrNull() ?: return null
                val ambiguousGap = projections.drop(1).any { other ->
                    other.second.distanceMeters - best.second.distanceMeters < MIN_AMBIGUITY_MARGIN_METERS
                }
                if (ambiguousGap) null else Candidate.Edge(
                    edge,
                    best.second.distanceMeters,
                    best.second,
                    walkingPathIndex = best.first
                )
            }
        }
    }

    private fun areLocal(first: Candidate, second: Candidate, axis: RouteJourneyAxis): Boolean {
        val firstNodes = first.nodeIds()
        val secondNodes = second.nodeIds()
        if (firstNodes.intersect(secondNodes).isNotEmpty()) return true
        if (first is Candidate.Node && second is Candidate.Node) {
            return axis.edges.any { edge ->
                edge.matchable && setOf(edge.fromNodeId, edge.toNodeId) == setOf(first.node.id, second.node.id)
            }
        }
        return false
    }

    private sealed interface Candidate {
        val distanceMeters: Double
        fun nodeIds(): Set<String>

        data class Node(
            val node: JourneyAxisNode,
            override val distanceMeters: Double
        ) : Candidate {
            override fun nodeIds(): Set<String> = setOf(node.id)

            fun toPosition(): JourneyPosition.AtNode {
                val membership = node.busMemberships.firstOrNull()
                return JourneyPosition.AtNode(
                    nodeId = node.id,
                    nodeLabel = node.label,
                    nodeKind = node.kind,
                    summarySegmentId = node.summarySegmentId,
                    timelineTargetIds = node.timelineTargetIds,
                    legIndex = membership?.legIndex,
                    stopIndex = membership?.stopIndex,
                    stopEdgeCount = membership?.stopEdgeCount,
                    distanceToAxisMeters = distanceMeters
                )
            }
        }

        data class Edge(
            val edge: JourneyAxisEdge,
            override val distanceMeters: Double,
            val projection: JourneyProjection,
            val walkingPathIndex: Int? = null
        ) : Candidate {
            override fun nodeIds(): Set<String> = setOf(edge.fromNodeId, edge.toNodeId)

            fun toPosition(axis: RouteJourneyAxis): JourneyPosition {
                val from = axis.nodesById.getValue(edge.fromNodeId)
                val to = axis.nodesById.getValue(edge.toNodeId)
                return when (edge) {
                    is JourneyAxisEdge.Bus -> JourneyPosition.BetweenNodes(
                        edgeId = edge.id,
                        fromNodeId = from.id,
                        fromLabel = from.label,
                        toNodeId = to.id,
                        toLabel = to.label,
                        summarySegmentId = edge.summarySegmentId,
                        legIndex = edge.legIndex,
                        fromStopIndex = edge.fromStopIndex,
                        stopEdgeCount = edge.stopEdgeCount,
                        distanceToAxisMeters = distanceMeters
                    )
                    is JourneyAxisEdge.Walking -> {
                        val path = edge.paths[requireNotNull(walkingPathIndex)]
                        val progress = if (edge.totalPathLengthMeters <= 0.0) 0.0 else {
                            (path.cumulativeStartMeters + projection.distanceAlongMeters) /
                                edge.totalPathLengthMeters
                        }.coerceIn(0.0, 1.0)
                        JourneyPosition.WalkingProgress(
                            edgeId = edge.id,
                            fromNodeId = from.id,
                            fromLabel = from.label,
                            toNodeId = to.id,
                            toLabel = to.label,
                            summarySegmentId = edge.summarySegmentId,
                            progress = progress,
                            distanceToAxisMeters = distanceMeters
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val MAX_FIX_AGE_MILLIS = 20_000L
        private const val MAX_ACCURACY_METERS = 75.0
        private const val MIN_AXIS_DISTANCE_METERS = 30.0
        private const val MIN_AMBIGUITY_MARGIN_METERS = 20.0
        private const val NODE_SNAP_METERS = 15.0
    }
}

class RouteJourneyPositionStabilizer {
    private var current: JourneyPosition? = null
    private var pendingRemoteRegion: String? = null

    fun update(
        axis: RouteJourneyAxis,
        candidate: JourneyPosition,
        fix: JourneyLocationFix
    ): JourneyPosition {
        if (candidate === JourneyPosition.Unreliable) {
            current = null
            pendingRemoteRegion = null
            return JourneyPosition.Unreliable
        }
        val previous = current
        if (previous == null) {
            if (pendingRemoteRegion == candidate.regionKey) {
                current = candidate
                pendingRemoteRegion = null
                return candidate
            }
            if (pendingRemoteRegion != null) {
                pendingRemoteRegion = candidate.regionKey
                return JourneyPosition.Unreliable
            }
            current = candidate
            return candidate
        }

        if (previous is JourneyPosition.AtNode && candidate !is JourneyPosition.AtNode &&
            candidate.nodeIds().contains(previous.nodeId)
        ) {
            val node = axis.nodesById[previous.nodeId]
            if (node != null && JourneyGeometry.distanceMeters(fix.coordinate(), node.coordinate) <=
                NODE_EXIT_HYSTERESIS_METERS
            ) return previous
        }

        if (areAdjacent(previous, candidate)) {
            current = candidate
            pendingRemoteRegion = null
            return candidate
        }

        if (pendingRemoteRegion == candidate.regionKey) {
            current = candidate
            pendingRemoteRegion = null
            return candidate
        }
        current = null
        pendingRemoteRegion = candidate.regionKey
        return JourneyPosition.Unreliable
    }

    fun reset() {
        current = null
        pendingRemoteRegion = null
    }

    private fun areAdjacent(first: JourneyPosition, second: JourneyPosition): Boolean {
        if (first.regionKey == second.regionKey) return true
        return first.nodeIds().intersect(second.nodeIds()).isNotEmpty()
    }

    private fun JourneyPosition.nodeIds(): Set<String> = when (this) {
        is JourneyPosition.AtNode -> setOf(nodeId)
        is JourneyPosition.BetweenNodes -> setOf(fromNodeId, toNodeId)
        is JourneyPosition.WalkingProgress -> setOf(fromNodeId, toNodeId)
        JourneyPosition.Unreliable -> emptySet()
    }

    private companion object {
        const val NODE_EXIT_HYSTERESIS_METERS = 30.0
    }
}

private fun JourneyPosition.nodeIds(): Set<String> = when (this) {
    is JourneyPosition.AtNode -> setOf(nodeId)
    is JourneyPosition.BetweenNodes -> setOf(fromNodeId, toNodeId)
    is JourneyPosition.WalkingProgress -> setOf(fromNodeId, toNodeId)
    JourneyPosition.Unreliable -> emptySet()
}
