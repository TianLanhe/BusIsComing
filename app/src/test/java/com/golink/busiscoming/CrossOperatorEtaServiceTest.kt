package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.CrossOperatorEtaQuery
import com.golink.busiscoming.data.model.CrossOperatorMatchStatus
import com.golink.busiscoming.data.model.CrossOperatorRouteMatch
import com.golink.busiscoming.data.model.EtaArrival
import com.golink.busiscoming.data.model.EtaUnavailableReason
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.StaticRouteVariant
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.CrossOperatorEtaMerger
import com.golink.busiscoming.data.repository.CrossOperatorFirstLegEtaService
import com.golink.busiscoming.data.repository.CrossOperatorMappingResolution
import com.golink.busiscoming.data.repository.EtaSourceResult
import com.golink.busiscoming.data.repository.FirstLegStopIdentity
import com.golink.busiscoming.data.repository.KmbFirstLegEtaSource
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossOperatorEtaServiceTest {
    @Test
    fun kmbEndpointKeepsLwbIdentityAndRejectsMismatchedOperatorAndDirection() {
        var requested: URL? = null
        val source = KmbFirstLegEtaSource(
            clock = { millis("2026-08-17T12:00:00+08:00") },
            fetcher = { url -> requested = url; fixture("kmb_eta.json") }
        )
        val query = CrossOperatorEtaQuery(
            BusOperator.LWB,
            "S1",
            "O",
            "1",
            "S1FIRSTSTOP00000",
            "S1LASTSTOP000000"
        )

        val result = source.query(query, "0") as EtaSourceResult.Success

        assertEquals(
            "https://data.etabus.gov.hk/v1/transport/kmb/eta/S1FIRSTSTOP00000/S1/1",
            requested.toString()
        )
        assertEquals(1, result.arrivals.size)
        assertEquals(BusOperator.LWB, result.arrivals.single().operator)
        assertEquals("機場", result.arrivals.single().destination)
        assertEquals(1, result.arrivals.single().sourceSequence)
    }

    @Test
    fun mergerKeepsAllArrivalsIncludingExactCrossOperatorTieAndReindexes() {
        val time = millis("2026-08-17T12:05:00+08:00")
        val citybus = WaitTimeState.Available(
            listOf(
                arrival(BusOperator.CTB, 1, time, 5),
                arrival(BusOperator.CTB, 2, time + 120_000, 7),
                arrival(BusOperator.CTB, 3, time + 240_000, 9),
                arrival(BusOperator.CTB, 4, time + 360_000, 11)
            )
        )
        val kmb = EtaSourceResult.Success(
            listOf(
                arrival(BusOperator.KMB, 1, time, 5),
                arrival(BusOperator.KMB, 2, time + 60_000, 6)
            )
        )

        val merged = CrossOperatorEtaMerger.merge(citybus, kmb) as WaitTimeState.Available

        assertEquals(6, merged.arrivals.size)
        assertEquals((1..6).toList(), merged.arrivals.map { it.sequence })
        assertEquals(
            listOf(BusOperator.CTB, BusOperator.KMB, BusOperator.KMB),
            merged.arrivals.take(3).map { it.operator }
        )
        assertEquals(2, merged.arrivals.count { it.etaMillis == time })
    }

    @Test
    fun mergerDistinguishesSuccessfulEmptyFromTechnicalFailure() {
        assertEquals(
            WaitTimeState.NoArrivals,
            CrossOperatorEtaMerger.merge(WaitTimeState.NoArrivals, EtaSourceResult.Success(emptyList()))
        )
        assertTrue(
            CrossOperatorEtaMerger.merge(
                WaitTimeState.NoArrivals,
                EtaSourceResult.Failure(EtaUnavailableReason.ETA_REQUEST_FAILED)
            ) is WaitTimeState.Unavailable
        )
        val oneSide = CrossOperatorEtaMerger.merge(
            WaitTimeState.Available(listOf(arrival(BusOperator.CTB, 1, 1_000L, 1))),
            EtaSourceResult.Failure(EtaUnavailableReason.ETA_REQUEST_FAILED)
        ) as WaitTimeState.Available
        assertEquals(BusOperator.CTB, oneSide.arrivals.single().operator)
    }

    @Test
    fun progressiveServiceEmitsCitybusBeforeMappedPartnerResult() {
        val emitted = mutableListOf<WaitTimeState>()
        val match = CrossOperatorRouteMatch(
            CrossOperatorMatchStatus.MATCHED,
            StaticRouteVariant(BusOperator.KMB, "118", "I", "1", emptyList()),
            1.0,
            1.0,
            emptyList(),
            1,
            100.0,
            46.0
        )
        val service = CrossOperatorFirstLegEtaService(
            citybusResolver = {
                WaitTimeState.Available(listOf(arrival(BusOperator.CTB, 1, 2_000L, 2)))
            },
            stopIdentityResolver = { FirstLegStopIdentity("C1", "C2") },
            mappingResolver = { _, _, _ ->
                CrossOperatorMappingResolution.Enabled(
                    CrossOperatorEtaQuery(BusOperator.KMB, "118", "I", "1", "K1", "K2"),
                    match
                )
            },
            partnerSource = { _, _ ->
                EtaSourceResult.Success(listOf(arrival(BusOperator.KMB, 1, 1_000L, 1)))
            }
        )

        val final = service.resolveWaitTimeProgressively(query(), emitted::add)

        assertEquals(2, emitted.size)
        assertEquals(BusOperator.CTB, (emitted.first() as WaitTimeState.Available).arrivals.single().operator)
        assertEquals(BusOperator.KMB, (final as WaitTimeState.Available).arrivals.first().operator)
        assertEquals(final, emitted.last())
    }

    private fun arrival(
        operator: BusOperator,
        sourceSequence: Int,
        etaMillis: Long,
        minutes: Int
    ) = EtaArrival(
        sequence = sourceSequence,
        minutes = minutes,
        etaMillis = etaMillis,
        arrivalTimeText = "12:05",
        operator = operator,
        sourceSequence = sourceSequence
    )

    private fun query() = FirstLegEtaQuery(
        "CTB",
        "118-TOS-1",
        "118",
        1,
        2,
        "O",
        "outbound",
        "raw",
        "0"
    )

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("cross_operator/$name")).readText()

    private fun millis(value: String): Long =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)!!.time
}

