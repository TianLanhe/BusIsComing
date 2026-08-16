package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.repository.CrossOperatorStaticParsers
import com.golink.busiscoming.data.repository.StaticDataValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossOperatorStaticParsersTest {
    @Test
    fun parsesOnlyJointGtfsAgenciesFromQuotedCsv() {
        val routes = CrossOperatorStaticParsers.parseJointGtfsRoutes(fixture("gtfs_routes.csv"))

        assertEquals(setOf("118", "S1"), routes.map { it.route }.toSet())
        assertEquals(BusOperator.KMB, routes.first { it.route == "118" }.partner)
        assertEquals(BusOperator.LWB, routes.first { it.route == "S1" }.partner)
    }

    @Test
    fun parsesKmbAndLwbStaticRecordsWithServiceTypeAndCoordinates() {
        val routes = CrossOperatorStaticParsers.parseKmbRoutes(fixture("kmb_route.json"))
        val routeStops = CrossOperatorStaticParsers.parseKmbRouteStops(fixture("kmb_route_stop.json"))
        val stops = CrossOperatorStaticParsers.parseKmbStops(fixture("kmb_stop.json"))

        assertEquals(listOf(BusOperator.KMB, BusOperator.LWB), routes.map { it.operator })
        assertEquals(listOf("1", "1", "1"), routeStops.map { it.serviceType })
        assertEquals("34F421B30D4CBFF5", routeStops.first().stopId)
        assertEquals(22.264705, stops.getValue("34F421B30D4CBFF5").latitude, 0.0000001)
    }

    @Test
    fun currentKmbStaticResponsesWithoutCoUseGtfsJointOperatorIdentity() {
        val routes = CrossOperatorStaticParsers.parseKmbRoutes(
            """{"data":[{"route":"118","bound":"O","service_type":"1"},{"route":"S1","bound":"I","service_type":"1"}]}""",
            mapOf("118" to BusOperator.KMB, "S1" to BusOperator.LWB)
        )
        val routeStops = CrossOperatorStaticParsers.parseKmbRouteStops(
            """{"data":[{"route":"S1","bound":"I","service_type":"1","seq":"1","stop":"A"}]}""",
            mapOf("S1" to BusOperator.LWB)
        )

        assertEquals(listOf(BusOperator.KMB, BusOperator.LWB), routes.map { it.operator })
        assertEquals(BusOperator.LWB, routeStops.single().operator)
    }

    @Test
    fun parsesCtbRouteSliceWithoutUsingNamesAsIdentity() {
        val routes = CrossOperatorStaticParsers.parseCtbRoutes(fixture("ctb_route.json"))
        val routeStops = CrossOperatorStaticParsers.parseCtbRouteStops(
            fixture("ctb_route_stop.json"),
            direction = "outbound"
        )
        val stop = CrossOperatorStaticParsers.parseCtbStop(fixture("ctb_stop.json"))

        assertEquals("118", routes.single().route)
        assertEquals("O", routes.single().direction)
        assertEquals("outbound", routeStops.single().direction)
        assertEquals("001227", stop.id)
        assertEquals(22.2648838, stop.latitude, 0.0000001)
    }

    @Test
    fun currentCtbFullRouteListMayOmitDirection() {
        val routes = CrossOperatorStaticParsers.parseCtbRoutes(
            """{"data":[{"co":"CTB","route":"118"}]}"""
        )

        assertEquals("118", routes.single().route)
        assertEquals("", routes.single().direction)
        assertThrows(StaticDataValidationException::class.java) {
            CrossOperatorStaticParsers.parseCtbRoutes(
                """{"data":[{"co":"KMB","route":"118"}]}"""
            )
        }
    }

    @Test
    fun rejectsInvalidCoordinatesAndDuplicateSequencesBeforePublishingVariant() {
        val stopRecords = CrossOperatorStaticParsers.parseKmbStops(fixture("kmb_stop.json"))
        val duplicate = CrossOperatorStaticParsers.parseKmbRouteStops(
            fixture("kmb_route_stop.json")
        ).let { listOf(it[0], it[1].copy(sequence = it[0].sequence)) }

        assertThrows(StaticDataValidationException::class.java) {
            CrossOperatorStaticParsers.buildVariants(duplicate, stopRecords)
        }
        assertTrue(
            runCatching {
                CrossOperatorStaticParsers.parseCtbStop(
                    """{"data":{"stop":"BAD","lat":"91","long":"114"}}"""
                )
            }.exceptionOrNull() is StaticDataValidationException
        )
    }

    private fun fixture(name: String): String {
        return requireNotNull(javaClass.classLoader?.getResource("cross_operator/$name"))
            .readText()
    }
}
