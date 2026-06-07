package com.example.busiscoming

import com.example.busiscoming.data.model.FirstLegEtaQuery
import com.example.busiscoming.data.model.WaitTimeState
import com.example.busiscoming.data.repository.CitybusFirstLegEtaService
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CitybusFirstLegEtaServiceTest {
    private val query = FirstLegEtaQuery(
        company = "CTB",
        routeVariant = "8X-THR-1",
        route = "8X",
        boardingSeq = 6,
        alightingSeq = 31,
        bound = "O",
        directionPath = "outbound"
    )

    @Test
    fun buildsRouteStopAndEtaUrls() {
        val service = CitybusFirstLegEtaService()

        assertEquals(
            "https://rt.data.gov.hk/v2/transport/citybus/route-stop/CTB/8X/outbound",
            service.buildRouteStopUrl("CTB", "8X", "outbound").toString()
        )
        assertEquals(
            "https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/001227/8X",
            service.buildEtaUrl("CTB", "001227", "8X").toString()
        )
    }

    @Test
    fun resolvesWaitMinutesFromMatchingEta() {
        val service = CitybusFirstLegEtaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            routeStopFetcher = { routeStopResponse() },
            etaFetcher = { etaResponse("2026-06-04T12:03:10+08:00") }
        )

        val waitTimeState = service.resolveWaitTime(query)

        assertEquals(WaitTimeState.Available(4), waitTimeState)
    }

    @Test
    fun returnsZeroWhenEtaIsNowOrPast() {
        val service = CitybusFirstLegEtaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            routeStopFetcher = { routeStopResponse() },
            etaFetcher = { etaResponse("2026-06-04T11:59:59+08:00") }
        )

        assertEquals(WaitTimeState.Available(0), service.resolveWaitTime(query))
    }

    @Test
    fun prefersStrictEtaMatchBeforeSeqFallback() {
        val service = CitybusFirstLegEtaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            routeStopFetcher = { routeStopResponse() },
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
        val service = CitybusFirstLegEtaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            routeStopFetcher = {
                """{"data":[{"co":"CTB","route":"118","dir":"O","seq":5,"stop":"001312"}]}"""
            },
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
        val service = CitybusFirstLegEtaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            routeStopFetcher = { routeStopResponse() },
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
    fun returnsUnavailableWhenNoStrictOrFallbackEtaIsParsable() {
        val service = CitybusFirstLegEtaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            routeStopFetcher = { routeStopResponse() },
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
    fun returnsUnavailableWhenStopOrEtaIsMissing() {
        val missingStopService = CitybusFirstLegEtaService(
            routeStopFetcher = { """{"data":[{"co":"CTB","route":"8X","dir":"O","seq":7,"stop":"001999"}]}""" },
            etaFetcher = { etaResponse("2026-06-04T12:03:10+08:00") }
        )
        val missingEtaService = CitybusFirstLegEtaService(
            routeStopFetcher = { routeStopResponse() },
            etaFetcher = { """{"data":[]}""" }
        )

        assertEquals(WaitTimeState.Unavailable, missingStopService.resolveWaitTime(query))
        assertEquals(WaitTimeState.Unavailable, missingEtaService.resolveWaitTime(query))
    }

    @Test
    fun cachesRouteStopResultsForOneDay() {
        var routeStopCalls = 0
        var now = 1_000L
        val service = CitybusFirstLegEtaService(
            clock = { now },
            routeStopFetcher = {
                routeStopCalls += 1
                routeStopResponse()
            },
            etaFetcher = { etaResponse("2026-06-04T12:03:10+08:00") }
        )

        service.resolveWaitTime(query)
        now += 1_000L
        service.resolveWaitTime(query)

        assertEquals(1, routeStopCalls)
    }

    @Test
    fun refreshesRouteStopCacheAfterOneDay() {
        var routeStopCalls = 0
        var now = 1_000L
        val service = CitybusFirstLegEtaService(
            clock = { now },
            routeStopFetcher = {
                routeStopCalls += 1
                routeStopResponse()
            },
            etaFetcher = { etaResponse("2026-06-04T12:03:10+08:00") }
        )

        service.resolveWaitTime(query)
        now += 86_400_001L
        service.resolveWaitTime(query)

        assertEquals(2, routeStopCalls)
    }

    @Test
    fun returnsUnavailableWhenFetcherFails() {
        val service = CitybusFirstLegEtaService(
            routeStopFetcher = { throw java.io.IOException("route-stop failed") },
            etaFetcher = { error("not called") }
        )

        assertEquals(WaitTimeState.Unavailable, service.resolveWaitTime(query))
    }

    private fun routeStopResponse(): String {
        return """{"data":[{"co":"CTB","route":"8X","dir":"O","seq":6,"stop":"001227"}]}"""
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
            directionPath = "outbound"
        )
    }

    private fun millis(value: String): Long {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)!!.time
    }
}
