package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pStopMap
import com.golink.busiscoming.data.model.P2pStopMapStop
import com.golink.busiscoming.data.model.PedestrianCoordinate
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailTransferType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@JvmInline
value class PedestrianSegmentId(val value: String)

enum class PedestrianSegmentRole {
    ORIGIN,
    TRANSFER,
    DESTINATION
}

enum class PedestrianEndpointFailure {
    MISSING,
    SOURCE_CONFLICT
}

sealed interface PedestrianSegment {
    val id: PedestrianSegmentId
    val role: PedestrianSegmentRole
    val citybusFallbackDistanceMeters: Int?

    data class Requestable(
        override val id: PedestrianSegmentId,
        override val role: PedestrianSegmentRole,
        val request: CsdiPedestrianRequest,
        override val citybusFallbackDistanceMeters: Int?
    ) : PedestrianSegment

    data class SameStop(
        override val id: PedestrianSegmentId,
        override val role: PedestrianSegmentRole = PedestrianSegmentRole.TRANSFER,
        override val citybusFallbackDistanceMeters: Int? = null
    ) : PedestrianSegment

    data class Unavailable(
        override val id: PedestrianSegmentId,
        override val role: PedestrianSegmentRole,
        val reason: PedestrianEndpointFailure,
        override val citybusFallbackDistanceMeters: Int?
    ) : PedestrianSegment
}

data class PedestrianRoutePlanKey(
    val endpointContextKey: String,
    val planFingerprint: String
)

data class PlannedPedestrianRoute(
    val key: PedestrianRoutePlanKey,
    val segments: List<PedestrianSegment>
)

object PedestrianSegmentPlanner {
    fun plan(
        query: P2pRouteDetailQuery,
        stopMap: P2pStopMap?,
        detail: RouteDetail?
    ): PlannedPedestrianRoute {
        val context = query.recoveryContext
        val segments = buildList {
            val firstLeg = query.plan.legs.firstOrNull()
            if (firstLeg != null) {
                val endpoint = resolveStopEndpoint(
                    stopMap = stopMap,
                    legIndex = 0,
                    leg = firstLeg,
                    sequence = firstLeg.boardingSeq,
                    detailStop = detail?.legs?.firstOrNull()?.boardingStop
                )
                add(
                    buildSegment(
                        id = PedestrianSegmentId("origin"),
                        role = PedestrianSegmentRole.ORIGIN,
                        start = context?.let { PedestrianCoordinate(it.originLatitude, it.originLongitude) },
                        end = endpoint,
                        fallbackDistance = detail?.originWalking?.distanceMeters
                    )
                )
            }

            if (detail != null) {
                detail.transfers.forEachIndexed { transferIndex, transfer ->
                    val id = PedestrianSegmentId("transfer:$transferIndex")
                    if (transfer.type == RouteDetailTransferType.SAME_STOP) {
                        add(PedestrianSegment.SameStop(id))
                    } else {
                        val previousLeg = query.plan.legs.getOrNull(transferIndex)
                        val nextLeg = query.plan.legs.getOrNull(transferIndex + 1)
                        val start = previousLeg?.let { leg ->
                            resolveStopEndpoint(
                                stopMap = stopMap,
                                legIndex = transferIndex,
                                leg = leg,
                                sequence = leg.alightingSeq,
                                detailStop = detail.legs.getOrNull(transferIndex)?.alightingStop
                            )
                        } ?: ResolvedEndpoint.Missing
                        val end = nextLeg?.let { leg ->
                            resolveStopEndpoint(
                                stopMap = stopMap,
                                legIndex = transferIndex + 1,
                                leg = leg,
                                sequence = leg.boardingSeq,
                                detailStop = detail.legs.getOrNull(transferIndex + 1)?.boardingStop
                            )
                        } ?: ResolvedEndpoint.Missing
                        add(
                            buildSegment(
                                id = id,
                                role = PedestrianSegmentRole.TRANSFER,
                                start = start,
                                end = end,
                                fallbackDistance = transfer.walking?.distanceMeters
                            )
                        )
                    }
                }
            }

            val lastLegIndex = query.plan.legs.lastIndex
            val lastLeg = query.plan.legs.lastOrNull()
            if (lastLeg != null) {
                val endpoint = resolveStopEndpoint(
                    stopMap = stopMap,
                    legIndex = lastLegIndex,
                    leg = lastLeg,
                    sequence = lastLeg.alightingSeq,
                    detailStop = detail?.legs?.lastOrNull()?.alightingStop
                )
                add(
                    buildSegment(
                        id = PedestrianSegmentId("destination"),
                        role = PedestrianSegmentRole.DESTINATION,
                        start = endpoint,
                        end = context?.let {
                            PedestrianCoordinate(it.destinationLatitude, it.destinationLongitude)
                        },
                        fallbackDistance = detail?.destinationWalking?.distanceMeters
                    )
                )
            }
        }
        return PlannedPedestrianRoute(
            key = PedestrianRoutePlanKey(
                endpointContextKey = context?.walkingContextKey().orEmpty(),
                planFingerprint = query.plan.fingerprint()
            ),
            segments = segments
        )
    }

    private fun buildSegment(
        id: PedestrianSegmentId,
        role: PedestrianSegmentRole,
        start: PedestrianCoordinate?,
        end: ResolvedEndpoint,
        fallbackDistance: Int?
    ): PedestrianSegment = buildSegment(
        id = id,
        role = role,
        start = start?.takeIf(PedestrianCoordinate::isValidWgs84)?.let(ResolvedEndpoint::Coordinate)
            ?: ResolvedEndpoint.Missing,
        end = end,
        fallbackDistance = fallbackDistance
    )

    private fun buildSegment(
        id: PedestrianSegmentId,
        role: PedestrianSegmentRole,
        start: ResolvedEndpoint,
        end: PedestrianCoordinate?,
        fallbackDistance: Int?
    ): PedestrianSegment = buildSegment(
        id = id,
        role = role,
        start = start,
        end = end?.takeIf(PedestrianCoordinate::isValidWgs84)?.let(ResolvedEndpoint::Coordinate)
            ?: ResolvedEndpoint.Missing,
        fallbackDistance = fallbackDistance
    )

    private fun buildSegment(
        id: PedestrianSegmentId,
        role: PedestrianSegmentRole,
        start: ResolvedEndpoint,
        end: ResolvedEndpoint,
        fallbackDistance: Int?
    ): PedestrianSegment {
        val failure = listOf(start, end).filterIsInstance<ResolvedEndpoint.Failed>().firstOrNull()
        if (failure != null) {
            return PedestrianSegment.Unavailable(id, role, failure.reason, fallbackDistance)
        }
        val startCoordinate = (start as? ResolvedEndpoint.Coordinate)?.value
        val endCoordinate = (end as? ResolvedEndpoint.Coordinate)?.value
        if (startCoordinate == null || endCoordinate == null) {
            return PedestrianSegment.Unavailable(
                id,
                role,
                PedestrianEndpointFailure.MISSING,
                fallbackDistance
            )
        }
        return PedestrianSegment.Requestable(
            id = id,
            role = role,
            request = CsdiPedestrianRequest(startCoordinate, endCoordinate),
            citybusFallbackDistanceMeters = fallbackDistance
        )
    }

    private fun resolveStopEndpoint(
        stopMap: P2pStopMap?,
        legIndex: Int,
        leg: P2pRouteLeg,
        sequence: Int,
        detailStop: RouteDetailStop?
    ): ResolvedEndpoint {
        val candidates = stopMap?.stops.orEmpty().filter {
            it.routeVariant == leg.routeVariant && it.sequence == sequence
        }
        val primary = candidates.firstOrNull { it.legIndex == legIndex }
            ?: candidates.singleOrNull()
            ?: return ResolvedEndpoint.Missing
        val primaryCoordinate = primary.coordinateOrNull()
        val fallbackCoordinate = detailStop
            ?.takeIf { it.matchesIdentity(primary) }
            ?.coordinateOrNull()
        if (primaryCoordinate != null && fallbackCoordinate != null &&
            distanceMeters(primaryCoordinate, fallbackCoordinate) > MAX_CITYBUS_SOURCE_DEVIATION_METERS
        ) {
            return ResolvedEndpoint.Failed(PedestrianEndpointFailure.SOURCE_CONFLICT)
        }
        return when {
            primaryCoordinate != null -> ResolvedEndpoint.Coordinate(primaryCoordinate)
            fallbackCoordinate != null -> ResolvedEndpoint.Coordinate(fallbackCoordinate)
            else -> ResolvedEndpoint.Missing
        }
    }

    private fun P2pStopMapStop.coordinateOrNull(): PedestrianCoordinate? =
        PedestrianCoordinate(latitude, longitude).takeIf(PedestrianCoordinate::isValidWgs84)

    private fun RouteDetailStop.coordinateOrNull(): PedestrianCoordinate? =
        PedestrianCoordinate(latitude, longitude).takeIf(PedestrianCoordinate::isValidWgs84)

    private fun RouteDetailStop.matchesIdentity(primary: P2pStopMapStop): Boolean =
        routeVariant == primary.routeVariant && sequence == primary.sequence && stopId == primary.stopId

    private fun distanceMeters(first: PedestrianCoordinate, second: PedestrianCoordinate): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private sealed interface ResolvedEndpoint {
        data class Coordinate(val value: PedestrianCoordinate) : ResolvedEndpoint
        data class Failed(val reason: PedestrianEndpointFailure) : ResolvedEndpoint
        data object Missing : ResolvedEndpoint
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MAX_CITYBUS_SOURCE_DEVIATION_METERS = 30.0
}
