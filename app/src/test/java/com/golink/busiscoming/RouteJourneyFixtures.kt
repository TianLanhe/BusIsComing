package com.golink.busiscoming

import com.golink.busiscoming.data.location.JourneyAxisBuildInput
import com.golink.busiscoming.data.location.JourneyEndpoint
import com.golink.busiscoming.data.model.PedestrianCoordinate
import com.golink.busiscoming.data.model.PedestrianRoute
import com.golink.busiscoming.data.model.PedestrianRoutePath
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailTransfer
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteDetailWalkingState
import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.data.model.RouteGeometryPoint
import com.golink.busiscoming.data.model.RouteGeometrySegment

object RouteJourneyFixtures {
    const val BASE_LATITUDE = 22.3000
    const val BASE_LONGITUDE = 114.1700

    fun stop(
        name: String,
        sequence: Int,
        longitudeOffset: Double,
        variant: String = "R1-A",
        role: RouteDetailStopRole
    ): RouteDetailStop = RouteDetailStop(
        rawName = name,
        displayName = name,
        stopId = "$variant-$sequence",
        sequence = sequence,
        latitude = BASE_LATITUDE,
        longitude = BASE_LONGITUDE + longitudeOffset,
        routeVariant = variant,
        role = role
    )

    fun leg(
        variant: String = "R1-A",
        route: String = "R1",
        baseSequence: Int = 1,
        longitudeStart: Double = 0.0010
    ): RouteDetailLeg = RouteDetailLeg(
        route = route,
        routeVariant = variant,
        directionText = "測試方向",
        boardingStop = stop(
            "$route 上車站",
            baseSequence,
            longitudeStart,
            variant,
            RouteDetailStopRole.BOARDING
        ),
        viaStops = listOf(
            stop(
                "$route 中途站",
                baseSequence + 1,
                longitudeStart + 0.0010,
                variant,
                RouteDetailStopRole.VIA
            )
        ),
        alightingStop = stop(
            "$route 下車站",
            baseSequence + 2,
            longitudeStart + 0.0020,
            variant,
            RouteDetailStopRole.ALIGHTING
        )
    )

    fun directDetail(): RouteDetail = RouteDetail(
        routeName = "R1",
        priceHkd = 8.0,
        durationMinutes = 20,
        walkingDistanceMeters = 220,
        legs = listOf(leg()),
        originName = "測試起點",
        destinationName = "測試終點"
    )

    fun transferDetail(sameStop: Boolean): RouteDetail {
        val first = leg()
        val secondStart = if (sameStop) 0.0030 else 0.0040
        val second = leg("R2-A", "R2", 10, secondStart)
        return directDetail().copy(
            routeName = "R1 → R2",
            legs = listOf(first, second),
            transfers = listOf(
                RouteDetailTransfer(
                    if (sameStop) RouteDetailTransferType.SAME_STOP
                    else RouteDetailTransferType.WALK_TO_TRANSFER_STOP
                )
            )
        )
    }

    fun geometry(leg: RouteDetailLeg, reversed: Boolean = false): RouteGeometrySegment {
        val stops = listOf(leg.boardingStop) + leg.viaStops + leg.alightingStop
        val ordered = if (reversed) stops.reversed() else stops
        return RouteGeometrySegment(
            key = RouteGeometryKey(
                leg.routeVariant,
                leg.boardingStop.sequence,
                leg.alightingStop.sequence
            ),
            points = ordered.mapIndexed { index, stop ->
                RouteGeometryPoint("p$index", stop.latitude, stop.longitude)
            }
        )
    }

    fun walkingRoute(vararg longitudeRanges: Pair<Double, Double>): RouteDetailWalkingState.CsdiSuccess {
        val paths = longitudeRanges.map { (start, end) ->
            PedestrianRoutePath(
                listOf(
                    PedestrianCoordinate(BASE_LATITUDE, BASE_LONGITUDE + start),
                    PedestrianCoordinate(BASE_LATITUDE, BASE_LONGITUDE + end)
                )
            )
        }
        return RouteDetailWalkingState.CsdiSuccess(
            PedestrianRoute(
                rawDistanceMeters = 100.0,
                rawTimeMinutes = 2.0,
                paths = paths
            )
        )
    }

    fun input(
        detail: RouteDetail = directDetail(),
        geometries: Map<RouteGeometryKey, RouteGeometrySegment> = detail.legs.associate { leg ->
            geometry(leg).let { it.key to it }
        },
        walkingSegments: Map<String, RouteDetailWalkingState> = mapOf(
            "origin" to walkingRoute(0.0 to 0.0010),
            "destination" to walkingRoute(0.0030 to 0.0040)
        ),
        pageGeneration: Long = 7L,
        structureIdentity: Int = 42
    ): JourneyAxisBuildInput = JourneyAxisBuildInput(
        pageGeneration = pageGeneration,
        structureIdentity = structureIdentity,
        origin = JourneyEndpoint("測試起點", BASE_LATITUDE, BASE_LONGITUDE),
        destination = JourneyEndpoint("測試終點", BASE_LATITUDE, BASE_LONGITUDE + 0.0040),
        detail = detail,
        geometries = geometries,
        walkingSegments = walkingSegments
    )
}
