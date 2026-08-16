package com.golink.busiscoming

import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.EtaUnavailableReason
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.CitybusFirstLegEtaService
import com.golink.busiscoming.data.repository.CitybusP2pStopMapResolver
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
    fun resolvesEnglishEtaWhenBoardingStopNameContainsEscapedApostrophe() {
        var requestedEtaUrl: URL? = null
        val service = etaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            stopMapProvider = {
                stopMapResponse(name = "Healthy Gardens, King\\'s Road")
            },
            etaFetcher = { url ->
                requestedEtaUrl = url
                etaResponse("2026-06-04T12:03:10+08:00")
            }
        )

        val result = service.resolveWaitTime(query.copy(lang = "1"))

        assertEquals(WaitTimeState.Available(4), result)
        assertEquals(
            "https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/001227/8X",
            requestedEtaUrl.toString()
        )
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

        assertEquals(WaitTimeState.NoArrivals, service.resolveWaitTime(query))
    }

    @Test
    fun resolvesAllArrivalsSortedByEtaSequence() {
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

        assertEquals(listOf(1, 2, 3, 4), waitTimeState.arrivals.map { it.sequence })
        assertEquals(listOf(4, 8, 11, 15), waitTimeState.arrivals.map { it.minutes })
        assertEquals("12:04", waitTimeState.arrivals.first().arrivalTimeText)
        assertEquals("筲箕灣", waitTimeState.arrivals.first().destination)
        assertEquals("原定班次", waitTimeState.arrivals.first().remark)
    }

    @Test
    fun selectsEtaDestinationAndRemarkByLanguageWithDocumentedOfficialFallback() {
        val response = """
            {
              "data": [
                {
                  "co":"CTB","route":"8X","dir":"O","seq":6,"stop":"001227",
                  "eta":"2026-06-04T12:04:00+08:00","eta_seq":1,
                  "dest_tc":"筲箕灣","dest_sc":"筲箕湾","dest_en":"Shau Kei Wan",
                  "rmk_tc":"原定班次","rmk_sc":"原定班次","rmk_en":"Scheduled departure"
                }
              ]
            }
        """.trimIndent()
        val service = etaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            etaFetcher = { response }
        )

        val traditional = service.resolveWaitTime(query.copy(lang = "0")) as WaitTimeState.Available
        val simplified = service.resolveWaitTime(query.copy(lang = "2")) as WaitTimeState.Available
        val english = service.resolveWaitTime(query.copy(lang = "1")) as WaitTimeState.Available

        assertEquals("筲箕灣", traditional.arrivals.single().destination)
        assertEquals("tc", traditional.arrivals.single().destinationLanguage)
        assertEquals("筲箕湾", simplified.arrivals.single().destination)
        assertEquals("sc", simplified.arrivals.single().destinationLanguage)
        assertEquals("Shau Kei Wan", english.arrivals.single().destination)
        assertEquals("en", english.arrivals.single().destinationLanguage)
        assertEquals("Scheduled departure", english.arrivals.single().remark)
        assertEquals("en", english.arrivals.single().remarkLanguage)
    }

    @Test
    fun fallsBackWithinOneOfficialEtaFieldWithoutTranslatingOrChangingTheQueryLanguage() {
        val service = etaService(
            clock = { millis("2026-06-04T12:00:00+08:00") },
            etaFetcher = {
                """
                {"data":[{
                  "co":"CTB","route":"8X","dir":"O","seq":6,"stop":"001227",
                  "eta":"2026-06-04T12:04:00+08:00","eta_seq":1,
                  "dest_tc":"筲箕灣","dest_sc":"","dest_en":""
                }]}
                """.trimIndent()
            }
        )

        val arrival = (service.resolveWaitTime(query.copy(lang = "1")) as WaitTimeState.Available)
            .arrivals.single()

        assertEquals("筲箕灣", arrival.destination)
        assertEquals("tc", arrival.destinationLanguage)
    }

    @Test
    fun returnsNoArrivalsWhenNoStrictOrFallbackEtaIsParsable() {
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

        assertEquals(WaitTimeState.NoArrivals, service.resolveWaitTime(query))
    }

    @Test
    fun distinguishesMissingBoardingStopFromValidEmptyEta() {
        val missingStopService = etaService(
            stopMapProvider = { stopMapResponse(stopId = "001999", seq = 7) },
            etaFetcher = { etaResponse("2026-06-04T12:03:10+08:00") }
        )
        val missingEtaService = etaService(
            etaFetcher = { """{"data":[]}""" }
        )

        assertEquals(
            WaitTimeState.Unavailable(EtaUnavailableReason.BOARDING_STOP_NOT_FOUND),
            missingStopService.resolveWaitTime(query)
        )
        assertEquals(WaitTimeState.NoArrivals, missingEtaService.resolveWaitTime(query))
    }

    @Test
    fun distinguishesStopMapRequestAndResponseFailures() {
        val requestFailure = etaService(
            stopMapProvider = { throw java.io.IOException("showstops2 failed") },
            etaFetcher = { error("not called") }
        )
        val invalidResponse = etaService(
            stopMapProvider = { "<html>missing stop map calls</html>" },
            etaFetcher = { error("not called") }
        )

        assertEquals(
            WaitTimeState.Unavailable(EtaUnavailableReason.STOP_MAP_REQUEST_FAILED),
            requestFailure.resolveWaitTime(query)
        )
        assertEquals(
            WaitTimeState.Unavailable(EtaUnavailableReason.STOP_MAP_RESPONSE_INVALID),
            invalidResponse.resolveWaitTime(query)
        )
    }

    @Test
    fun distinguishesEtaRequestAndResponseFailures() {
        val requestFailure = etaService(
            etaFetcher = { throw java.io.IOException("ETA failed") }
        )
        val invalidResponse = etaService(
            etaFetcher = { "<html>upstream failure</html>" }
        )

        assertEquals(
            WaitTimeState.Unavailable(EtaUnavailableReason.ETA_REQUEST_FAILED),
            requestFailure.resolveWaitTime(query)
        )
        assertEquals(
            WaitTimeState.Unavailable(EtaUnavailableReason.ETA_RESPONSE_INVALID),
            invalidResponse.resolveWaitTime(query)
        )
    }

    @Test
    fun reportsMissingFirstLegDataWithoutMakingNetworkRequests() {
        val service = etaService(
            stopMapProvider = { error("not called") },
            etaFetcher = { error("not called") }
        )

        assertEquals(
            WaitTimeState.Unavailable(EtaUnavailableReason.MISSING_FIRST_LEG_DATA),
            service.resolveWaitTime(query.copy(rawInfo = ""))
        )
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
