package com.golink.busiscoming

import com.golink.busiscoming.data.model.JourneyAxisNodeKind
import com.golink.busiscoming.data.model.JourneyPosition
import com.golink.busiscoming.ui.main.RouteCurrentPositionPresentation
import com.golink.busiscoming.ui.main.RouteCurrentPositionPresenter
import com.golink.busiscoming.ui.main.RoutePositionIndicatorGeometry
import com.golink.busiscoming.ui.main.RouteSummaryAnchorGeometry
import com.golink.busiscoming.ui.main.RouteTimelineRailGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCurrentPositionPresentationTest {
    @Test
    fun busNodeUsesStopIndexOverEdgeCount() {
        val result = RouteCurrentPositionPresenter.present(
            atNode(stopIndex = 2, stopEdgeCount = 4)
        )

        assertEquals("leg-0-card", result.summary.segmentId)
        assertEquals(0.5, result.summary.progress, 0.0)
    }

    @Test
    fun betweenBusStopsUsesFixedHalfEdgeProgress() {
        val result = RouteCurrentPositionPresenter.present(
            JourneyPosition.BetweenNodes(
                edgeId = "bus:0:1",
                fromNodeId = "a",
                fromLabel = "甲站",
                toNodeId = "b",
                toLabel = "乙站",
                summarySegmentId = "leg-0-card",
                legIndex = 0,
                fromStopIndex = 1,
                stopEdgeCount = 4,
                distanceToAxisMeters = 3.0
            )
        )

        assertEquals(0.375, result.summary.progress, 0.0)
        assertTrue(result.timeline is RouteCurrentPositionPresentation.Timeline.BetweenNodes)
    }

    @Test
    fun walkingKeepsActualPathProgress() {
        val result = RouteCurrentPositionPresenter.present(
            JourneyPosition.WalkingProgress(
                edgeId = "walk:origin",
                fromNodeId = "origin",
                fromLabel = "起點",
                toNodeId = "board",
                toLabel = "上車站",
                summarySegmentId = "walk-origin",
                progress = 0.37,
                distanceToAxisMeters = 4.0
            )
        )

        assertEquals(0.37, result.summary.progress, 0.0)
        assertEquals(
            RouteCurrentPositionPresentation.Timeline.Walking("walk-origin", 0.37),
            result.timeline
        )
    }

    @Test
    fun sameStopTransferUsesSegmentCenter() {
        val result = RouteCurrentPositionPresenter.present(
            atNode(
                stopIndex = 2,
                stopEdgeCount = 4,
                nodeKind = JourneyAxisNodeKind.SAME_STOP_TRANSFER,
                summarySegmentId = "transfer-0"
            )
        )

        assertEquals("transfer-0", result.summary.segmentId)
        assertEquals(0.5, result.summary.progress, 0.0)
    }

    @Test
    fun journeyEndpointsMapToWalkingEnds() {
        val origin = RouteCurrentPositionPresenter.present(
            atNode(
                stopIndex = null,
                stopEdgeCount = null,
                nodeKind = JourneyAxisNodeKind.ORIGIN,
                summarySegmentId = "walk-origin"
            )
        )
        val destination = RouteCurrentPositionPresenter.present(
            atNode(
                stopIndex = null,
                stopEdgeCount = null,
                nodeKind = JourneyAxisNodeKind.DESTINATION,
                summarySegmentId = "walk-destination"
            )
        )

        assertEquals(0.0, origin.summary.progress, 0.0)
        assertEquals(1.0, destination.summary.progress, 0.0)
    }

    @Test
    fun approvedIndicatorGeometryKeepsFixedUnstretchedDimensions() {
        assertEquals(18f, RoutePositionIndicatorGeometry.SUMMARY_PIN_WIDTH_DP, 0f)
        assertEquals(22f, RoutePositionIndicatorGeometry.SUMMARY_PIN_HEIGHT_DP, 0f)
        assertEquals(38f, RoutePositionIndicatorGeometry.HALO_DIAMETER_DP, 0f)
        assertEquals(26f, RoutePositionIndicatorGeometry.SUPPORT_DIAMETER_DP, 0f)
        assertEquals(20f, RoutePositionIndicatorGeometry.RING_DIAMETER_DP, 0f)
        assertEquals(8f, RoutePositionIndicatorGeometry.TAIL_LENGTH_DP, 0f)
        assertTrue(
            RoutePositionIndicatorGeometry.TAIL_HALF_BASE_DP <
                RoutePositionIndicatorGeometry.RING_DIAMETER_DP / 4f
        )
    }

    @Test
    fun summaryPinTipIncludesContentPaddingAndTargetsSegmentProgress() {
        val pinLeft = RouteSummaryAnchorGeometry.pinLeft(
            rowLeft = 9f,
            segmentLeft = 100f,
            segmentWidth = 80f,
            progress = 0.25,
            pinWidth = 18f
        )

        assertEquals(120f, pinLeft, 0f)
    }

    @Test
    fun timelineRailUsesApprovedAxisAndStationGeometry() {
        assertEquals(10f, RouteTimelineRailGeometry.BUS_AXIS_WIDTH_DP, 0f)
        assertEquals(2f, RouteTimelineRailGeometry.WALK_AXIS_WIDTH_DP, 0f)
        assertEquals(10f, RouteTimelineRailGeometry.VIA_DIAMETER_DP, 0f)
        assertEquals(2f, RouteTimelineRailGeometry.VIA_BOUNDARY_DP, 0f)
        assertEquals(16f, RouteTimelineRailGeometry.ENDPOINT_DIAMETER_DP, 0f)
        assertEquals(3f, RouteTimelineRailGeometry.ENDPOINT_OUTLINE_DP, 0f)
        assertEquals(4f, RouteTimelineRailGeometry.ENDPOINT_CORE_DP, 0f)
    }

    private fun atNode(
        stopIndex: Int?,
        stopEdgeCount: Int?,
        nodeKind: JourneyAxisNodeKind = JourneyAxisNodeKind.VIA,
        summarySegmentId: String = "leg-0-card"
    ): JourneyPosition.AtNode = JourneyPosition.AtNode(
        nodeId = "node",
        nodeLabel = "測試站",
        nodeKind = nodeKind,
        summarySegmentId = summarySegmentId,
        timelineTargetIds = setOf("leg-0-via-3"),
        legIndex = stopIndex?.let { 0 },
        stopIndex = stopIndex,
        stopEdgeCount = stopEdgeCount,
        distanceToAxisMeters = 2.0
    )
}
