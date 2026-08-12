package com.golink.busiscoming.data.model

data class JourneyCoordinate(
    val latitude: Double,
    val longitude: Double
) {
    val isValidWgs84: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

enum class JourneyAxisNodeKind {
    ORIGIN,
    BOARDING,
    VIA,
    ALIGHTING,
    SAME_STOP_TRANSFER,
    DESTINATION
}

data class JourneyBusMembership(
    val legIndex: Int,
    val stopIndex: Int,
    val stopEdgeCount: Int
)

data class JourneyAxisNode(
    val id: String,
    val label: String,
    val coordinate: JourneyCoordinate,
    val kind: JourneyAxisNodeKind,
    val summarySegmentId: String,
    val timelineTargetIds: Set<String>,
    val busMemberships: List<JourneyBusMembership> = emptyList(),
    val matchable: Boolean = false
)

data class JourneyPath(
    val points: List<JourneyCoordinate>,
    val lengthMeters: Double,
    val cumulativeStartMeters: Double
)

sealed interface JourneyAxisEdge {
    val id: String
    val fromNodeId: String
    val toNodeId: String
    val summarySegmentId: String
    val matchable: Boolean

    data class Bus(
        override val id: String,
        override val fromNodeId: String,
        override val toNodeId: String,
        override val summarySegmentId: String,
        override val matchable: Boolean,
        val legIndex: Int,
        val fromStopIndex: Int,
        val stopEdgeCount: Int,
        val geometry: List<JourneyCoordinate>
    ) : JourneyAxisEdge

    data class Walking(
        override val id: String,
        override val fromNodeId: String,
        override val toNodeId: String,
        override val summarySegmentId: String,
        override val matchable: Boolean,
        val paths: List<JourneyPath>,
        val totalPathLengthMeters: Double
    ) : JourneyAxisEdge
}

data class JourneyAxisIdentity(
    val pageGeneration: Long,
    val structureIdentity: Int,
    val contentFingerprint: Int
)

data class RouteJourneyAxis(
    val identity: JourneyAxisIdentity,
    val nodes: List<JourneyAxisNode>,
    val edges: List<JourneyAxisEdge>
) {
    val nodesById: Map<String, JourneyAxisNode> by lazy { nodes.associateBy(JourneyAxisNode::id) }
    val edgesById: Map<String, JourneyAxisEdge> by lazy { edges.associateBy(JourneyAxisEdge::id) }
}

sealed interface JourneyPosition {
    val regionKey: String?

    data class AtNode(
        val nodeId: String,
        val nodeLabel: String,
        val nodeKind: JourneyAxisNodeKind,
        val summarySegmentId: String,
        val timelineTargetIds: Set<String>,
        val legIndex: Int?,
        val stopIndex: Int?,
        val stopEdgeCount: Int?,
        val distanceToAxisMeters: Double
    ) : JourneyPosition {
        override val regionKey: String = "node:$nodeId"
    }

    data class BetweenNodes(
        val edgeId: String,
        val fromNodeId: String,
        val fromLabel: String,
        val toNodeId: String,
        val toLabel: String,
        val summarySegmentId: String,
        val legIndex: Int,
        val fromStopIndex: Int,
        val stopEdgeCount: Int,
        val distanceToAxisMeters: Double
    ) : JourneyPosition {
        override val regionKey: String = "edge:$edgeId"
    }

    data class WalkingProgress(
        val edgeId: String,
        val fromNodeId: String,
        val fromLabel: String,
        val toNodeId: String,
        val toLabel: String,
        val summarySegmentId: String,
        val progress: Double,
        val distanceToAxisMeters: Double
    ) : JourneyPosition {
        override val regionKey: String = "edge:$edgeId"
    }

    data object Unreliable : JourneyPosition {
        override val regionKey: String? = null
    }
}
