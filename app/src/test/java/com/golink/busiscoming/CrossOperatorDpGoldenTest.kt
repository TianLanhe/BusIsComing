package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.CrossOperatorMatchStatus
import com.golink.busiscoming.data.model.StaticRouteStop
import com.golink.busiscoming.data.model.StaticRouteVariant
import com.golink.busiscoming.data.repository.CrossOperatorRouteMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossOperatorDpGoldenTest {
    @Test
    fun route118GoldenSkipsAnExtraKmbStopAndMapsForwardSequence() {
        val ctb = variant(BusOperator.CTB, "118", "outbound", "", points("C", 0.0, 0.001, 0.002))
        val kmb = variant(
            BusOperator.KMB,
            "118",
            "I",
            "1",
            points("K", 0.0, 0.0005, 0.001, 0.002)
        )

        val result = CrossOperatorRouteMatcher().match(ctb, listOf(kmb))

        assertEquals(CrossOperatorMatchStatus.MATCHED, result.status)
        assertEquals(listOf("K1", "K3", "K4"), result.stopPairs.map { it.operatorStopId })
    }

    @Test
    fun airportLwbGoldensSelectSameRouteWithoutUsingNames() {
        listOf("S1", "R8").forEach { route ->
            val ctb = variant(
                BusOperator.CTB,
                route,
                "inbound",
                "",
                points("C", 0.0, 0.001, 0.002).map { it.copy(name = "城巴完全不同站名") }
            )
            val lwb = variant(
                BusOperator.LWB,
                route,
                "I",
                "1",
                points("L", 0.00001, 0.00101, 0.00201).map { it.copy(name = "LWB unrelated name") }
            )

            val result = CrossOperatorRouteMatcher().match(ctb, listOf(lwb))

            assertEquals(route, result.winner?.route)
            assertEquals(BusOperator.LWB, result.winner?.operator)
            assertEquals(3, result.stopPairs.size)
        }
    }

    @Test
    fun circularAndGapGoldensPreserveOrderAndOnlyReturnDiagonalPairs() {
        val ring = variant(
            BusOperator.CTB,
            "RING",
            "outbound",
            "",
            points("C", 0.0, 0.001, 0.0)
        )
        val partner = variant(
            BusOperator.KMB,
            "RING",
            "O",
            "1",
            points("K", 0.00001, 0.0005, 0.00101, 0.00001)
        )

        val result = CrossOperatorRouteMatcher().match(ring, listOf(partner))

        assertEquals(listOf("K1", "K3", "K4"), result.stopPairs.map { it.operatorStopId })
        assertTrue(result.stopPairs.zipWithNext().all { (first, second) ->
            first.operatorSequence < second.operatorSequence
        })
    }

    @Test
    fun equalCostAndThresholdBoundaryAreDeterministicWithoutSecondPlaceDelta() {
        val ctb = variant(BusOperator.CTB, "T", "outbound", "", points("C", 0.0))
        val serviceTwo = variant(BusOperator.KMB, "T", "O", "2", points("B", 0.0001))
        val serviceOne = variant(BusOperator.KMB, "T", "O", "1", points("A", 0.0001))
        val measured = CrossOperatorRouteMatcher(thresholdMetersPerStop = 1_000.0)
            .match(ctb, listOf(serviceTwo, serviceOne))

        assertEquals("1", measured.winner?.serviceType)
        assertEquals(
            CrossOperatorMatchStatus.MATCHED,
            CrossOperatorRouteMatcher(thresholdMetersPerStop = measured.normalizedCost)
                .match(ctb, listOf(serviceTwo)).status
        )
        assertEquals(
            CrossOperatorMatchStatus.NO_MATCH,
            CrossOperatorRouteMatcher(
                thresholdMetersPerStop = Math.nextDown(measured.normalizedCost)
            ).match(ctb, listOf(serviceTwo)).status
        )
    }

    private fun points(prefix: String, vararg latitudes: Double): List<StaticRouteStop> {
        return latitudes.mapIndexed { index, latitude ->
            StaticRouteStop("$prefix${index + 1}", index + 1, 22.28 + latitude, 114.15, "$prefix $index")
        }
    }

    private fun variant(
        operator: BusOperator,
        route: String,
        direction: String,
        serviceType: String,
        stops: List<StaticRouteStop>
    ) = StaticRouteVariant(operator, route, direction, serviceType, stops)
}
