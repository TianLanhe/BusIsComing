package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.CrossOperatorEtaQuery
import com.golink.busiscoming.data.model.CrossOperatorMatchStatus
import com.golink.busiscoming.data.model.StaticRouteStop
import com.golink.busiscoming.data.model.StaticRouteVariant
import com.golink.busiscoming.data.repository.CrossOperatorRouteMatcher
import com.golink.busiscoming.data.repository.HongKongDataDay
import com.golink.busiscoming.data.repository.P2pCrossOperatorGate
import com.golink.busiscoming.data.repository.RouteSemanticFingerprint
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossOperatorDomainTest {
    @Test
    fun resolvesOperatorCodesWithoutTreatingUnknownAsKmb() {
        assertEquals(BusOperator.CTB, BusOperator.fromCode("CTB"))
        assertEquals(BusOperator.KMB, BusOperator.fromCode("kmb"))
        assertEquals(BusOperator.LWB, BusOperator.fromCode("LWB"))
        assertNull(BusOperator.fromCode("NLB"))
    }

    @Test
    fun rollsHongKongDataDayAt0515() {
        assertEquals("2026-08-16", HongKongDataDay.forInstant(millis("2026-08-17T05:14:59+08:00")))
        assertEquals("2026-08-17", HongKongDataDay.forInstant(millis("2026-08-17T05:15:00+08:00")))
    }

    @Test
    fun routeFingerprintIgnoresNamesAndInputOrderButIncludesOperatorAndCoordinates() {
        val original = variant(
            BusOperator.KMB,
            stops = listOf(
                stop("K1", 1, 22.2800, 114.1500, "舊名一"),
                stop("K2", 2, 22.2810, 114.1510, "舊名二")
            )
        )
        val renamedAndReordered = original.copy(
            stops = listOf(
                stop("K2", 2, 22.2810, 114.1510, "新名二"),
                stop("K1", 1, 22.2800, 114.1500, "新名一")
            )
        )

        assertEquals(
            RouteSemanticFingerprint.of(original),
            RouteSemanticFingerprint.of(renamedAndReordered)
        )
        assertNotEquals(
            RouteSemanticFingerprint.of(original),
            RouteSemanticFingerprint.of(original.copy(operator = BusOperator.LWB))
        )
        assertNotEquals(
            RouteSemanticFingerprint.of(original),
            RouteSemanticFingerprint.of(
                original.copy(stops = original.stops.mapIndexed { index, value ->
                    if (index == 0) value.copy(latitude = 22.2900) else value
                })
            )
        )
    }

    @Test
    fun dynamicProgrammingChoosesWholeRouteWinnerAndKeepsOnlyDiagonalPairs() {
        val ctb = variant(
            BusOperator.CTB,
            direction = "O",
            stops = listOf(
                stop("C1", 1, 22.2648838, 114.2415686),
                stop("C2", 2, 22.2700000, 114.2400000),
                stop("C3", 3, 22.2800000, 114.2300000)
            )
        )
        val wrongDirection = variant(
            BusOperator.KMB,
            direction = "O",
            stops = listOf(
                stop("W1", 1, 22.4000, 114.3000),
                stop("W2", 2, 22.4100, 114.3100)
            )
        )
        val winner = variant(
            BusOperator.KMB,
            direction = "I",
            stops = listOf(
                stop("K1", 1, 22.2647050, 114.2413840),
                stop("EXTRA", 2, 22.2670000, 114.2410000),
                stop("K2", 3, 22.2700200, 114.2400100),
                stop("K3", 4, 22.2800200, 114.2300100)
            )
        )

        val result = CrossOperatorRouteMatcher().match(ctb, listOf(wrongDirection, winner))

        assertEquals(CrossOperatorMatchStatus.MATCHED, result.status)
        assertEquals("I", result.winner?.direction)
        assertEquals(listOf("C1", "C2", "C3"), result.stopPairs.map { it.ctbStopId })
        assertEquals(listOf("K1", "K2", "K3"), result.stopPairs.map { it.operatorStopId })
        assertTrue(result.normalizedCost <= 46.0)
    }

    @Test
    fun matcherUsesStableServiceTypeTieBreakAndThresholdWithoutSecondPlaceDelta() {
        val ctb = variant(BusOperator.CTB, stops = listOf(stop("C", 1, 22.28, 114.15)))
        val serviceTwo = variant(
            BusOperator.KMB,
            serviceType = "2",
            stops = listOf(stop("K2", 1, 22.28, 114.15))
        )
        val serviceOne = variant(
            BusOperator.KMB,
            serviceType = "1",
            stops = listOf(stop("K1", 1, 22.28, 114.15))
        )

        val matched = CrossOperatorRouteMatcher().match(ctb, listOf(serviceTwo, serviceOne))
        assertEquals("1", matched.winner?.serviceType)

        val rejected = CrossOperatorRouteMatcher(thresholdMetersPerStop = 0.0).match(
            ctb,
            listOf(serviceOne.copy(stops = listOf(stop("K1", 1, 22.2801, 114.15))))
        )
        assertEquals(CrossOperatorMatchStatus.NO_MATCH, rejected.status)
    }

    @Test
    fun p2pGateRequiresBothUniqueMappingsInForwardOrder() {
        val ctb = variant(
            BusOperator.CTB,
            stops = listOf(stop("C1", 1, 22.28, 114.15), stop("C2", 2, 22.29, 114.16))
        )
        val kmb = variant(
            BusOperator.KMB,
            direction = "I",
            stops = listOf(stop("K1", 4, 22.28, 114.15), stop("K2", 5, 22.29, 114.16))
        )
        val match = CrossOperatorRouteMatcher().match(ctb, listOf(kmb))

        assertEquals(
            CrossOperatorEtaQuery(BusOperator.KMB, "118", "I", "1", "K1", "K2"),
            P2pCrossOperatorGate.resolve(match, "C1", "C2")
        )
        assertNull(P2pCrossOperatorGate.resolve(match, "C2", "C1"))
        assertNull(P2pCrossOperatorGate.resolve(match, "MISSING", "C2"))
    }

    private fun variant(
        operator: BusOperator,
        direction: String = "O",
        serviceType: String = "1",
        stops: List<StaticRouteStop>
    ) = StaticRouteVariant(operator, "118", direction, serviceType, stops)

    private fun stop(
        id: String,
        sequence: Int,
        latitude: Double,
        longitude: Double,
        name: String = id
    ) = StaticRouteStop(id, sequence, latitude, longitude, name)

    private fun millis(value: String): Long = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        Locale.US
    ).parse(value)!!.time
}

