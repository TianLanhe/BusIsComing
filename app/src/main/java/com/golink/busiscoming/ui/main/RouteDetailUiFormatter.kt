package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailWalkingKind
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.model.RouteDetailWalkingState
import com.golink.busiscoming.data.model.PedestrianRouteRounding

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

enum class RouteSummarySegmentKind {
    WALKING,
    BUS,
    SAME_STOP_TRANSFER
}

data class RouteSummarySegment(
    val detailTargetId: String,
    val kind: RouteSummarySegmentKind,
    val routeLabel: String? = null,
    val colorKey: Int? = null,
    val durationMinutes: Int? = null
)

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
        val isWalkingDistanceLoading: Boolean = false,
        val firstLegEta: WaitTimeState,
        val segments: List<RouteSummarySegment> = emptyList(),
        val currentPosition: RouteCurrentPositionPresentation? = null
    ) : RouteDetailUiItem("summary")

    data class DynamicStatus(
        val status: RouteDynamicDetailStatus
    ) : RouteDetailUiItem("dynamic-status")

    data class Walking(
        override val stableId: String,
        val kind: RouteDetailWalkingKind,
        val distanceMeters: Int?,
        val approximateMinutes: Int? = null,
        val isLoading: Boolean = false,
        val isUnavailable: Boolean = false
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
        dynamicStatus: RouteDynamicDetailStatus = RouteDynamicDetailStatus.CURRENT,
        walkingSegments: Map<String, RouteDetailWalkingState> = emptyMap(),
        currentPosition: RouteCurrentPositionPresentation? = null
    ): List<RouteDetailUiItem> = buildList {
        val walkingSummary = walkingSummary(detail, walkingSegments)
        add(
            RouteDetailUiItem.Summary(
                routeName = detail.routeName,
                durationMinutes = detail.durationMinutes,
                plannedArrivalTime = detail.plannedArrivalTime,
                priceHkd = detail.priceHkd,
                rideStopCount = RideStopCountState.Available(detail.totalViaStopCount),
                walkingDistanceMeters = walkingSummary.distanceMeters,
                isWalkingDistanceComplete = walkingSummary.complete,
                isWalkingDistanceLoading = walkingSummary.loading,
                firstLegEta = firstLegEta,
                segments = summarySegments(detail, walkingSegments, dynamicStatus),
                currentPosition = currentPosition
            )
        )
        if (dynamicStatus != RouteDynamicDetailStatus.CURRENT) {
            add(RouteDetailUiItem.DynamicStatus(dynamicStatus))
        }
        add(RouteDetailUiItem.Endpoint("origin", detail.originName, detail.plannedDepartureTime, true))
        add(
            walkingItem(
                stableId = "walk-origin",
                segmentId = "origin",
                kind = RouteDetailWalkingKind.ORIGIN,
                citybusDistance = detail.originWalking?.distanceMeters,
                states = walkingSegments
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
                    leg.fareHkd.takeIf { detail.legs.size > 1 },
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
                        walkingItem(
                            stableId = "walk-transfer-$index",
                            segmentId = "transfer:$index",
                            kind = RouteDetailWalkingKind.TRANSFER,
                            citybusDistance = transfer.walking?.distanceMeters,
                            states = walkingSegments
                        )
                    )
                }
            }
        }
        add(
            walkingItem(
                stableId = "walk-destination",
                segmentId = "destination",
                kind = RouteDetailWalkingKind.DESTINATION,
                citybusDistance = detail.destinationWalking?.distanceMeters,
                states = walkingSegments
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
            isWalkingDistanceLoading = true,
            firstLegEta = firstLegEta,
            segments = launchSegments(args)
        )
    }

    fun plannedMinutesBetween(start: String?, end: String?): Int? {
        val startMinutes = parseClockMinutes(start) ?: return null
        val endMinutes = parseClockMinutes(end) ?: return null
        val adjustedEnd = if (endMinutes < startMinutes) endMinutes + MINUTES_PER_DAY else endMinutes
        return (adjustedEnd - startMinutes).takeIf { it > 0 }
    }

    private fun summarySegments(
        detail: RouteDetail,
        states: Map<String, RouteDetailWalkingState>,
        dynamicStatus: RouteDynamicDetailStatus
    ): List<RouteSummarySegment> = buildList {
        add(walkingSummarySegment("walk-origin", "origin", states))
        detail.legs.forEachIndexed { index, leg ->
            add(
                RouteSummarySegment(
                    detailTargetId = "leg-$index-card",
                    kind = RouteSummarySegmentKind.BUS,
                    routeLabel = leg.route,
                    colorKey = index % 4,
                    durationMinutes = if (dynamicStatus == RouteDynamicDetailStatus.CURRENT) {
                        plannedMinutesBetween(leg.plannedBoardingTime, leg.plannedAlightingTime)
                    } else {
                        null
                    }
                )
            )
            detail.transfers.getOrNull(index)?.let { transfer ->
                if (transfer.type == RouteDetailTransferType.SAME_STOP) {
                    add(
                        RouteSummarySegment(
                            detailTargetId = "transfer-$index",
                            kind = RouteSummarySegmentKind.SAME_STOP_TRANSFER
                        )
                    )
                } else {
                    add(
                        walkingSummarySegment(
                            detailTargetId = "walk-transfer-$index",
                            segmentId = "transfer:$index",
                            states = states
                        )
                    )
                }
            }
        }
        add(walkingSummarySegment("walk-destination", "destination", states))
    }

    private fun launchSegments(args: RouteDetailLaunchArgs): List<RouteSummarySegment> = buildList {
        add(RouteSummarySegment("walk-origin", RouteSummarySegmentKind.WALKING))
        args.routeSegments.forEachIndexed { index, route ->
            add(
                RouteSummarySegment(
                    detailTargetId = "leg-$index-card",
                    kind = RouteSummarySegmentKind.BUS,
                    routeLabel = route,
                    colorKey = index % 4
                )
            )
            if (index < args.routeSegments.lastIndex) {
                add(
                    RouteSummarySegment(
                        detailTargetId = "walk-transfer-$index",
                        kind = RouteSummarySegmentKind.WALKING
                    )
                )
            }
        }
        add(RouteSummarySegment("walk-destination", RouteSummarySegmentKind.WALKING))
    }

    private fun walkingSummarySegment(
        detailTargetId: String,
        segmentId: String,
        states: Map<String, RouteDetailWalkingState>
    ): RouteSummarySegment = RouteSummarySegment(
        detailTargetId = detailTargetId,
        kind = RouteSummarySegmentKind.WALKING,
        durationMinutes = (states[segmentId] as? RouteDetailWalkingState.CsdiSuccess)
            ?.route
            ?.rawTimeMinutes
            ?.let(PedestrianRouteRounding::segmentMinutes)
    )

    private fun parseClockMinutes(value: String?): Int? {
        val match = CLOCK_PATTERN.matchEntire(value?.trim().orEmpty()) ?: return null
        val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hour * 60 + minute
    }

    private fun walkingItem(
        stableId: String,
        segmentId: String,
        kind: RouteDetailWalkingKind,
        citybusDistance: Int?,
        states: Map<String, RouteDetailWalkingState>
    ): RouteDetailUiItem.Walking {
        if (states.isEmpty()) return RouteDetailUiItem.Walking(stableId, kind, citybusDistance)
        return when (val state = states[segmentId]) {
            is RouteDetailWalkingState.CsdiSuccess -> RouteDetailUiItem.Walking(
                stableId = stableId,
                kind = kind,
                distanceMeters = PedestrianRouteRounding.segmentDistanceMeters(
                    state.route.rawDistanceMeters
                ),
                approximateMinutes = PedestrianRouteRounding.segmentMinutes(
                    state.route.rawTimeMinutes
                )
            )
            is RouteDetailWalkingState.CitybusFallback -> RouteDetailUiItem.Walking(
                stableId = stableId,
                kind = kind,
                distanceMeters = state.distanceMeters,
                isUnavailable = state.distanceMeters == null
            )
            RouteDetailWalkingState.Loading, null -> RouteDetailUiItem.Walking(
                stableId = stableId,
                kind = kind,
                distanceMeters = null,
                isLoading = true
            )
            RouteDetailWalkingState.SameStop -> RouteDetailUiItem.Walking(
                stableId = stableId,
                kind = kind,
                distanceMeters = null,
                isUnavailable = true
            )
        }
    }

    private fun walkingSummary(
        detail: RouteDetail,
        states: Map<String, RouteDetailWalkingState>
    ): WalkingSummary {
        if (states.isEmpty()) {
            return WalkingSummary(
                detail.displayWalkingDistanceMeters,
                detail.hasCompleteWalkingDistance,
                false
            )
        }
        val required = states.values.filterNot { it is RouteDetailWalkingState.SameStop }
        if (required.any { it is RouteDetailWalkingState.CitybusFallback }) {
            return WalkingSummary(detail.walkingDistanceMeters, false, false)
        }
        val successes = required.filterIsInstance<RouteDetailWalkingState.CsdiSuccess>()
        if (required.isNotEmpty() && successes.size == required.size) {
            return WalkingSummary(
                PedestrianRouteRounding.totalDistanceMeters(
                    successes.map { it.route.rawDistanceMeters }
                ),
                true,
                false
            )
        }
        return WalkingSummary(detail.walkingDistanceMeters, false, true)
    }

    private data class WalkingSummary(
        val distanceMeters: Int,
        val complete: Boolean,
        val loading: Boolean
    )

    private val CLOCK_PATTERN = Regex("^(\\d{1,2}):(\\d{2})$")
    private const val MINUTES_PER_DAY = 24 * 60
}
