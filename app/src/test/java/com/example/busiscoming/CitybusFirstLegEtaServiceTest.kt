package com.example.busiscoming

import com.example.busiscoming.data.model.FirstLegEtaQuery
import com.example.busiscoming.data.model.WaitTimeState
import com.example.busiscoming.data.repository.CitybusFirstLegEtaService
import com.example.busiscoming.data.repository.CitybusP2pStopMapResolver
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitybusFirstLegEtaServiceTest {
    private val query = FirstLegEtaQuery(
        company = "CTB",
        routeVariant = "8X-THR-1",
        route = "8X",
        boardingSeq = 6,
        alightingSeq = 31,
        bound = "O",
        directionPath = "outbound",
        rawInfo = "1|*|CTB||8X-THR-1||6||31||O|*|",
        lang = "0"
    )

    @Test
    fun buildsStopMapHistoricalRouteStopAndEtaUrls() {
        val stopMapResolver = CitybusP2pStopMapResolver()
        val service = etaService()

        val stopMapUrl = stopMapResolver.buildStopMapUrl(query.rawInfo, query.lang).toString()
        assertTrue(stopMapUrl.startsWith("https://mobile.citybus.com.hk/nwp3/showstops2.php?r="))
        assertTrue(stopMapUrl.contains("8X-THR-1"))
        assertEquals(
            "https://rt.data.gov.hk/v2/transport/citybus/route-stop/CTB/8X/outbound",
            service.buildHistoricalRouteStopUrl("CTB", "8X", "outbound").toString()
        )
        assertEquals(
            "https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/001227/8X",
            service.buildEtaUrl("CTB", "001227", "8X").toString()
        )
    }

    @Test
    fun resolvesWaitMinutesFromP2pStopMapAndMatchingEta() {
        val service = etaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            etaFetcher = { etaResponse("2026-06-04T12:03:10+08:00") }
        )

        val waitTimeState = service.resolveWaitTime(query)

        assertEquals(WaitTimeState.Available(4), waitTimeState)
    }

    @Test
    fun returnsZeroWhenEtaIsNowOrPast() {
        val service = etaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            etaFetcher = { etaResponse("2026-06-04T11:59:59+08:00") }
        )

        assertEquals(WaitTimeState.Available(0), service.resolveWaitTime(query))
    }

    @Test
    fun prefersStrictEtaMatchBeforeSeqFallback() {
        val service = etaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            etaFetcher = {
                """
                {
                  "data": [
                    {"co":"CTB","route":"8X","dir":"I","seq":6,"stop":"001227","eta":"2026-06-04T12:01:00+08:00"},
                    {"co":"CTB","route":"8X","dir":"O","seq":7,"stop":"001227","eta":"2026-06-04T12:02:00+08:00"},
                    {"co":"CTB","route":"8X","dir":"O","seq":6,"stop":"001227","eta":"2026-06-04T12:05:00+08:00"}
                  ]
                }
                """.trimIndent()
            }
        )

        assertEquals(WaitTimeState.Available(5), service.resolveWaitTime(query))
    }

    @Test
    fun fallsBackToRouteStopAndDirectionWhenEtaSeqDiffers() {
        val service = etaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            stopMapProvider = { stopMapResponse(stopId = "001312", routeVariant = "118-TOS-1", seq = 5) },
            etaFetcher = {
                """
                {
                  "data": [
                    {"co":"CTB","route":"118","dir":"O","seq":3,"stop":"001312","eta":"2026-06-04T12:03:10+08:00"}
                  ]
                }
                """.trimIndent()
            }
        )

        assertEquals(WaitTimeState.Available(4), service.resolveWaitTime(route118Query()))
    }

    @Test
    fun ignoresSeqFallbackRecordsWithDifferentRouteStopOrDirection() {
        val service = etaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            etaFetcher = {
                """
                {
                  "data": [
                    {"co":"CTB","route":"8","dir":"O","seq":7,"stop":"001227","eta":"2026-06-04T12:01:00+08:00"},
                    {"co":"CTB","route":"8X","dir":"I","seq":7,"stop":"001227","eta":"2026-06-04T12:02:00+08:00"},
                    {"co":"CTB","route":"8X","dir":"O","seq":7,"stop":"001228","eta":"2026-06-04T12:03:00+08:00"}
                  ]
                }
                """.trimIndent()
            }
        )

        assertEquals(WaitTimeState.Unavailable, service.resolveWaitTime(query))
    }

    @Test
    fun resolvesUpToThreeArrivalsSortedByEtaSequence() {
        val service = etaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            etaFetcher = {
                """
                {
                  "generated_timestamp":"2026-06-04T12:00:01+08:00",
                  "data": [
                    {"co":"CTB","route":"8X","dir":"O","seq":6,"stop":"001227","eta":"2026-06-04T12:08:00+08:00","eta_seq":2,"dest_tc":"筲箕灣"},
                    {"co":"CTB","route":"8X","dir":"O","seq":6,"stop":"001227","eta":"2026-06-04T12:04:00+08:00","eta_seq":1,"dest_tc":"筲箕灣","rmk_tc":"原定班次"},
                    {"co":"CTB","route":"8X","dir":"O","seq":6,"stop":"001227","eta":"2026-06-04T12:11:00+08:00","eta_seq":3,"dest_tc":"筲箕灣"},
                    {"co":"CTB","route":"8X","dir":"O","seq":6,"stop":"001227","eta":"2026-06-04T12:15:00+08:00","eta_seq":4,"dest_tc":"筲箕灣"}
                  ]
                }
                """.trimIndent()
            }
        )

        val waitTimeState = service.resolveWaitTime(query) as WaitTimeState.Available

        assertEquals(listOf(1, 2, 3), waitTimeState.arrivals.map { it.sequence })
        assertEquals(listOf(4, 8, 11), waitTimeState.arrivals.map { it.minutes })
        assertEquals("12:04", waitTimeState.arrivals.first().arrivalTimeText)
        assertEquals("筲箕灣", waitTimeState.arrivals.first().destination)
        assertEquals("原定班次", waitTimeState.arrivals.first().remark)
    }

    @Test
    fun returnsUnavailableWhenNoStrictOrFallbackEtaIsParsable() {
        val service = etaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            etaFetcher = {
                """
                {
                  "data": [
                    {"co":"CTB","route":"8X","dir":"O","seq":6,"stop":"001227","eta":""},
                    {"co":"CTB","route":"8X","dir":"O","seq":7,"stop":"001227","eta":"not-a-date"}
                  ]
                }
                """.trimIndent()
            }
        )

        assertEquals(WaitTimeState.Unavailable, service.resolveWaitTime(query))
    }

    @Test
    fun returnsUnavailableWhenStopMapOrEtaIsMissing() {
        val missingStopService = etaService(
            stopMapProvider = { stopMapResponse(stopId = "001999", seq = 7) },
            etaFetcher = { etaResponse("2026-06-04T12:03:10+08:00") }
        )
        val missingEtaService = etaService(
            etaFetcher = { """{"data":[]}""" }
        )

        assertEquals(WaitTimeState.Unavailable, missingStopService.resolveWaitTime(query))
        assertEquals(WaitTimeState.Unavailable, missingEtaService.resolveWaitTime(query))
    }

    @Test
    fun cachesP2pStopMapResultsForOneDay() {
        var stopMapCalls = 0
        var now = 1_000L
        val service = etaService(
            clock = { now },
            stopMapProvider = {
                stopMapCalls += 1
                stopMapResponse()
            },
            etaFetcher = { etaResponse("2026-06-04T12:03:10+08:00") }
        )

        service.resolveWaitTime(query)
        now += 1_000L
        service.resolveWaitTime(query)

        assertEquals(1, stopMapCalls)
    }

    @Test
    fun refreshesP2pStopMapCacheAfterOneDay() {
        var stopMapCalls = 0
        var now = 1_000L
        val service = etaService(
            clock = { now },
            stopMapProvider = {
                stopMapCalls += 1
                stopMapResponse()
            },
            etaFetcher = { etaResponse("2026-06-04T12:03:10+08:00") }
        )

        service.resolveWaitTime(query)
        now += 86_400_001L
        service.resolveWaitTime(query)

        assertEquals(2, stopMapCalls)
    }

    @Test
    fun returnsUnavailableWhenStopMapFetcherFailsWithoutCallingEta() {
        val service = etaService(
            stopMapProvider = { throw java.io.IOException("showstops2 failed") },
            etaFetcher = { error("not called") }
        )

        assertEquals(WaitTimeState.Unavailable, service.resolveWaitTime(query))
    }

    private fun etaService(
        clock: () -> Long = { 1_000L },
        stopMapProvider: () -> String = { stopMapResponse() },
        etaFetcher: (URL) -> String = { etaResponse("2026-06-04T12:03:10+08:00") }
    ): CitybusFirstLegEtaService {
        return CitybusFirstLegEtaService(
            clock = clock,
            etaFetcher = etaFetcher,
            stopMapResolver = CitybusP2pStopMapResolver(
                clock = clock,
                stopMapFetcher = { _, _ -> stopMapProvider() }
            )
        )
    }

    private fun stopMapResponse(
        stopId: String = "001227",
        routeVariant: String = "8X-THR-1",
        seq: Int = 6,
        name: String = "樂軒臺, 柴灣道"
    ): String {
        return """
            <iframe onload="addstoponmap('$stopId',114.24156861053,22.264883822091,'S','$seq','$seq - $name','$routeVariant','O','N',
            '114.24156861053','22.264883822091');"></iframe>
        """.trimIndent()
    }

    private fun etaResponse(eta: String): String {
        return """
            {
              "data": [
                {"co":"CTB","route":"8X","dir":"O","seq":6,"stop":"001227","eta":"$eta","eta_seq":1}
              ]
            }
        """.trimIndent()
    }

    private fun route118Query(): FirstLegEtaQuery {
        return FirstLegEtaQuery(
            company = "CTB",
            routeVariant = "118-TOS-1",
            route = "118",
            boardingSeq = 5,
            alightingSeq = 9,
            bound = "O",
            directionPath = "outbound",
            rawInfo = "1|*|CTB||118-TOS-1||5||9||O|*|",
            lang = "0"
        )
    }

    private fun millis(value: String): Long {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)!!.time
    }
}
