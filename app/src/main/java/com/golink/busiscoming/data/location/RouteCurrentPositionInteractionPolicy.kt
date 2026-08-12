package com.golink.busiscoming.data.location

import com.golink.busiscoming.data.model.JourneyPosition

sealed interface RouteCurrentPositionInteraction {
    data class AutoExpandLeg(val legIndex: Int) : RouteCurrentPositionInteraction
    data class Announce(val position: JourneyPosition) : RouteCurrentPositionInteraction
}

data class RouteCurrentPositionInteractionState(
    val expansionConsumedLegs: Set<Int> = emptySet(),
    val userCollapsedLegs: Set<Int> = emptySet(),
    val announcedRegionKey: String? = null
)

class RouteCurrentPositionInteractionPolicy(
    restoredState: RouteCurrentPositionInteractionState? = null
) {
    private val expansionConsumedLegs = restoredState?.expansionConsumedLegs
        ?.toMutableSet()
        ?: mutableSetOf()
    private val userCollapsedLegs = restoredState?.userCollapsedLegs
        ?.toMutableSet()
        ?: mutableSetOf()
    private var announcedRegionKey: String? = restoredState?.announcedRegionKey

    fun update(position: JourneyPosition): List<RouteCurrentPositionInteraction> = buildList {
        val regionKey = position.regionKey ?: return@buildList
        if (announcedRegionKey != regionKey) {
            announcedRegionKey = regionKey
            add(RouteCurrentPositionInteraction.Announce(position))
        }
        val legIndex = position.busLegWithViaStops() ?: return@buildList
        if (legIndex !in expansionConsumedLegs && legIndex !in userCollapsedLegs) {
            expansionConsumedLegs += legIndex
            add(RouteCurrentPositionInteraction.AutoExpandLeg(legIndex))
        }
    }

    fun userCollapsedLeg(legIndex: Int) {
        userCollapsedLegs += legIndex
        expansionConsumedLegs += legIndex
    }

    fun state(): RouteCurrentPositionInteractionState = RouteCurrentPositionInteractionState(
        expansionConsumedLegs = expansionConsumedLegs.toSet(),
        userCollapsedLegs = userCollapsedLegs.toSet(),
        announcedRegionKey = announcedRegionKey
    )

    private fun JourneyPosition.busLegWithViaStops(): Int? = when (this) {
        is JourneyPosition.AtNode -> legIndex?.takeIf { (stopEdgeCount ?: 0) > 1 }
        is JourneyPosition.BetweenNodes -> legIndex.takeIf { stopEdgeCount > 1 }
        is JourneyPosition.WalkingProgress,
        JourneyPosition.Unreliable -> null
    }
}
