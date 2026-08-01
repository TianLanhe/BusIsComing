package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteDetailWalkingKind
import com.golink.busiscoming.data.model.WaitTimeState

sealed class RouteDetailUiItem(open val stableId: String) {
    data object Loading : RouteDetailUiItem("loading")

    data object Error : RouteDetailUiItem("error")

    data class Summary(
        val routeName: String,
        val durationMinutes: Int,
        val plannedArrivalTime: String?,
        val priceHkd: Double,
        val totalViaStops: Int,
        val walkingDistanceMeters: Int,
        val isWalkingDistanceComplete: Boolean
    ) : RouteDetailUiItem("summary")

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

object RouteDetailUiFormatter {
    fun items(
        detail: RouteDetail,
        expandedLegIndexes: Set<Int>,
        firstLegEta: WaitTimeState
    ): List<RouteDetailUiItem> = buildList {
        add(
            RouteDetailUiItem.Summary(
                routeName = detail.routeName,
                durationMinutes = detail.durationMinutes,
                plannedArrivalTime = detail.plannedArrivalTime,
                priceHkd = detail.priceHkd,
                totalViaStops = detail.totalViaStopCount,
                walkingDistanceMeters = detail.displayWalkingDistanceMeters,
                isWalkingDistanceComplete = detail.hasCompleteWalkingDistance
            )
        )
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
                    "leg-$index-board",
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
                        add(RouteDetailUiItem.ViaStop("leg-$index-via-${stop.sequence}", index, stop, colorKey))
                    }
                }
            }
            add(
                RouteDetailUiItem.Stop(
                    "leg-$index-alight",
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
}
