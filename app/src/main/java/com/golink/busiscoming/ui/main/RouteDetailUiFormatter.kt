package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailWalkingKind
import com.golink.busiscoming.data.model.WaitTimeState

sealed interface RideStopCountState {
    data object Loading : RideStopCountState

    data class Available(val count: Int) : RideStopCountState

    data object Unavailable : RideStopCountState
}

enum class RouteDynamicDetailStatus {
    CURRENT,
    REFRESHING,
    STALE_AFTER_ERROR
}

sealed class RouteDetailUiItem(open val stableId: String) {
    data object Loading : RouteDetailUiItem("loading")

    data object Error : RouteDetailUiItem("error")

    data class Summary(
        val routeName: String,
        val durationMinutes: Int,
        val plannedArrivalTime: String?,
        val priceHkd: Double,
        val rideStopCount: RideStopCountState,
        val walkingDistanceMeters: Int,
        val isWalkingDistanceComplete: Boolean,
        val firstLegEta: WaitTimeState
    ) : RouteDetailUiItem("summary")

    data class DynamicStatus(
        val status: RouteDynamicDetailStatus
    ) : RouteDetailUiItem("dynamic-status")

    data class Walking(
        override val stableId: String,
        val kind: RouteDetailWalkingKind,
        val distanceMeters: Int?
    ) : RouteDetailUiItem(stableId)

    data class Stop(
        override val stableId: String,
        val legIndex: Int,
        val stop: RouteDetailStop,
        val isBoarding: Boolean,
        val plannedTime: String?,
        val colorKey: Int
    ) : RouteDetailUiItem(stableId)

    data class BusLeg(
        override val stableId: String,
        val legIndex: Int,
        val route: String,
        val direction: String?,
        val fareHkd: Double?,
        val stopCount: Int,
        val liveEta: WaitTimeState?,
        val colorKey: Int
    ) : RouteDetailUiItem(stableId)

    data class ViaToggle(
        override val stableId: String,
        val legIndex: Int,
        val count: Int,
        val expanded: Boolean,
        val colorKey: Int
    ) : RouteDetailUiItem(stableId)

    data class ViaStop(
        override val stableId: String,
        val legIndex: Int,
        val stop: RouteDetailStop,
        val colorKey: Int
    ) : RouteDetailUiItem(stableId)

    data class Transfer(
        override val stableId: String,
        val type: RouteDetailTransferType
    ) : RouteDetailUiItem(stableId)

    data class Endpoint(
        override val stableId: String,
        val name: String?,
        val plannedTime: String?,
        val isOrigin: Boolean
    ) : RouteDetailUiItem(stableId)
}

object RouteDetailTimelineStableIds {
    fun stop(legIndex: Int, stop: RouteDetailStop): String = when (stop.role) {
        RouteDetailStopRole.BOARDING -> "leg-$legIndex-board"
        RouteDetailStopRole.VIA -> "leg-$legIndex-via-${stop.sequence}"
        RouteDetailStopRole.ALIGHTING -> "leg-$legIndex-alight"
    }
}

object RouteDetailUiFormatter {
    fun items(
        detail: RouteDetail,
        expandedLegIndexes: Set<Int>,
        firstLegEta: WaitTimeState,
        dynamicStatus: RouteDynamicDetailStatus = RouteDynamicDetailStatus.CURRENT
    ): List<RouteDetailUiItem> = buildList {
        add(
            RouteDetailUiItem.Summary(
                routeName = detail.routeName,
                durationMinutes = detail.durationMinutes,
                plannedArrivalTime = detail.plannedArrivalTime,
                priceHkd = detail.priceHkd,
                rideStopCount = RideStopCountState.Available(detail.totalViaStopCount),
                walkingDistanceMeters = detail.displayWalkingDistanceMeters,
                isWalkingDistanceComplete = detail.hasCompleteWalkingDistance,
                firstLegEta = firstLegEta
            )
        )
        if (dynamicStatus != RouteDynamicDetailStatus.CURRENT) {
            add(RouteDetailUiItem.DynamicStatus(dynamicStatus))
        }
        add(RouteDetailUiItem.Endpoint("origin", detail.originName, detail.plannedDepartureTime, true))
        add(
            RouteDetailUiItem.Walking(
                "walk-origin",
                RouteDetailWalkingKind.ORIGIN,
                detail.originWalking?.distanceMeters
            )
        )
        detail.legs.forEachIndexed { index, leg ->
            val colorKey = index % 4
            add(
                RouteDetailUiItem.Stop(
                    RouteDetailTimelineStableIds.stop(index, leg.boardingStop),
                    index,
                    leg.boardingStop,
                    true,
                    leg.plannedBoardingTime,
                    colorKey
                )
            )
            add(
                RouteDetailUiItem.BusLeg(
                    "leg-$index-card",
                    index,
                    leg.route,
                    leg.directionText,
                    leg.fareHkd,
                    leg.viaStops.size + 2,
                    if (index == 0) firstLegEta else null,
                    colorKey
                )
            )
            if (leg.viaStops.isNotEmpty()) {
                val expanded = index in expandedLegIndexes
                add(RouteDetailUiItem.ViaToggle("leg-$index-toggle", index, leg.viaStops.size, expanded, colorKey))
                if (expanded) {
                    leg.viaStops.forEach { stop ->
                        add(RouteDetailUiItem.ViaStop(RouteDetailTimelineStableIds.stop(index, stop), index, stop, colorKey))
                    }
                }
            }
            add(
                RouteDetailUiItem.Stop(
                    RouteDetailTimelineStableIds.stop(index, leg.alightingStop),
                    index,
                    leg.alightingStop,
                    false,
                    leg.plannedAlightingTime,
                    colorKey
                )
            )
            detail.transfers.getOrNull(index)?.let { transfer ->
                add(RouteDetailUiItem.Transfer("transfer-$index", transfer.type))
                if (transfer.type == RouteDetailTransferType.WALK_TO_TRANSFER_STOP) {
                    add(
                        RouteDetailUiItem.Walking(
                            "walk-transfer-$index",
                            RouteDetailWalkingKind.TRANSFER,
                            transfer.walking?.distanceMeters
                        )
                    )
                }
            }
        }
        add(
            RouteDetailUiItem.Walking(
                "walk-destination",
                RouteDetailWalkingKind.DESTINATION,
                detail.destinationWalking?.distanceMeters
            )
        )
        add(RouteDetailUiItem.Endpoint("destination", detail.destinationName, detail.plannedArrivalTime, false))
    }

    fun launchSummary(
        args: RouteDetailLaunchArgs,
        firstLegEta: WaitTimeState,
        rideStopCount: RideStopCountState
    ): RouteDetailUiItem.Summary {
        val arrival = args.routeDetailQuery?.generalInfo
            ?.substringBefore("|*|")
            ?.takeIf { it.contains(':') }
        return RouteDetailUiItem.Summary(
            routeName = args.routeName,
            durationMinutes = args.durationMinutes,
            plannedArrivalTime = arrival,
            priceHkd = args.priceHkd,
            rideStopCount = rideStopCount,
            walkingDistanceMeters = args.walkingDistanceMeters,
            isWalkingDistanceComplete = false,
            firstLegEta = firstLegEta
        )
    }
}
