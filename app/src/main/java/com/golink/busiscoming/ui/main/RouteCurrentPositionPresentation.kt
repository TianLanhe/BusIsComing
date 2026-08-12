package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.JourneyAxisNodeKind
import com.golink.busiscoming.data.model.JourneyPosition

data class RouteCurrentPositionPresentation(
    val regionKey: String,
    val summary: Summary,
    val timeline: Timeline,
    val announcement: Announcement
) {
    data class Summary(
        val segmentId: String,
        val progress: Double
    )

    sealed interface Timeline {
        data class AtNode(val targetIds: Set<String>) : Timeline
        data class BetweenNodes(val fromNodeId: String, val toNodeId: String) : Timeline
        data class Walking(val targetId: String, val progress: Double) : Timeline
    }

    sealed interface Announcement {
        data class NearNode(val name: String) : Announcement
        data class Between(val fromName: String, val toName: String) : Announcement
    }
}

object RouteCurrentPositionPresenter {
    fun present(position: JourneyPosition): RouteCurrentPositionPresentation {
        require(position !== JourneyPosition.Unreliable)
        return when (position) {
            is JourneyPosition.AtNode -> {
                val summaryProgress = when (position.nodeKind) {
                    JourneyAxisNodeKind.ORIGIN -> 0.0
                    JourneyAxisNodeKind.DESTINATION -> 1.0
                    JourneyAxisNodeKind.SAME_STOP_TRANSFER -> 0.5
                    JourneyAxisNodeKind.BOARDING,
                    JourneyAxisNodeKind.VIA,
                    JourneyAxisNodeKind.ALIGHTING -> {
                        val edges = position.stopEdgeCount?.takeIf { it > 0 }
                        val stop = position.stopIndex
                        if (edges == null || stop == null) 0.5 else stop.toDouble() / edges
                    }
                }
                RouteCurrentPositionPresentation(
                    regionKey = requireNotNull(position.regionKey),
                    summary = RouteCurrentPositionPresentation.Summary(
                        position.summarySegmentId,
                        summaryProgress.coerceIn(0.0, 1.0)
                    ),
                    timeline = RouteCurrentPositionPresentation.Timeline.AtNode(
                        position.timelineTargetIds
                    ),
                    announcement = RouteCurrentPositionPresentation.Announcement.NearNode(
                        position.nodeLabel
                    )
                )
            }
            is JourneyPosition.BetweenNodes -> RouteCurrentPositionPresentation(
                regionKey = requireNotNull(position.regionKey),
                summary = RouteCurrentPositionPresentation.Summary(
                    position.summarySegmentId,
                    ((position.fromStopIndex + 0.5) / position.stopEdgeCount.coerceAtLeast(1))
                        .coerceIn(0.0, 1.0)
                ),
                timeline = RouteCurrentPositionPresentation.Timeline.BetweenNodes(
                    position.fromNodeId,
                    position.toNodeId
                ),
                announcement = RouteCurrentPositionPresentation.Announcement.Between(
                    position.fromLabel,
                    position.toLabel
                )
            )
            is JourneyPosition.WalkingProgress -> RouteCurrentPositionPresentation(
                regionKey = requireNotNull(position.regionKey),
                summary = RouteCurrentPositionPresentation.Summary(
                    position.summarySegmentId,
                    position.progress.coerceIn(0.0, 1.0)
                ),
                timeline = RouteCurrentPositionPresentation.Timeline.Walking(
                    position.summarySegmentId,
                    position.progress.coerceIn(0.0, 1.0)
                ),
                announcement = RouteCurrentPositionPresentation.Announcement.Between(
                    position.fromLabel,
                    position.toLabel
                )
            )
            JourneyPosition.Unreliable -> error("Unreliable position has no presentation")
        }
    }
}

object RoutePositionIndicatorGeometry {
    const val SUMMARY_PIN_WIDTH_DP = 18f
    const val SUMMARY_PIN_HEIGHT_DP = 22f
    const val HALO_DIAMETER_DP = 38f
    const val SUPPORT_DIAMETER_DP = 26f
    const val RING_DIAMETER_DP = 20f
    const val TAIL_LENGTH_DP = 8f
    const val TAIL_HALF_BASE_DP = 2f
}

object RouteSummaryAnchorGeometry {
    fun pinLeft(
        rowLeft: Float,
        segmentLeft: Float,
        segmentWidth: Float,
        progress: Double,
        pinWidth: Float
    ): Float = rowLeft + segmentLeft +
        segmentWidth * progress.coerceIn(0.0, 1.0).toFloat() - pinWidth / 2f
}

data class RouteTimelineAnchorBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

data class RouteTimelineAnchorPoint(val x: Float, val y: Float)

object RouteTimelineAnchorGeometry {
    fun atNode(bounds: RouteTimelineAnchorBounds): RouteTimelineAnchorPoint =
        RouteTimelineAnchorPoint(bounds.centerX, bounds.centerY)

    fun betweenNodes(
        from: RouteTimelineAnchorBounds,
        to: RouteTimelineAnchorBounds
    ): RouteTimelineAnchorPoint = RouteTimelineAnchorPoint(
        (from.centerX + to.centerX) / 2f,
        (from.centerY + to.centerY) / 2f
    )

    fun walking(
        bounds: RouteTimelineAnchorBounds,
        progress: Double
    ): RouteTimelineAnchorPoint = RouteTimelineAnchorPoint(
        bounds.centerX,
        bounds.top + (bounds.bottom - bounds.top) * progress.coerceIn(0.0, 1.0).toFloat()
    )
}
