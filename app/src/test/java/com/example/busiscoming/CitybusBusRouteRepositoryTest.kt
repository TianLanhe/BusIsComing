package com.example.busiscoming

import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.model.WaitTimeState
import com.example.busiscoming.data.repository.BusRouteQueryCallback
import com.example.busiscoming.data.repository.CitybusBusRouteRepository
import com.example.busiscoming.data.repository.CitybusRouteStopResolver
import com.example.busiscoming.data.repository.CitybusStopNameResolver
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.RouteCardStopPreview
import java.io.IOException
import java.net.URL
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import com.example.busiscoming.data.repository.RouteCardStopPreviewResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CitybusBusRouteRepositoryTest {
    private val repository = CitybusBusRouteRepository(clock = { 0L })
    private val origin = Place("起點", 22.267079693838, 114.24208950984)
    private val destination = Place("終點", 22.282043425996, 114.15760138031)

    @Test
    fun formatsQueryTimeUsingHongKongTime() {
        assertEquals("1970-01-01 08:00", repository.formatQueryTime(0L))
    }

    @Test
    fun buildsRouteUrlWithCoordinatesTimeAndFixedParameters() {
        val url = repository.buildRouteUrl(origin, destination, "2026-06-03 12:41").toString()

        assertEquals(
            "https://mobile.citybus.com.hk/nwp3/ppsearch_p3.php" +
                "?slat=22.267079693838" +
                "&slon=114.24208950984" +
                "&elat=22.282043425996" +
                "&elon=114.15760138031" +
                "&t=2026-06-03%2012%3A41" +
                "&ws=1.3" +
                "&leg=2" +
                "&m1=T" +
                "&l=0",
            url
        )
        assertFalse(url.contains("loc="))
        assertFalse(url.contains("ssid="))
        assertFalse(url.contains("sysid="))
    }

    @Test
    fun buildsRouteUrlForDifferentSearchModes() {
        val urls = listOf("T", "F", "W").map { mode ->
            repository.buildRouteUrl(origin, destination, "2026-06-03 12:41", mode).toString()
        }

        assertTrue(urls[0].contains("&m1=T&"))
        assertTrue(urls[1].contains("&m1=F&"))
        assertTrue(urls[2].contains("&m1=W&"))
        urls.forEach { url ->
            assertTrue(url.contains("&t=2026-06-03%2012%3A41&"))
            assertTrue(url.contains("&ws=1.3&"))
            assertFalse(url.contains("loc="))
            assertFalse(url.contains("ssid="))
            assertFalse(url.contains("sysid="))
        }
    }

    @Test
    fun exposesRequiredRequestHeaders() {
        val headers = repository.requestHeaders()

        assertEquals("*/*", headers["Accept"])
        assertEquals("zh-CN,zh;q=0.9", headers["Accept-Language"])
        assertEquals("keep-alive", headers["Connection"])
        assertEquals("https://mobile.citybus.com.hk/nwp3/", headers["Referer"])
        assertEquals("empty", headers["Sec-Fetch-Dest"])
        assertEquals("cors", headers["Sec-Fetch-Mode"])
        assertEquals("same-origin", headers["Sec-Fetch-Site"])
        assertTrue(headers["User-Agent"].orEmpty().contains("Chrome/148.0.0.0"))
        assertTrue(headers["Cookie"].orEmpty().contains("ETWEBID=6a1ecbeae8d60"))
    }

    @Test
    fun logsEquivalentCurlForEachModeRequest() {
        val logs = Collections.synchronizedList(mutableListOf<String>())
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { _, _ ->
                routeHtml("788 港元8.7預計29分鐘 步行距離(約)350米")
            },
            requestLogger = { logs.add(it) }
        )

        repository.searchRoutes(origin, destination)

        assertEquals(3, logs.size)
        assertEquals(setOf("T", "F", "W"), logs.map { it.queryParamFromCurl("m1") }.toSet())
        logs.forEach { curl ->
            assertTrue(curl.startsWith("curl 'https://mobile.citybus.com.hk/nwp3/ppsearch_p3.php?"))
            assertTrue(curl.contains("&ws=1.3&"))
            assertTrue(curl.contains(" -H 'Cookie: ETWEBID=6a1ecbeae8d60;"))
            assertTrue(curl.contains(" -H 'User-Agent: Mozilla/5.0"))
        }
    }

    @Test
    fun searchesAllModesWithSameQueryTimeAndAggregatesUniqueResults() {
        val requestedUrls = Collections.synchronizedList(mutableListOf<URL>())
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                requestedUrls += url
                when (url.queryParam("m1")) {
                    "T" -> routeHtml(*TIME_MODE_LABELS)
                    "F" -> routeHtml(*FARE_MODE_LABELS)
                    "W" -> routeHtml(*WALKING_MODE_LABELS)
                    else -> error("Unexpected m1")
                }
            },
            requestLogger = {}
        )

        val routes = repository.searchRoutes(origin, destination)

        assertEquals(setOf("T", "F", "W"), requestedUrls.map { it.queryParam("m1") }.toSet())
        assertEquals(1, requestedUrls.map { it.queryParam("t") }.distinct().size)
        assertEquals(setOf("1.3"), requestedUrls.map { it.queryParam("ws") }.toSet())
        assertEquals(16, routes.size)
        assertEquals(routes.map { it.durationMinutes }.sorted(), routes.map { it.durationMinutes })
        assertEquals(1, routes.count { it.routeName == "788" })
        assertEquals(1, routes.count { it.routeName == "780" })
        assertTrue(
            routes.map { it.routeName }.containsAll(
                listOf(
                    "789 \u2192 619",
                    "789 \u2192 15",
                    "8X \u2192 10",
                    "8X \u2192 1",
                    "8 \u2192 90",
                    "8 \u2192 E11A",
                    "8 \u2192 E11B"
                )
            )
        )

        val fastestRoute = routes.first { it.routeName == "789 \u2192 619" }
        assertEquals(16.4, fastestRoute.priceHkd, 0.001)
        assertEquals(28, fastestRoute.durationMinutes)
        assertEquals(363, fastestRoute.walkingDistanceMeters)

        val freeTransferRoute = routes.first { it.routeName == "8X \u2192 10" }
        assertEquals(8.1, freeTransferRoute.priceHkd, 0.001)
        assertEquals(74, freeTransferRoute.durationMinutes)
        assertEquals(355, freeTransferRoute.walkingDistanceMeters)

        val shortWalkRoute = routes.first { it.routeName == "8 \u2192 90" }
        assertEquals(12.9, shortWalkRoute.priceHkd, 0.001)
        assertEquals(95, shortWalkRoute.walkingDistanceMeters)
    }

    @Test
    fun keepsSameRouteSegmentsWhenDurationOrWalkingDistanceDiffers() {
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                when (url.queryParam("m1")) {
                    "T" -> routeHtml("8X 港元8.1 至 1 免費 *預計74分鐘 步行距離(約)355米")
                    "F" -> routeHtml("8X 港元8.1 至 1 免費 *預計104分鐘 步行距離(約)450米")
                    "W" -> routeHtml("8X 港元8.1 至 1 免費 *預計74分鐘 步行距離(約)355米")
                    else -> error("Unexpected m1")
                }
            },
            requestLogger = {}
        )

        val routes = repository.searchRoutes(origin, destination)

        assertEquals(2, routes.size)
        assertEquals(listOf(74, 104), routes.map { it.durationMinutes })
        assertEquals(listOf(355, 450), routes.map { it.walkingDistanceMeters })
    }

    @Test
    fun keepsRoutesWithDifferentRawInfoAsSeparateVisibleResults() {
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                if (url.queryParam("m1") == "T") {
                    routeHtmlWithInfo(
                        routeWithInfo("1 港元1.0預計10分鐘 步行距離(約)100米", "1", 1),
                        routeWithInfo("1 港元1.0預計10分鐘 步行距離(約)100米", "1", 2)
                    )
                } else {
                    routeHtml()
                }
            },
            requestLogger = {}
        )

        val routes = repository.searchRoutes(origin, destination)

        assertEquals(2, routes.size)
        assertEquals(2, routes.map { it.routeDetailQuery?.rawInfo }.distinct().size)
        assertEquals(2, routes.map { it.resultId }.distinct().size)
    }

    @Test
    fun returnsSuccessfulModeResultsWhenSomeModesFail() {
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                if (url.queryParam("m1") == "W") {
                    routeHtml("788 港元8.7預計29分鐘 步行距離(約)350米")
                } else {
                    throw IOException("mode failed")
                }
            },
            requestLogger = {}
        )

        val routes = repository.searchRoutes(origin, destination)

        assertEquals(listOf("788"), routes.map { it.routeName })
    }

    @Test
    fun returnsOtherModeResultsWhenOneModeHasNoValidCandidates() {
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                when (url.queryParam("m1")) {
                    "T" -> routeHtml()
                    "F" -> routeHtml("8X 港元8.1 至 10 免費 *預計74分鐘 步行距離(約)355米")
                    "W" -> routeHtml("788 港元8.7預計29分鐘 步行距離(約)350米")
                    else -> error("Unexpected m1")
                }
            },
            requestLogger = {}
        )

        val routes = repository.searchRoutes(origin, destination)

        assertEquals(listOf("788", "8X \u2192 10"), routes.map { it.routeName })
    }

    @Test
    fun returnsEmptyWhenAllModesHaveNoValidCandidates() {
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { _, _ -> routeHtml() },
            requestLogger = {}
        )

        val routes = repository.searchRoutes(origin, destination)

        assertEquals(emptyList<Any>(), routes)
    }

    @Test
    fun throwsWhenAllModesFail() {
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { _, _ -> throw IOException("mode failed") },
            requestLogger = {}
        )

        assertThrows(IOException::class.java) {
            repository.searchRoutes(origin, destination)
        }
    }

    @Test
    fun progressiveQueryEmitsInitialRoutesImmediatelyAndUpdatesEtaInBackground() {
        val callback = RecordingCallback(expectedUpdates = 6)
        val waitTimeCalls = AtomicInteger(0)
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                if (url.queryParam("m1") == "T") {
                    routeHtmlWithInfo(
                        routeWithInfo("1 港元1.0預計1分鐘 步行距離(約)101米", "1", 1),
                        routeWithInfo("2 港元1.0預計2分鐘 步行距離(約)102米", "2", 2),
                        routeWithInfo("3 港元1.0預計3分鐘 步行距離(約)103米", "3", 3),
                        routeWithInfo("4 港元1.0預計4分鐘 步行距離(約)104米", "4", 4),
                        routeWithInfo("5 港元1.0預計5分鐘 步行距離(約)105米", "5", 5),
                        routeWithInfo("6 港元1.0預計6分鐘 步行距離(約)106米", "6", 6)
                    )
                } else {
                    routeHtml()
                }
            },
            requestLogger = {},
            waitTimeResolver = {
                waitTimeCalls.incrementAndGet()
                WaitTimeState.Available(it.boardingSeq)
            },
            etaWorkerCount = 2
        )

        repository.searchRoutesProgressively(origin, destination, callback)

        assertEquals(1, callback.initialRoutes.size)
        val initialRoutes = callback.awaitInitialRoutes()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), initialRoutes.map { it.durationMinutes })
        assertEquals(List(6) { WaitTimeState.Loading }, initialRoutes.map { it.waitTimeState })

        val updates = callback.awaitUpdateCount(6)
        assertEquals(6, waitTimeCalls.get())
        assertEquals(
            setOf(
                WaitTimeState.Available(1),
                WaitTimeState.Available(2),
                WaitTimeState.Available(3),
                WaitTimeState.Available(4),
                WaitTimeState.Available(5),
                WaitTimeState.Available(6)
            ),
            updates.map { it.second }.toSet()
        )
    }

    @Test
    fun progressiveQueryFansOutDuplicateFirstLegEta() {
        val waitTimeCalls = AtomicInteger(0)
        val callback = RecordingCallback(expectedUpdates = 2)
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                if (url.queryParam("m1") == "F") {
                    routeHtmlWithInfo(
                        routeWithInfo(
                            "8X 港元8.1 至 1 免費 *預計74分鐘 步行距離(約)450米",
                            "8X",
                            6,
                            alightingSeq = 31
                        ),
                        routeWithInfo(
                            "8X 港元8.1 至 10 免費 *預計73分鐘 步行距離(約)355米",
                            "8X",
                            6,
                            alightingSeq = 23
                        )
                    )
                } else {
                    routeHtml()
                }
            },
            requestLogger = {},
            waitTimeResolver = {
                waitTimeCalls.incrementAndGet()
                WaitTimeState.Available(4)
            }
        )

        repository.searchRoutesProgressively(origin, destination, callback)

        assertEquals(
            listOf(WaitTimeState.Loading, WaitTimeState.Loading),
            callback.awaitInitialRoutes().map { it.waitTimeState }
        )
        val updates = callback.awaitUpdateCount(2)
        assertEquals(1, waitTimeCalls.get())
        assertEquals(
            listOf(WaitTimeState.Available(4), WaitTimeState.Available(4)),
            updates.map { it.second }
        )
    }

    @Test
    fun progressiveQueryDoesNotWaitForDelayedEtaBeforeInitialRoutes() {
        val callback = RecordingCallback(expectedUpdates = 1)
        val etaStarted = CountDownLatch(1)
        val releaseEta = CountDownLatch(1)
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                if (url.queryParam("m1") == "T") {
                    routeHtmlWithInfo(routeWithInfo("1 港元1.0預計1分鐘 步行距離(約)101米", "1", 1))
                } else {
                    routeHtml()
                }
            },
            requestLogger = {},
            waitTimeResolver = {
                etaStarted.countDown()
                releaseEta.await(1, TimeUnit.SECONDS)
                WaitTimeState.Available(1)
            }
        )

        repository.searchRoutesProgressively(origin, destination, callback)

        assertEquals(listOf(WaitTimeState.Loading), callback.awaitInitialRoutes(200).map { it.waitTimeState })
        assertTrue("ETA resolver was not started", etaStarted.await(1, TimeUnit.SECONDS))
        callback.assertNoUpdatesFor(100)

        releaseEta.countDown()

        assertEquals(listOf(WaitTimeState.Available(1)), callback.awaitUpdateCount(1).map { it.second })
    }

    @Test
    fun progressiveQueryEmitsEtaUpdatesByCompletionOrder() {
        val callback = RecordingCallback(expectedUpdates = 2)
        val slowEtaStarted = CountDownLatch(1)
        val releaseSlowEta = CountDownLatch(1)
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                if (url.queryParam("m1") == "T") {
                    routeHtmlWithInfo(
                        routeWithInfo("1 港元1.0預計1分鐘 步行距離(約)101米", "1", 1),
                        routeWithInfo("2 港元1.0預計2分鐘 步行距離(約)102米", "2", 2)
                    )
                } else {
                    routeHtml()
                }
            },
            requestLogger = {},
            waitTimeResolver = {
                if (it.boardingSeq == 1) {
                    slowEtaStarted.countDown()
                    releaseSlowEta.await(1, TimeUnit.SECONDS)
                }
                WaitTimeState.Available(it.boardingSeq)
            },
            etaWorkerCount = 2
        )

        repository.searchRoutesProgressively(origin, destination, callback)

        assertEquals(
            listOf(WaitTimeState.Loading, WaitTimeState.Loading),
            callback.awaitInitialRoutes().map { it.waitTimeState }
        )
        assertTrue("Slow ETA resolver was not started", slowEtaStarted.await(1, TimeUnit.SECONDS))
        assertEquals(
            listOf(WaitTimeState.Available(2)),
            callback.awaitUpdateCount(1).map { it.second }
        )

        releaseSlowEta.countDown()

        assertEquals(
            listOf(WaitTimeState.Available(2), WaitTimeState.Available(1)),
            callback.awaitUpdateCount(2).map { it.second }
        )
    }

    @Test
    fun secondProgressiveQueryStartsWhilePreviousEtaIsBlocked() {
        val firstCallback = RecordingCallback()
        val secondCallback = RecordingCallback(expectedUpdates = 1)
        val firstEtaStarted = CountDownLatch(1)
        val releaseFirstEta = CountDownLatch(1)
        val secondDestination = Place("終點2", 22.0, 114.0)
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                if (url.queryParam("m1") != "T") {
                    routeHtml()
                } else if (url.queryParam("elat") == destination.latitude.toString()) {
                    routeHtmlWithInfo(routeWithInfo("1 港元1.0預計1分鐘 步行距離(約)101米", "1", 1))
                } else {
                    routeHtmlWithInfo(routeWithInfo("2 港元1.0預計2分鐘 步行距離(約)102米", "2", 2))
                }
            },
            requestLogger = {},
            waitTimeResolver = {
                if (it.boardingSeq == 1) {
                    firstEtaStarted.countDown()
                    releaseFirstEta.await(2, TimeUnit.SECONDS)
                }
                WaitTimeState.Available(it.boardingSeq)
            },
            etaWorkerCount = 1
        )

        repository.searchRoutesProgressively(origin, destination, firstCallback)

        assertEquals(listOf(WaitTimeState.Loading), firstCallback.awaitInitialRoutes().map { it.waitTimeState })
        assertTrue("First ETA resolver was not started", firstEtaStarted.await(1, TimeUnit.SECONDS))

        repository.searchRoutesProgressively(origin, secondDestination, secondCallback)

        assertEquals(listOf(WaitTimeState.Loading), secondCallback.awaitInitialRoutes(300).map { it.waitTimeState })
        assertEquals(listOf(WaitTimeState.Available(2)), secondCallback.awaitUpdateCount(1).map { it.second })

        releaseFirstEta.countDown()
        firstCallback.assertNoUpdatesFor(100)
    }

    @Test
    fun cancelProgressiveQueriesSuppressesOldEtaUpdates() {
        val callback = RecordingCallback()
        val etaStarted = CountDownLatch(1)
        val releaseEta = CountDownLatch(1)
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                if (url.queryParam("m1") == "T") {
                    routeHtmlWithInfo(routeWithInfo("1 港元1.0預計1分鐘 步行距離(約)101米", "1", 1))
                } else {
                    routeHtml()
                }
            },
            requestLogger = {},
            waitTimeResolver = {
                etaStarted.countDown()
                releaseEta.await(2, TimeUnit.SECONDS)
                WaitTimeState.Available(1)
            }
        )

        repository.searchRoutesProgressively(origin, destination, callback)

        assertEquals(listOf(WaitTimeState.Loading), callback.awaitInitialRoutes().map { it.waitTimeState })
        assertTrue("ETA resolver was not started", etaStarted.await(1, TimeUnit.SECONDS))

        repository.cancelProgressiveQueries()
        releaseEta.countDown()

        callback.assertNoUpdatesFor(100)
    }

    @Test
    fun progressiveQueryIsolatesEtaFailuresToIndividualRoutes() {
        val callback = RecordingCallback(expectedUpdates = 2)
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                if (url.queryParam("m1") == "T") {
                    routeHtmlWithInfo(
                        routeWithInfo("1 港元1.0預計1分鐘 步行距離(約)101米", "1", 1),
                        routeWithInfo("2 港元1.0預計2分鐘 步行距離(約)102米", "2", 2)
                    )
                } else {
                    routeHtml()
                }
            },
            requestLogger = {},
            waitTimeResolver = {
                if (it.boardingSeq == 1) throw IOException("ETA failed")
                WaitTimeState.Available(2)
            }
        )

        repository.searchRoutesProgressively(origin, destination, callback)

        assertEquals(
            listOf(WaitTimeState.Loading, WaitTimeState.Loading),
            callback.awaitInitialRoutes().map { it.waitTimeState }
        )
        assertEquals(
            listOf(WaitTimeState.Unavailable, WaitTimeState.Available(2)),
            callback.awaitUpdateCount(2).map { it.second }
        )
    }

    @Test
    fun progressiveQueryEmitsInitialRoutesBeforeStopPreviewCompletes() {
        val callback = RecordingCallback(expectedPreviewUpdates = 1)
        val previewStarted = CountDownLatch(1)
        val releasePreview = CountDownLatch(1)
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                if (url.queryParam("m1") == "T") {
                    routeHtmlWithInfo(routeWithInfo("1 港元1.0預計10分鐘 步行距離(約)100米", "1", 1))
                } else {
                    routeHtml()
                }
            },
            requestLogger = {},
            waitTimeResolver = { WaitTimeState.Unavailable },
            stopPreviewResolver = previewResolver(
                routeStopFetcher = {
                    previewStarted.countDown()
                    releasePreview.await(1, TimeUnit.SECONDS)
                    """{"data":[{"co":"CTB","route":"1","dir":"O","seq":1,"stop":"001"},{"co":"CTB","route":"1","dir":"O","seq":10,"stop":"010"}]}"""
                }
            ),
            stopPreviewWorkerCount = 1
        )

        repository.searchRoutesProgressively(origin, destination, callback)

        assertEquals(listOf("1"), callback.awaitInitialRoutes(200).map { it.routeName })
        assertTrue("Stop preview resolver was not started", previewStarted.await(1, TimeUnit.SECONDS))
        callback.assertNoPreviewUpdatesFor(100)

        releasePreview.countDown()

        assertEquals(
            listOf("上車 起點站  \u2192  下車 終點站"),
            callback.awaitPreviewCount(1).map { it.second.displayText() }
        )
    }

    @Test
    fun cancelProgressiveQueriesSuppressesOldStopPreviewUpdates() {
        val callback = RecordingCallback(expectedPreviewUpdates = 0)
        val previewStarted = CountDownLatch(1)
        val releasePreview = CountDownLatch(1)
        val repository = CitybusBusRouteRepository(
            clock = { fixedQueryTimestamp },
            routeFetcher = { url, _ ->
                if (url.queryParam("m1") == "T") {
                    routeHtmlWithInfo(routeWithInfo("1 港元1.0預計10分鐘 步行距離(約)100米", "1", 1))
                } else {
                    routeHtml()
                }
            },
            requestLogger = {},
            waitTimeResolver = { WaitTimeState.Unavailable },
            stopPreviewResolver = previewResolver(
                routeStopFetcher = {
                    previewStarted.countDown()
                    releasePreview.await(1, TimeUnit.SECONDS)
                    """{"data":[{"co":"CTB","route":"1","dir":"O","seq":1,"stop":"001"},{"co":"CTB","route":"1","dir":"O","seq":10,"stop":"010"}]}"""
                }
            ),
            stopPreviewWorkerCount = 1
        )

        repository.searchRoutesProgressively(origin, destination, callback)
        assertEquals(listOf("1"), callback.awaitInitialRoutes().map { it.routeName })
        assertTrue("Stop preview resolver was not started", previewStarted.await(1, TimeUnit.SECONDS))

        repository.cancelProgressiveQueries()
        releasePreview.countDown()

        callback.assertNoPreviewUpdatesFor(150)
    }

    private fun routeHtml(vararg labels: String): String {
        return labels.joinToString(
            separator = "\n",
            prefix = "<div id=\"routelist2\">\n",
            postfix = "\n</div>"
        ) { label -> "<table aria-label=\"$label\"></table>" }
    }

    private fun routeHtmlWithInfo(vararg rows: Pair<String, String>): String {
        return rows.joinToString(
            separator = "\n",
            prefix = "<div id=\"routelist2\">\n",
            postfix = "\n</div>"
        ) { (label, info) -> "<table aria-label=\"$label\" onclick=\"showroutep2p('$info','0','12:00|*|30');\"></table>" }
    }

    private fun routeWithInfo(
        label: String,
        route: String,
        boardingSeq: Int,
        alightingSeq: Int = 10
    ): Pair<String, String> {
        return label to "1|*|CTB||$route-TEST-1||$boardingSeq||$alightingSeq||O|*|"
    }

    private fun previewResolver(
        routeStopFetcher: (URL) -> String
    ): RouteCardStopPreviewResolver {
        return RouteCardStopPreviewResolver(
            routeStopResolver = CitybusRouteStopResolver(routeStopFetcher = routeStopFetcher),
            stopNameResolver = CitybusStopNameResolver(
                stopFetcher = { url ->
                    when {
                        url.toString().endsWith("/001") -> {
                            """{"data":{"stop":"001","name_tc":"起點站"}}"""
                        }
                        url.toString().endsWith("/010") -> {
                            """{"data":{"stop":"010","name_tc":"終點站"}}"""
                        }
                        else -> """{"data":{}}"""
                    }
                }
            )
        )
    }

    private fun URL.queryParam(name: String): String {
        return query.split("&")
            .first { it.startsWith("$name=") }
            .substringAfter("=")
    }

    private fun String.queryParamFromCurl(name: String): String {
        return substringAfter("&$name=")
            .substringBefore("&")
            .substringBefore("'")
    }

    private class RecordingCallback(
        private val expectedUpdates: Int = 0,
        private val expectedPreviewUpdates: Int = 0
    ) : BusRouteQueryCallback {
        val initialRoutes = Collections.synchronizedList(mutableListOf<List<BusRouteOption>>())
        val updates = Collections.synchronizedList(mutableListOf<Pair<String, WaitTimeState>>())
        val previewUpdates = Collections.synchronizedList(mutableListOf<Pair<String, RouteCardStopPreview>>())
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        private val initialRoutesLatch = CountDownLatch(1)
        private val updateLatch = CountDownLatch(expectedUpdates)
        private val previewUpdateLatch = CountDownLatch(expectedPreviewUpdates)
        private val updatesLock = Object()
        private val previewUpdatesLock = Object()

        override fun onInitialRoutes(routes: List<BusRouteOption>) {
            initialRoutes += routes
            initialRoutesLatch.countDown()
        }

        override fun onRouteWaitTimeUpdated(routeId: String, waitTimeState: WaitTimeState) {
            synchronized(updatesLock) {
                updates += routeId to waitTimeState
                updatesLock.notifyAll()
            }
            updateLatch.countDown()
        }

        override fun onRouteStopPreviewUpdated(routeId: String, preview: RouteCardStopPreview) {
            synchronized(previewUpdatesLock) {
                previewUpdates += routeId to preview
                previewUpdatesLock.notifyAll()
            }
            previewUpdateLatch.countDown()
        }

        override fun onFailure(error: Throwable) {
            failures += error
        }

        fun awaitInitialRoutes(timeoutMillis: Long = 1_000): List<BusRouteOption> {
            assertTrue(
                "Initial routes callback was not invoked",
                initialRoutesLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)
            )
            synchronized(initialRoutes) {
                return initialRoutes.first()
            }
        }

        fun awaitUpdateCount(expectedCount: Int, timeoutMillis: Long = 1_000): List<Pair<String, WaitTimeState>> {
            if (expectedCount == expectedUpdates && expectedUpdates > 0) {
                assertTrue(
                    "Expected at least $expectedCount ETA updates, got ${updates.size}",
                    updateLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)
                )
            }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            synchronized(updatesLock) {
                while (updates.size < expectedCount) {
                    val remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                    if (remainingMillis <= 0) break
                    updatesLock.wait(remainingMillis)
                }
                assertTrue(
                    "Expected at least $expectedCount ETA updates, got ${updates.size}",
                    updates.size >= expectedCount
                )
                return updates.toList()
            }
        }

        fun assertNoUpdatesFor(timeoutMillis: Long) {
            Thread.sleep(timeoutMillis)
            synchronized(updatesLock) {
                assertEquals(emptyList<Pair<String, WaitTimeState>>(), updates.toList())
            }
        }

        fun awaitPreviewCount(expectedCount: Int, timeoutMillis: Long = 1_000): List<Pair<String, RouteCardStopPreview>> {
            if (expectedCount == expectedPreviewUpdates && expectedPreviewUpdates > 0) {
                assertTrue(
                    "Expected at least $expectedCount stop preview updates, got ${previewUpdates.size}",
                    previewUpdateLatch.await(timeoutMillis, TimeUnit.MILLISECONDS)
                )
            }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            synchronized(previewUpdatesLock) {
                while (previewUpdates.size < expectedCount) {
                    val remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                    if (remainingMillis <= 0) break
                    previewUpdatesLock.wait(remainingMillis)
                }
                assertTrue(
                    "Expected at least $expectedCount stop preview updates, got ${previewUpdates.size}",
                    previewUpdates.size >= expectedCount
                )
                return previewUpdates.toList()
            }
        }

        fun assertNoPreviewUpdatesFor(timeoutMillis: Long) {
            Thread.sleep(timeoutMillis)
            synchronized(previewUpdatesLock) {
                assertEquals(emptyList<Pair<String, RouteCardStopPreview>>(), previewUpdates.toList())
            }
        }
    }

    companion object {
        private const val fixedQueryTimestamp = 1_780_461_660_000L

        private val WALKING_MODE_LABELS = arrayOf(
            "8 港元8.1 至 90 港元4.8預計71分鐘 步行距離(約)95米",
            "8 港元8.1 至 E11A 港元21.7預計72分鐘 步行距離(約)95米",
            "8 港元8.1 至 E11B 港元21.7預計91分鐘 步行距離(約)95米",
            "8 港元8.1 至 26 港元5.6預計82分鐘 步行距離(約)164米",
            "8 港元8.1 至 10 港元4.8預計74分鐘 步行距離(約)167米",
            "8 港元8.1 至 5B 港元4.8預計80分鐘 步行距離(約)167米",
            "8 港元8.1 至 37A 港元6.6預計72分鐘 步行距離(約)169米",
            "8 港元8.1 至 967X 港元27.3預計79分鐘 步行距離(約)203米",
            "8 港元8.1 至 5X 港元6.1預計77分鐘 步行距離(約)210米",
            "8 港元8.1 至 914 港元12.2預計84分鐘 步行距離(約)210米",
            "788 港元8.7預計29分鐘 步行距離(約)350米",
            "780 港元8.7預計41分鐘 步行距離(約)488米"
        )

        private val FARE_MODE_LABELS = arrayOf(
            "8X 港元8.1 至 10 免費 *預計74分鐘 步行距離(約)355米",
            "8X 港元8.1 至 1 免費 *預計104分鐘 步行距離(約)450米",
            "780 港元8.7預計41分鐘 步行距離(約)488米",
            "788 港元8.7預計29分鐘 步行距離(約)350米"
        )

        private val TIME_MODE_LABELS = arrayOf(
            "789 港元8.7 至 619 港元7.7預計28分鐘 步行距離(約)363米",
            "789 港元8.7 至 15 港元4.9預計29分鐘 步行距離(約)330米",
            "788 港元8.7預計29分鐘 步行距離(約)350米",
            "780 港元8.7預計41分鐘 步行距離(約)488米"
        )
    }
}
