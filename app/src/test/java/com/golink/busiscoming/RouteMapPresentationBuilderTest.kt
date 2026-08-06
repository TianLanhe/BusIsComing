package com.golink.busiscoming

import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailTransfer
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.data.model.RouteGeometryPoint
import com.golink.busiscoming.data.model.RouteGeometrySegment
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.PedestrianCoordinate
import com.golink.busiscoming.data.model.PedestrianRoute
import com.golink.busiscoming.data.model.PedestrianRoutePath
import com.golink.busiscoming.data.model.RouteDetailWalkingState
import com.golink.busiscoming.ui.main.RouteDetailQueryEndpoint
import com.golink.busiscoming.ui.main.RouteMapLineKind
import com.golink.busiscoming.ui.main.RouteMapCoordinate
import com.golink.busiscoming.ui.main.RouteMapMarkerRole
import com.golink.busiscoming.ui.main.RouteMapPresentationBuilder
import com.golink.busiscoming.ui.main.RouteDetailUiFormatter
import com.golink.busiscoming.ui.main.RouteDetailUiItem
import com.golink.busiscoming.data.model.WaitTimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMapPresentationBuilderTest {
    @Test
    fun singleLegPresentationNeverInventsSchematicWalkingLines() {
        val key = RouteGeometryKey("N118-TOS-1", 5, 7)
        val geometry = RouteGeometrySegment(
            key,
            listOf(
                RouteGeometryPoint("p1", 22.3000, 114.1000),
                RouteGeometryPoint("p2", 22.3200, 114.1200)
            )
        )

        val presentation = RouteMapPresentationBuilder.build(
            detail = detail(transfer = false),
            queryOrigin = RouteDetailQueryEndpoint("起點", 22.2900, 114.0900),
            queryDestination = RouteDetailQueryEndpoint("終點", 22.3300, 114.1300),
            geometries = mapOf(key to geometry),
            selectedMarkerId = null
        )

        assertEquals(5, presentation.markers.size)
        assertEquals(1, presentation.lines.count { it.kind == RouteMapLineKind.BUS })
        assertEquals(0, presentation.lines.count { it.kind == RouteMapLineKind.WALKING })
        assertTrue(presentation.markers.any { it.role == RouteMapMarkerRole.QUERY_ORIGIN })
        assertTrue(presentation.markers.any { it.role == RouteMapMarkerRole.VIA })
        assertTrue(presentation.markers.any { it.role == RouteMapMarkerRole.QUERY_DESTINATION })
        assertFalse(presentation.markers.first { it.role == RouteMapMarkerRole.VIA }.showLabelByDefault)
        assertEquals(7, presentation.boundsPoints.size)
    }

    @Test
    fun sameStopTransferUsesOneCompositeMarkerWithoutWalkingLine() {
        val presentation = RouteMapPresentationBuilder.build(
            detail = multiLegDetail(RouteDetailTransferType.SAME_STOP),
            queryOrigin = null,
            queryDestination = null,
            geometries = emptyMap(),
            selectedMarkerId = null
        )

        val transfer = presentation.markers.single { it.role == RouteMapMarkerRole.TRANSFER }
        assertEquals(setOf(0, 1), transfer.legIndexes)
        assertEquals(listOf("82X", "102"), transfer.routeLabels)
        assertEquals(2, transfer.timelineStopIds.size)
        assertEquals(3, presentation.markers.size)
        assertFalse(presentation.lines.any { it.kind == RouteMapLineKind.WALKING })
    }

    @Test
    fun walkingTransferKeepsBothStopRolesWithoutInventingSchematicLine() {
        val presentation = RouteMapPresentationBuilder.build(
            detail = multiLegDetail(RouteDetailTransferType.WALK_TO_TRANSFER_STOP),
            queryOrigin = null,
            queryDestination = null,
            geometries = emptyMap(),
            selectedMarkerId = null
        )

        assertEquals(4, presentation.markers.size)
        assertEquals(1, presentation.markers.count { it.role == RouteMapMarkerRole.ALIGHTING && 0 in it.legIndexes })
        assertEquals(1, presentation.markers.count { it.role == RouteMapMarkerRole.BOARDING && 1 in it.legIndexes })
        assertFalse(presentation.lines.any { it.kind == RouteMapLineKind.WALKING })
    }

    @Test
    fun onlySuccessfulCsdiSegmentsCreateIndependentOrderedPathLines() {
        val route = PedestrianRoute(
            rawDistanceMeters = 123.4,
            rawTimeMinutes = 2.1,
            paths = listOf(
                PedestrianRoutePath(
                    listOf(
                        PedestrianCoordinate(22.2900, 114.0900),
                        PedestrianCoordinate(22.2910, 114.0910)
                    )
                ),
                PedestrianRoutePath(
                    listOf(
                        PedestrianCoordinate(22.2920, 114.0920),
                        PedestrianCoordinate(22.3000, 114.1000)
                    )
                )
            )
        )

        val presentation = RouteMapPresentationBuilder.build(
            detail = detail(transfer = false),
            queryOrigin = RouteDetailQueryEndpoint("起點", 22.2900, 114.0900),
            queryDestination = RouteDetailQueryEndpoint("終點", 22.3300, 114.1300),
            geometries = emptyMap(),
            selectedMarkerId = null,
            walkingSegments = mapOf(
                "origin" to RouteDetailWalkingState.CsdiSuccess(route),
                "destination" to RouteDetailWalkingState.CitybusFallback(80),
                "transfer:0" to RouteDetailWalkingState.Loading,
                "transfer:1" to RouteDetailWalkingState.SameStop
            )
        )

        val walking = presentation.lines.filter { it.kind == RouteMapLineKind.WALKING }
        assertEquals(listOf("walk:origin:path:0", "walk:origin:path:1"), walking.map { it.stableId })
        assertEquals(
            listOf(RouteMapCoordinate(22.2900, 114.0900), RouteMapCoordinate(22.2910, 114.0910)),
            walking[0].points
        )
        assertEquals(
            listOf(RouteMapCoordinate(22.2920, 114.0920), RouteMapCoordinate(22.3000, 114.1000)),
            walking[1].points
        )
        assertTrue(walking[0].points.last() != walking[1].points.first())
        assertTrue(presentation.boundsPoints.containsAll(walking.flatMap { it.points }))
    }

    @Test
    fun validatedGeometryCanRemainVisibleWhenTextDetailFails() {
        val key = RouteGeometryKey("780-CEF-1", 6, 17)
        val geometry = RouteGeometrySegment(
            key,
            listOf(
                RouteGeometryPoint("first", 22.26, 114.23),
                RouteGeometryPoint("second", 22.28, 114.15)
            )
        )

        val presentation = RouteMapPresentationBuilder.build(
            detail = null,
            queryOrigin = RouteDetailQueryEndpoint("起點", 22.25, 114.24),
            queryDestination = RouteDetailQueryEndpoint("終點", 22.29, 114.14),
            geometries = mapOf(key to geometry),
            selectedMarkerId = null,
            routePlan = listOf(P2pRouteLeg("CTB", key.routeVariant, "780", 6, 17, "O", null))
        )

        assertEquals(2, presentation.markers.size)
        assertEquals(1, presentation.lines.count { it.kind == RouteMapLineKind.BUS })
        assertEquals(4, presentation.boundsPoints.size)
    }

    @Test
    fun mapStopIdsMatchTimelineIdsForBidirectionalSelection() {
        val detail = detail(transfer = false)
        val presentation = RouteMapPresentationBuilder.build(
            detail = detail,
            queryOrigin = null,
            queryDestination = null,
            geometries = emptyMap(),
            selectedMarkerId = null
        )
        val timelineIds = RouteDetailUiFormatter.items(detail, setOf(0), WaitTimeState.Loading)
            .filter { it is RouteDetailUiItem.Stop || it is RouteDetailUiItem.ViaStop }
            .mapTo(mutableSetOf()) { it.stableId }

        assertEquals(timelineIds, presentation.markers.mapTo(mutableSetOf()) { it.stableId })
    }

    private fun detail(transfer: Boolean): RouteDetail {
        val first = RouteDetailLeg(
            route = "N118",
            routeVariant = "N118-TOS-1",
            directionText = "長沙灣",
            boardingStop = stop("上車站", 5, RouteDetailStopRole.BOARDING, 22.3000, 114.1000),
            viaStops = listOf(stop("途經站", 6, RouteDetailStopRole.VIA, 22.3100, 114.1100)),
            alightingStop = stop("下車站", 7, RouteDetailStopRole.ALIGHTING, 22.3200, 114.1200)
        )
        return RouteDetail(
            routeName = "N118",
            priceHkd = 17.8,
            durationMinutes = 13,
            walkingDistanceMeters = 262,
            legs = listOf(first),
            originName = "起點",
            destinationName = "終點"
        )
    }

    private fun multiLegDetail(transferType: RouteDetailTransferType): RouteDetail {
        val sharedLatitude = 22.3100
        val sharedLongitude = 114.1100
        val first = RouteDetailLeg(
            route = "82X",
            routeVariant = "82X-ISR-1",
            directionText = null,
            boardingStop = stop("第一段上車", 6, RouteDetailStopRole.BOARDING, 22.3000, 114.1000, "82X-ISR-1"),
            viaStops = emptyList(),
            alightingStop = stop("轉乘站", 9, RouteDetailStopRole.ALIGHTING, sharedLatitude, sharedLongitude, "82X-ISR-1")
        )
        val second = RouteDetailLeg(
            route = "102",
            routeVariant = "102-MEF-1",
            directionText = null,
            boardingStop = stop("轉乘站", 12, RouteDetailStopRole.BOARDING, sharedLatitude, sharedLongitude, "102-MEF-1"),
            viaStops = emptyList(),
            alightingStop = stop("第二段下車", 15, RouteDetailStopRole.ALIGHTING, 22.3200, 114.1200, "102-MEF-1")
        )
        return RouteDetail(
            routeName = "82X → 102",
            priceHkd = 20.0,
            durationMinutes = 35,
            walkingDistanceMeters = 0,
            legs = listOf(first, second),
            transfers = listOf(RouteDetailTransfer(transferType))
        )
    }

    private fun stop(
        name: String,
        sequence: Int,
        role: RouteDetailStopRole,
        latitude: Double,
        longitude: Double,
        routeVariant: String = "N118-TOS-1"
    ) = RouteDetailStop(
        rawName = name,
        displayName = name,
        stopId = sequence.toString(),
        sequence = sequence,
        latitude = latitude,
        longitude = longitude,
        routeVariant = routeVariant,
        role = role
    )
}
