package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteRecoveryContext
import com.golink.busiscoming.data.model.RouteDetailCompleteness
import com.golink.busiscoming.data.model.RouteDetailDisplayFormatter
import com.golink.busiscoming.data.model.RouteDetailExpansionState
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.repository.CitybusRouteDetailParseException
import com.golink.busiscoming.data.repository.CitybusRouteDetailParser
import com.golink.busiscoming.data.repository.CitybusRouteDetailRepository
import com.golink.busiscoming.data.repository.CitybusRouteParser
import com.golink.busiscoming.data.repository.CitybusSessionRegistry
import com.golink.busiscoming.data.repository.RouteStructureCache
import com.golink.busiscoming.data.repository.WalkingDistanceCache
import com.golink.busiscoming.data.repository.RouteDetailCache
import java.io.IOException
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CitybusRouteDetailRepositoryTest {
    @Test
    fun buildsDetailUrlFromP2pMetadata() {
        val query = query(FIRST_ROUTE_RAW_INFO, generalInfo = "12:34|*|34", listId = "0")
        val url = CitybusRouteDetailRepository().buildDetailUrl(query).toString()

        assertTrue(url.startsWith("https://mobile.citybus.com.hk/nwp3/getp2pstopinroute.php?"))
        assertTrue(url.contains("info=2%7C*%7CCTB%7C%7C82X-ISR-1"))
        assertTrue(url.contains("ginfo=12%3A34%7C*%7C34"))
        assertTrue(url.contains("lid=0"))
        assertTrue(url.contains("l=0"))
        assertFalse(url.contains("data.gov.hk"))
    }

    @Test
    fun usesNoExplicitCitybusRequestHeaders() {
        assertTrue(CitybusRouteDetailRepository().requestHeaders().isEmpty())
    }

    @Test
    fun sendsOnlyMatchingPhpSessionCookieToCitybusDetailEndpoint() {
        val context = recoveryContext("T")
        val registry = CitybusSessionRegistry(referenceFactory = { "opaque" })
        val reference = registry.register("matching-session", "0", context)
        var capturedHeaders = emptyMap<String, String>()
        val repository = CitybusRouteDetailRepository(
            sessionRegistry = registry,
            detailFetcher = { _, headers ->
                capturedHeaders = headers
                resourceText("citybus/getp2pstopinroute-n118.html")
            }
        )

        repository.loadRouteDetail(
            route(
                query("1|*|CTB||N118-TOS-1||5||9||O|*|").copy(
                    sessionRef = reference,
                    recoveryContext = context
                ),
                routeName = "N118"
            )
        )

        assertEquals(mapOf("Cookie" to "PHPSESSID=matching-session"), capturedHeaders)
        assertFalse(capturedHeaders.containsKey("User-Agent"))
        assertFalse(capturedHeaders.containsKey("Referer"))
    }

    @Test
    fun classifiesSessionMissingAndSupportsSimplifiedChineseAndEnglishFallbacks() {
        val plan = query(SINGLE_ROUTE_RAW_INFO).plan

        val missing = CitybusRouteDetailParser.parseDetail(
            resourceText("citybus/getp2pstopinroute-session-missing.html"),
            plan
        )
        val simplified = CitybusRouteDetailParser.parseDetail(
            resourceText("citybus/getp2pstopinroute-sc-fallback.html"),
            plan
        )
        val english = CitybusRouteDetailParser.parseDetail(
            resourceText("citybus/getp2pstopinroute-en-fallback.html"),
            plan
        )

        assertEquals(RouteDetailCompleteness.SESSION_MISSING, missing.completeness)
        assertEquals(RouteDetailCompleteness.COMPLETE, simplified.completeness)
        assertEquals(123, simplified.originWalking?.distanceMeters)
        assertEquals(45, simplified.destinationWalking?.distanceMeters)
        assertEquals(RouteDetailCompleteness.COMPLETE, english.completeness)
        assertEquals(88, english.originWalking?.distanceMeters)
        assertEquals(34, english.destinationWalking?.distanceMeters)
    }

    @Test
    fun recoversExpiredSessionOnceAndMatchesCandidateByPlanFingerprint() {
        val context = recoveryContext("F")
        val registry = CitybusSessionRegistry(referenceFactory = { raw -> "ref-${raw.takeLast(3)}" })
        val freshReference = registry.register("fresh-session", "0", context)
        val originalQuery = query("1|*|CTB||N118-TOS-1||5||9||O|*|", listId = "old").copy(
            sessionRef = "expired-reference",
            recoveryContext = context
        )
        var recoveryCount = 0
        var capturedUrl: URL? = null
        var capturedHeaders = emptyMap<String, String>()
        val repository = CitybusRouteDetailRepository(
            sessionRegistry = registry,
            recoverySearcher = { _, _ ->
                recoveryCount += 1
                listOf(
                    route(query("1|*|CTB||OTHER-1||1||2||O|*|")),
                    route(
                        originalQuery.copy(
                            listId = "fresh-lid",
                            sessionRef = freshReference
                        ),
                        routeName = "N118"
                    )
                )
            },
            detailFetcher = { url, headers ->
                capturedUrl = url
                capturedHeaders = headers
                resourceText("citybus/getp2pstopinroute-n118.html")
            }
        )

        val detail = repository.loadRouteDetail(route(originalQuery, routeName = "N118"))

        assertEquals(1, recoveryCount)
        assertTrue(capturedUrl.toString().contains("lid=fresh-lid"))
        assertEquals("PHPSESSID=fresh-session", capturedHeaders["Cookie"])
        assertEquals(262, detail.completeWalkingDistanceMeters)
    }

    @Test
    fun retriesSessionMissingPayloadOnlyOnceThenDegradesHonestly() {
        val context = recoveryContext("T")
        val registry = CitybusSessionRegistry(referenceFactory = { raw -> "ref-${raw.takeLast(3)}" })
        val oldReference = registry.register("old-session", "0", context)
        val freshReference = registry.register("fresh-session", "0", context)
        val originalQuery = query(SINGLE_ROUTE_RAW_INFO, listId = "old").copy(
            sessionRef = oldReference,
            recoveryContext = context
        )
        var recoveryCount = 0
        var fetchCount = 0
        val repository = CitybusRouteDetailRepository(
            sessionRegistry = registry,
            recoverySearcher = { _, _ ->
                recoveryCount += 1
                listOf(
                    route(
                        originalQuery.copy(
                            generalInfo = "23:59|*|30",
                            listId = "fresh",
                            sessionRef = freshReference
                        )
                    )
                )
            },
            detailFetcher = { _, _ ->
                fetchCount += 1
                resourceText("citybus/getp2pstopinroute-session-missing.html")
            }
        )

        val detail = repository.loadRouteDetail(route(originalQuery))

        assertEquals(1, recoveryCount)
        assertEquals(2, fetchCount)
        assertEquals(RouteDetailCompleteness.PARTIAL, detail.completeness)
        assertFalse(detail.hasCompleteWalkingDistance)
        assertEquals("12:00", detail.plannedArrivalTime)
    }

    @Test
    fun noMatchingRecoveryCandidateStopsAfterOneSearchAndKeepsOriginalRouteContext() {
        val context = recoveryContext("W")
        val originalQuery = query(SINGLE_ROUTE_RAW_INFO, generalInfo = "12:34|*|30").copy(
            sessionRef = "expired-reference",
            recoveryContext = context
        )
        var recoveryCount = 0
        var fetchCount = 0
        val repository = CitybusRouteDetailRepository(
            sessionRegistry = CitybusSessionRegistry(),
            recoverySearcher = { _, _ ->
                recoveryCount += 1
                listOf(route(query("1|*|CTB||OTHER-1||1||2||O|*|")))
            },
            detailFetcher = { _, headers ->
                fetchCount += 1
                assertTrue(headers.isEmpty())
                resourceText("citybus/getp2pstopinroute-session-missing.html")
            }
        )

        val detail = repository.loadRouteDetail(route(originalQuery))

        assertEquals(1, recoveryCount)
        assertEquals(1, fetchCount)
        assertEquals(RouteDetailCompleteness.PARTIAL, detail.completeness)
        assertEquals("12:34", detail.plannedArrivalTime)
        assertEquals(listOf("8X"), detail.legs.map { it.route })
    }

    @Test
    fun splitCachesReuseStableStructureAndWalkingAcrossSessionsButNotEndpointsOrPlannedTimes() {
        val structureCache = RouteStructureCache()
        val walkingCache = WalkingDistanceCache()
        val firstContext = recoveryContext("T")
        val secondContext = firstContext.copy(originLatitude = 22.300001)
        var fetchCount = 0
        val repository = CitybusRouteDetailRepository(
            structureCache = structureCache,
            walkingCache = walkingCache,
            detailFetcher = { _, _ ->
                fetchCount += 1
                resourceText("citybus/getp2pstopinroute-n118.html")
            }
        )
        val firstQuery = query("1|*|CTB||N118-TOS-1||5||9||O|*|", generalInfo = "02:04|*|13").copy(
            recoveryContext = firstContext
        )
        val sameContextNewSession = firstQuery.copy(
            rawInfo = "1|*|CTB||N118-TOS-1||5||9||O|*|",
            listId = "new-list",
            sessionRef = "different-opaque-ref",
            generalInfo = "03:15|*|13"
        )

        repository.loadRouteDetail(route(firstQuery, routeName = "N118"))
        val cached = repository.loadRouteDetail(route(sameContextNewSession, routeName = "N118"))
        repository.loadRouteDetail(
            route(sameContextNewSession.copy(recoveryContext = secondContext), routeName = "N118")
        )

        assertEquals(2, fetchCount)
        assertEquals(262, cached.completeWalkingDistanceMeters)
        assertEquals("03:15", cached.plannedArrivalTime)
        assertEquals(null, cached.plannedDepartureTime)
    }

    @Test
    fun parsesMultiLegRouteDetailAndNormalizesStationNames() {
        val detail = CitybusRouteDetailParser.parse(DETAIL_HTML, query(FIRST_ROUTE_RAW_INFO).plan)

        assertEquals(2, detail.size)
        val firstLeg = detail.first()
        assertEquals("82X", firstLeg.route)
        assertEquals("82X-ISR-1", firstLeg.routeVariant)
        assertEquals("樂軒臺", firstLeg.boardingStop.displayName)
        assertEquals("樂軒臺, 柴灣道", firstLeg.boardingStop.rawName)
        assertEquals(RouteDetailStopRole.BOARDING, firstLeg.boardingStop.role)
        assertEquals(listOf("環翠商場", "張振興伉儷書院"), firstLeg.viaStops.map { it.displayName })
        assertEquals(RouteDetailStopRole.VIA, firstLeg.viaStops.first().role)
        assertEquals("健康村", firstLeg.alightingStop.displayName)
        assertEquals(RouteDetailStopRole.ALIGHTING, firstLeg.alightingStop.role)
        assertEquals("001227", firstLeg.boardingStop.stopId)
        assertEquals(6, firstLeg.boardingStop.sequence)
        assertEquals(22.264934942091, firstLeg.boardingStop.latitude, 0.0000001)

        val secondLeg = detail[1]
        assertEquals("102", secondLeg.route)
        assertEquals("健康村", secondLeg.boardingStop.displayName)
        assertEquals(listOf("健威花園", "新都城大廈"), secondLeg.viaStops.map { it.displayName })
        assertEquals("長康街", secondLeg.alightingStop.displayName)
    }

    @Test
    fun parsesDirectionTextWhenInterfaceReturnsIt() {
        val detail = CitybusRouteDetailParser.parse(DIRECTION_DETAIL_HTML, query(SINGLE_ROUTE_RAW_INFO).plan)

        assertEquals("筲箕灣", detail.first().directionText)
        assertEquals("筲箕灣", RouteDetailDisplayFormatter.directionLabel(detail.first().directionText))
    }

    @Test
    fun parsesFirstWalkingDistanceBeforeRouteDetailRows() {
        val distance = CitybusRouteDetailParser.parseOriginWalkingDistanceMeters(
            """
            <div>步行距離(約) 236米</div>
            <table class="p2proutetitle"><tr><td>82X 往 小西灣</td></tr></table>
            <div>步行距離(約) 88米</div>
            """.trimIndent()
        )

        assertEquals(236, distance)
    }

    @Test
    fun doesNotReuseOriginDistanceWhenDestinationMarkerIsMissing() {
        val distance = CitybusRouteDetailParser.parseDestinationWalkingDistanceMeters(
            """
            <div>步行距離(約) 236米</div>
            <table class="p2proutetitle"><tr><td>82X 往 小西灣</td></tr></table>
            """.trimIndent()
        )

        assertEquals(null, distance)
    }

    @Test
    fun hidesDirectionWhenItIsBlank() {
        assertEquals(null, RouteDetailDisplayFormatter.directionLabel(""))
        assertEquals(null, RouteDetailDisplayFormatter.directionLabel(null))
    }

    @Test
    fun throwsForEmptyOrMalformedDetailResponses() {
        assertThrows(CitybusRouteDetailParseException::class.java) {
            CitybusRouteDetailParser.parse("<div></div>", query(SINGLE_ROUTE_RAW_INFO).plan)
        }
        assertThrows(CitybusRouteDetailParseException::class.java) {
            CitybusRouteDetailParser.parse(MISSING_BOARDING_HTML, query(SINGLE_ROUTE_RAW_INFO).plan)
        }
    }

    @Test
    fun repositoryCachesSuccessfulDetailsAndDoesNotCacheFailures() {
        var fetchCount = 0
        val route = route(query("1|*|CTB||N118-TOS-1||5||9||O|*|"), routeName = "N118")
        val repository = CitybusRouteDetailRepository(
            detailFetcher = { _: URL, _ ->
                fetchCount += 1
                resourceText("citybus/getp2pstopinroute-n118.html")
            }
        )

        val firstDetail = repository.loadRouteDetail(route)
        assertEquals(1, firstDetail.legs.size)
        assertEquals("02:04", firstDetail.plannedArrivalTime)
        val cachedDetail = repository.loadRouteDetail(route)
        assertEquals(1, cachedDetail.legs.size)
        assertEquals(firstDetail.originWalking, cachedDetail.originWalking)
        assertEquals(firstDetail.transfers, cachedDetail.transfers)
        assertEquals(firstDetail.destinationWalking, cachedDetail.destinationWalking)
        assertEquals(1, fetchCount)

        var failingFetchCount = 0
        val failingRepository = CitybusRouteDetailRepository(
            detailFetcher = { _: URL, _ ->
                failingFetchCount += 1
                throw IOException("detail failed")
            }
        )

        assertThrows(IOException::class.java) {
            failingRepository.loadRouteDetail(route)
        }
        assertThrows(IOException::class.java) {
            failingRepository.loadRouteDetail(route)
        }
        assertEquals(2, failingFetchCount)
    }

    @Test
    fun repositoryCanUseProvidedN8pN969FixtureAsMockHttpResponse() {
        val route = route(
            query(
                rawInfo = "2|*|CTB||N8P-ISR-1||6||15||O|*|CTB||N969-TSC-1||3||10||I|*|",
                generalInfo = "01:21|*|49"
            ),
            routeName = "N8P \u2192 N969",
            priceHkd = 51.2,
            durationMinutes = 49,
            walkingDistanceMeters = 378
        )
        var capturedUrl: URL? = null
        val repository = CitybusRouteDetailRepository(
            detailFetcher = { url, _ ->
                capturedUrl = url
                resourceText("citybus/getp2pstopinroute-n8p-n969.html")
            }
        )

        val detail = repository.loadRouteDetail(route)

        assertTrue(capturedUrl.toString().contains("getp2pstopinroute.php"))
        assertEquals("N8P \u2192 N969", detail.routeName)
        assertEquals(2, detail.legs.size)
        assertEquals(listOf("N8P", "N969"), detail.legs.map { it.route })
        assertEquals(listOf("小西灣藍灣半島", "天水圍市中心"), detail.legs.map { it.directionText })
        assertEquals("小西灣藍灣半島", RouteDetailDisplayFormatter.directionLabel(detail.legs.first().directionText))
        assertEquals("樂軒臺", detail.legs.first().boardingStop.displayName)
        assertEquals("灣仔消防局", detail.legs.first().alightingStop.displayName)
        assertEquals("堅拿道東", detail.legs[1].boardingStop.displayName)
        assertEquals("利源東街", detail.legs[1].alightingStop.displayName)
        assertEquals(8, detail.legs.first().viaStops.size)
        assertEquals(6, detail.legs[1].viaStops.size)
        assertEquals(14, detail.totalViaStopCount)
        assertEquals(378, detail.walkingDistanceMeters)
        assertEquals(403, detail.completeWalkingDistanceMeters)
        assertTrue(detail.hasCompleteWalkingDistance)
        assertEquals(243, detail.originWalking?.distanceMeters)
        assertEquals(25, detail.transfers.single().walking?.distanceMeters)
        assertEquals(RouteDetailTransferType.WALK_TO_TRANSFER_STOP, detail.transfers.single().type)
        assertEquals(135, detail.destinationWalking?.distanceMeters)
        assertEquals("00:32", detail.plannedDepartureTime)
        assertEquals("01:21", detail.plannedArrivalTime)
        assertEquals(9.7, detail.legs[0].fareHkd!!, 0.0)
        assertEquals("00:42", detail.legs[0].plannedBoardingTime)
        assertEquals("01:03", detail.legs[0].plannedAlightingTime)
        assertEquals(41.5, detail.legs[1].fareHkd!!, 0.0)
        assertEquals("01:09", detail.legs[1].plannedBoardingTime)
        assertEquals("01:18", detail.legs[1].plannedAlightingTime)
    }

    @Test
    fun repositoryCanUseProvidedN118FixtureAsMockHttpResponse() {
        val route = route(
            query(
                rawInfo = "1|*|CTB||N118-TOS-1||5||9||O|*|",
                generalInfo = "02:04|*|13"
            ),
            routeName = "N118",
            priceHkd = 17.8,
            durationMinutes = 13,
            walkingDistanceMeters = 262
        )
        val repository = CitybusRouteDetailRepository(
            detailFetcher = { _, _ -> resourceText("citybus/getp2pstopinroute-n118.html") }
        )

        val detail = repository.loadRouteDetail(route)

        assertEquals(1, detail.legs.size)
        val leg = detail.legs.first()
        assertEquals("N118", leg.route)
        assertEquals("長沙灣(深旺道)", leg.directionText)
        assertEquals("長沙灣(深旺道)", RouteDetailDisplayFormatter.directionLabel(leg.directionText))
        assertEquals("樂軒臺", leg.boardingStop.displayName)
        assertEquals(listOf("環翠商場", "環翠邨澤翠樓", "興華邨卓華樓"), leg.viaStops.map { it.displayName })
        assertEquals("興華邨豐興樓", leg.alightingStop.displayName)
        assertEquals(262, detail.completeWalkingDistanceMeters)
        assertTrue(detail.hasCompleteWalkingDistance)
        assertEquals(17.8, leg.fareHkd!!, 0.0)
        assertEquals("02:01", leg.plannedBoardingTime)
        assertEquals("02:04", leg.plannedAlightingTime)
        assertEquals("02:04", detail.plannedArrivalTime)
    }

    @Test
    fun parsesSameStopTransferWithoutInventingWalkingDistanceWhenOptionalFieldsAreMissing() {
        val detail = repositoryForFixture("citybus/getp2pstopinroute-same-stop-missing.html").loadRouteDetail(
            route(
                query("2|*|CTB||82X-ISR-1||6||7||O|*|CTB||102-MEF-1||12||13||O|*|"),
                routeName = "82X → 102",
                walkingDistanceMeters = 150
            )
        )

        assertEquals(2, detail.legs.size)
        assertEquals(RouteDetailTransferType.SAME_STOP, detail.transfers.single().type)
        assertEquals(null, detail.transfers.single().walking)
        assertEquals(150, detail.completeWalkingDistanceMeters)
        assertTrue(detail.hasCompleteWalkingDistance)
        assertEquals(null, detail.legs.first().fareHkd)
        assertEquals(null, detail.legs[1].plannedBoardingTime)
    }

    @Test
    fun incompleteWalkingRowsFallBackToCardDistanceWithoutLosingStationStructure() {
        val detail = repositoryForFixture("citybus/getp2pstopinroute-partial-missing.html").loadRouteDetail(
            route(
                query("2|*|CTB||82X-ISR-1||6||7||O|*|CTB||102-MEF-1||12||13||O|*|"),
                routeName = "82X → 102",
                walkingDistanceMeters = 378
            )
        )

        assertEquals(2, detail.legs.size)
        assertFalse(detail.hasCompleteWalkingDistance)
        assertEquals(null, detail.completeWalkingDistanceMeters)
        assertEquals(378, detail.displayWalkingDistanceMeters)
        assertEquals(RouteDetailTransferType.WALK_TO_TRANSFER_STOP, detail.transfers.single().type)
        assertEquals(null, detail.transfers.single().walking?.distanceMeters)
        assertEquals("12:30", detail.plannedArrivalTime)
    }

    @Test
    fun cacheHonorsExpiryAndLanguageIsolation() {
        var now = 1_000L
        val cache = RouteDetailCache(clock = { now }, ttlMillis = 100L)
        val tcKey = query(SINGLE_ROUTE_RAW_INFO, lang = "0").cacheKey()
        val enKey = query(SINGLE_ROUTE_RAW_INFO, lang = "1").cacheKey()
        val legs = CitybusRouteDetailParser.parse(DIRECTION_DETAIL_HTML, query(SINGLE_ROUTE_RAW_INFO).plan)

        cache.put(tcKey, legs)

        assertNotNull(cache.get(tcKey))
        assertEquals(null, cache.get(enKey))

        now += 101L

        assertEquals(null, cache.get(tcKey))
    }

    @Test
    fun expansionStateDefaultsFoldedAndTogglesPerLeg() {
        val state = RouteDetailExpansionState(2)

        assertFalse(state.isExpanded(0))
        assertFalse(state.isExpanded(1))

        state.toggle(1)

        assertFalse(state.isExpanded(0))
        assertTrue(state.isExpanded(1))
    }

    private fun query(
        rawInfo: String,
        generalInfo: String = "12:00|*|30",
        listId: String = "0",
        lang: String = "0"
    ): P2pRouteDetailQuery {
        return P2pRouteDetailQuery(
            rawInfo = rawInfo,
            generalInfo = generalInfo,
            listId = listId,
            lang = lang,
            plan = requireNotNull(CitybusRouteParser.parseP2pRoutePlan(rawInfo))
        )
    }

    private fun route(
        query: P2pRouteDetailQuery,
        routeName: String = "8X",
        priceHkd: Double = 8.1,
        durationMinutes: Int = 38,
        walkingDistanceMeters: Int = 438
    ): BusRouteOption {
        return BusRouteOption(
            routeName = routeName,
            routeSegments = routeName.split(" \u2192 "),
            priceHkd = priceHkd,
            durationMinutes = durationMinutes,
            arrivalMinutes = durationMinutes,
            transferCount = 0,
            walkingDistanceMeters = walkingDistanceMeters,
            routeDetailQuery = query
        )
    }

    private fun recoveryContext(mode: String): P2pRouteRecoveryContext {
        return P2pRouteRecoveryContext(
            originLatitude = 22.29361,
            originLongitude = 114.20056,
            destinationLatitude = 22.28190,
            destinationLongitude = 114.15815,
            searchMode = mode
        )
    }

    private fun resourceText(path: String): String {
        return requireNotNull(javaClass.classLoader?.getResource(path)) {
            "Missing test resource $path"
        }.readText()
    }

    private fun repositoryForFixture(path: String): CitybusRouteDetailRepository {
        return CitybusRouteDetailRepository(detailFetcher = { _, _ -> resourceText(path) })
    }

    companion object {
        private const val FIRST_ROUTE_RAW_INFO =
            "2|*|CTB||82X-ISR-1||6||9||O|*|CTB||102-MEF-1||12||15||O|*|"
        private const val SINGLE_ROUTE_RAW_INFO =
            "1|*|CTB||8X-THR-1||6||8||O|*|"

        private const val DETAIL_HTML = """
            <div>
                <table class="p2proutetitle"><tr><td>82X</td><td>往 </td></tr></table>
                <table class="p2plistcell" onclick="stopclick1('001227','22.264934942091','114.24163903053001','6','82X-ISR-1','','Y','22.264934942091','114.24163903053001');"><tr><td align="left"><table><tr><td align="left">樂軒臺, 柴灣道</td></tr></table></td></tr></table>
                <table class="p2plistcell" onclick="stopclick1('001228','22.263136522091','114.23869463053','7','82X-ISR-1','','Y','22.263136522091','114.23869463053');"><tr><td align="left"><table><tr><td align="left">環翠商場, 柴灣道</td></tr></table></td></tr></table>
                <table class="p2plistcell" onclick="stopclick1('001372','22.267973272090998','114.23757547053','8','82X-ISR-1','','Y','22.267973272090998','114.23757547053');"><tr><td align="left"><table><tr><td align="left">張振興伉儷書院, 東區走廊</td></tr></table></td></tr></table>
                <table class="p2plistcell" onclick="stopclick1('001265','22.292090752091','114.20650947053001','9','82X-ISR-1','','Y','22.292090752091','114.20650947053001');"><tr><td align="left"><table><tr><td align="left">健康村, 英皇道</td></tr></table></td></tr></table>
                <table class="p2proutetitle"><tr><td>102</td><td>往 </td></tr></table>
                <table class="p2plistcell" onclick="stopclick1('001276','22.291904002090998','114.20644476053','12','102-MEF-1','','Y','22.291904002090998','114.20644476053');"><tr><td align="left"><table><tr><td align="left">健康村, 英皇道</td></tr></table></td></tr></table>
                <table class="p2plistcell" onclick="stopclick1('001277','22.291810122091','114.20389006053','13','102-MEF-1','','Y','22.291810122091','114.20389006053');"><tr><td align="left"><table><tr><td align="left">健威花園, 英皇道</td></tr></table></td></tr></table>
                <table class="p2plistcell" onclick="stopclick1('001280','22.290908382091','114.19953255053001','14','102-MEF-1','','Y','22.290908382091','114.19953255053001');"><tr><td align="left"><table><tr><td align="left">新都城大廈, 英皇道</td></tr></table></td></tr></table>
                <table class="p2plistcell" onclick="stopclick1('001364','22.290234382091','114.19623343053','15','102-MEF-1','','Y','22.290234382091','114.19623343053');"><tr><td align="left"><table><tr><td align="left">長康街, 英皇道</td></tr></table></td></tr></table>
            </div>
        """

        private const val DIRECTION_DETAIL_HTML = """
            <div>
                <table class="p2proutetitle"><tr><td>8X</td><td>往 筲箕灣</td></tr></table>
                <table class="p2plistcell" onclick="stopclick1('001227','22.264934942091','114.24163903053001','6','8X-THR-1','','Y','22.264934942091','114.24163903053001');"><tr><td align="left"><table><tr><td align="left">樂軒臺, 柴灣道</td></tr></table></td></tr></table>
                <table class="p2plistcell" onclick="stopclick1('001228','22.263136522091','114.23869463053','7','8X-THR-1','','Y','22.263136522091','114.23869463053');"><tr><td align="left"><table><tr><td align="left">環翠商場, 柴灣道</td></tr></table></td></tr></table>
                <table class="p2plistcell" onclick="stopclick1('001372','22.267973272090998','114.23757547053','8','8X-THR-1','','Y','22.267973272090998','114.23757547053');"><tr><td align="left"><table><tr><td align="left">張振興伉儷書院, 東區走廊</td></tr></table></td></tr></table>
            </div>
        """

        private const val MISSING_BOARDING_HTML = """
            <div>
                <table class="p2plistcell" onclick="stopclick1('001372','22.267973272090998','114.23757547053','8','8X-THR-1','','Y','22.267973272090998','114.23757547053');"><tr><td align="left"><table><tr><td align="left">張振興伉儷書院, 東區走廊</td></tr></table></td></tr></table>
            </div>
        """
    }
}
