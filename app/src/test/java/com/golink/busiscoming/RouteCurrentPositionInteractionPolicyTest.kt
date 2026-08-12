package com.golink.busiscoming

import com.golink.busiscoming.data.location.RouteCurrentPositionInteraction
import com.golink.busiscoming.data.location.RouteCurrentPositionInteractionPolicy
import com.golink.busiscoming.data.model.JourneyAxisNodeKind
import com.golink.busiscoming.data.model.JourneyPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCurrentPositionInteractionPolicyTest {
    @Test
    fun firstReliableBusRegionAnnouncesAndExpandsLegOnlyOnce() {
        val policy = RouteCurrentPositionInteractionPolicy()
        val node = busNode(legIndex = 0, region = "one")

        val first = policy.update(node)
        val repeated = policy.update(node)

        assertTrue(first.contains(RouteCurrentPositionInteraction.AutoExpandLeg(0)))
        assertTrue(first.contains(RouteCurrentPositionInteraction.Announce(node)))
        assertTrue(repeated.isEmpty())
    }

    @Test
    fun enteringAnotherBusLegCanExpandThatLegWithoutChangingOthers() {
        val policy = RouteCurrentPositionInteractionPolicy()
        policy.update(busNode(0, "first"))

        val effects = policy.update(busNode(1, "second"))

        assertTrue(effects.contains(RouteCurrentPositionInteraction.AutoExpandLeg(1)))
        assertTrue(effects.none { it == RouteCurrentPositionInteraction.AutoExpandLeg(0) })
    }

    @Test
    fun manualCollapsePreventsReopenAndWalkingNeverExpandsBusLeg() {
        val policy = RouteCurrentPositionInteractionPolicy()
        policy.userCollapsedLeg(0)

        val busEffects = policy.update(busNode(0, "bus"))
        val walk = JourneyPosition.WalkingProgress(
            edgeId = "walk:origin",
            fromNodeId = "origin",
            fromLabel = "起點",
            toNodeId = "board",
            toLabel = "上車站",
            summarySegmentId = "walk-origin",
            progress = 0.4,
            distanceToAxisMeters = 2.0
        )
        val walkEffects = policy.update(walk)

        assertTrue(busEffects.none { it is RouteCurrentPositionInteraction.AutoExpandLeg })
        assertTrue(walkEffects.none { it is RouteCurrentPositionInteraction.AutoExpandLeg })
    }

    @Test
    fun busLegWithoutViaStopsDoesNotAutoExpand() {
        val policy = RouteCurrentPositionInteractionPolicy()

        val effects = policy.update(busNode(0, "direct", stopEdgeCount = 1))

        assertTrue(effects.none { it is RouteCurrentPositionInteraction.AutoExpandLeg })
    }

    private fun busNode(
        legIndex: Int,
        region: String,
        stopEdgeCount: Int = 2
    ) = JourneyPosition.AtNode(
        nodeId = region,
        nodeLabel = region,
        nodeKind = JourneyAxisNodeKind.VIA,
        summarySegmentId = "leg-$legIndex-card",
        timelineTargetIds = setOf("leg-$legIndex-via-2"),
        legIndex = legIndex,
        stopIndex = 1,
        stopEdgeCount = stopEdgeCount,
        distanceToAxisMeters = 1.0
    )
}
