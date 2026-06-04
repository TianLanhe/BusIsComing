package com.example.busiscomming

import com.example.busiscomming.data.repository.CitybusRouteParseException
import com.example.busiscomming.data.repository.CitybusRouteParser
import com.example.busiscomming.data.model.WaitTimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CitybusRouteParserTest {
    @Test
    fun parsesSampleHtmlResults() {
        val routes = CitybusRouteParser.parse(SAMPLE_HTML)

        assertEquals(14, routes.size)
        assertEquals("82X \u2192 102", routes.first().routeName)
        assertEquals(20.4, routes.first().priceHkd, 0.001)
        assertEquals(34, routes.first().durationMinutes)
        assertEquals(34, routes.first().arrivalMinutes)
        assertEquals(1, routes.first().transferCount)
        assertEquals(listOf("82X", "102"), routes.first().routeSegments)
        assertEquals(456, routes.first().walkingDistanceMeters)
    }

    @Test
    fun parsesSingleSegmentRoute() {
        val routes = CitybusRouteParser.parse(SAMPLE_HTML)
        val route = routes.first { it.routeName == "8X" }

        assertEquals(8.1, route.priceHkd, 0.001)
        assertEquals(45, route.durationMinutes)
        assertEquals(45, route.arrivalMinutes)
        assertEquals(0, route.transferCount)
        assertEquals(listOf("8X"), route.routeSegments)
        assertEquals(438, route.walkingDistanceMeters)
    }

    @Test
    fun parsesFreeSegmentAndWalkingDistanceFromCentralSample() {
        val routes = CitybusRouteParser.parse(CENTRAL_SAMPLE_HTML)

        assertEquals(4, routes.size)
        val route = routes.first()
        assertEquals("8X \u2192 1", route.routeName)
        assertEquals(listOf("8X", "1"), route.routeSegments)
        assertEquals(8.1, route.priceHkd, 0.001)
        assertEquals(93, route.durationMinutes)
        assertEquals(93, route.arrivalMinutes)
        assertEquals(1, route.transferCount)
        assertEquals(450, route.walkingDistanceMeters)
    }

    @Test
    fun parsesSingleSegmentWalkingDistanceFromCentralSample() {
        val routes = CitybusRouteParser.parse(CENTRAL_SAMPLE_HTML)
        val route = routes.first { it.routeName == "788" }

        assertEquals(8.7, route.priceHkd, 0.001)
        assertEquals(35, route.durationMinutes)
        assertEquals(35, route.arrivalMinutes)
        assertEquals(0, route.transferCount)
        assertEquals(350, route.walkingDistanceMeters)
    }

    @Test
    fun parsesWalkingModeSample() {
        val routes = CitybusRouteParser.parse(WALKING_MODE_SAMPLE_HTML)

        assertEquals(12, routes.size)
        assertEquals(
            setOf("8 \u2192 90", "8 \u2192 E11A", "8 \u2192 10", "788", "780"),
            routes.map { it.routeName }
                .filter { it in setOf("8 \u2192 90", "8 \u2192 E11A", "8 \u2192 10", "788", "780") }
                .toSet()
        )

        val route = routes.first { it.routeName == "8 \u2192 90" }
        assertEquals(12.9, route.priceHkd, 0.001)
        assertEquals(71, route.durationMinutes)
        assertEquals(71, route.arrivalMinutes)
        assertEquals(1, route.transferCount)
        assertEquals(listOf("8", "90"), route.routeSegments)
        assertEquals(95, route.walkingDistanceMeters)
    }

    @Test
    fun parsesFareModeSampleWithFreeTransfers() {
        val routes = CitybusRouteParser.parse(FARE_MODE_SAMPLE_HTML)

        assertEquals(4, routes.size)
        assertEquals(setOf("8X \u2192 10", "8X \u2192 1", "780", "788"), routes.map { it.routeName }.toSet())

        val routeTo10 = routes.first { it.routeName == "8X \u2192 10" }
        assertEquals(8.1, routeTo10.priceHkd, 0.001)
        assertEquals(74, routeTo10.durationMinutes)
        assertEquals(355, routeTo10.walkingDistanceMeters)

        val routeTo1 = routes.first { it.routeName == "8X \u2192 1" }
        assertEquals(8.1, routeTo1.priceHkd, 0.001)
        assertEquals(104, routeTo1.durationMinutes)
        assertEquals(450, routeTo1.walkingDistanceMeters)
    }

    @Test
    fun parsesTimeModeSampleWithFastestTransfers() {
        val routes = CitybusRouteParser.parse(TIME_MODE_SAMPLE_HTML)

        assertEquals(4, routes.size)
        assertEquals(setOf("789 \u2192 619", "789 \u2192 15", "788", "780"), routes.map { it.routeName }.toSet())

        val routeTo619 = routes.first { it.routeName == "789 \u2192 619" }
        assertEquals(16.4, routeTo619.priceHkd, 0.001)
        assertEquals(28, routeTo619.durationMinutes)
        assertEquals(363, routeTo619.walkingDistanceMeters)

        val routeTo15 = routes.first { it.routeName == "789 \u2192 15" }
        assertEquals(13.6, routeTo15.priceHkd, 0.001)
        assertEquals(29, routeTo15.durationMinutes)
        assertEquals(330, routeTo15.walkingDistanceMeters)
    }

    @Test
    fun trimsRouteListIdWhitespace() {
        val routes = CitybusRouteParser.parse(
            """
            <div id="routelist2 ">
                <table aria-label="8X 港元8.1預計45分鐘 步行距離(約)438米"></table>
            </div>
            """.trimIndent()
        )

        assertEquals(listOf("8X"), routes.map { it.routeName })
    }

    @Test
    fun parsesSingleLegFirstEtaQueryFromShowroutep2p() {
        val routes = CitybusRouteParser.parse(
            routeTableHtml(
                label = "8X 港元8.1預計33分鐘 步行距離(約) 438米",
                info = "1|*|CTB||8X-THR-1||6||20||O|*|"
            )
        )

        val firstLeg = routes.first().firstLegEtaQuery
        assertEquals("CTB", firstLeg?.company)
        assertEquals("8X-THR-1", firstLeg?.routeVariant)
        assertEquals("8X", firstLeg?.route)
        assertEquals(6, firstLeg?.boardingSeq)
        assertEquals(20, firstLeg?.alightingSeq)
        assertEquals("O", firstLeg?.bound)
        assertEquals("outbound", firstLeg?.directionPath)
        assertEquals(WaitTimeState.Loading, routes.first().waitTimeState)
    }

    @Test
    fun parsesOnlyFirstLegFromMultiLegShowroutep2p() {
        val routes = CitybusRouteParser.parse(
            routeTableHtml(
                label = "8X 港元8.1 至 1 免費 *預計93分鐘 步行距離(約) 450米",
                info = "2|*|CTB||8X-THR-1||6||31||O|*|CTB||1-MAF-1||5||15||I|*|"
            )
        )

        val firstLeg = routes.first().firstLegEtaQuery
        assertEquals("8X", firstLeg?.route)
        assertEquals(6, firstLeg?.boardingSeq)
        assertEquals(31, firstLeg?.alightingSeq)
        assertEquals("outbound", firstLeg?.directionPath)
        assertEquals(listOf("8X", "1"), routes.first().routeSegments)
    }

    @Test
    fun keepsRouteWhenShowroutep2pIsMissing() {
        val routes = CitybusRouteParser.parse(
            """
            <div id="routelist2">
                <table aria-label="8X 港元8.1預計45分鐘 步行距離(約)438米"></table>
            </div>
            """.trimIndent()
        )

        assertEquals(1, routes.size)
        assertEquals("8X", routes.first().routeName)
        assertEquals(null, routes.first().firstLegEtaQuery)
        assertEquals(WaitTimeState.Unavailable, routes.first().waitTimeState)
    }

    @Test
    fun parsesRealCandidateShowroutep2pAttribute() {
        val routes = CitybusRouteParser.parse(
            """
            <div id="routelist2">
                <table width="100%" aria-label="8X 港元8.1 至 1 免費 *預計93分鐘 步行距離(約) 450米" onclick="lastP2PResultListScrollY=document.getElementById('routelist2').scrollTop; showroutep2p('2|*|CTB||8X-THR-1||6||31||O|*|CTB||1-MAF-1||5||15||I|*|','0','21:54|*|93'); showrouteline('2|*|CTB||8X-THR-1||6||31||O|*|CTB||1-MAF-1||5||15||I|*|');"></table>
            </div>
            """.trimIndent()
        )

        assertEquals("8X \u2192 1", routes.first().routeName)
        assertEquals(8.1, routes.first().priceHkd, 0.001)
        assertEquals(93, routes.first().durationMinutes)
        assertTrue(routes.first().waitTimeState is WaitTimeState.Loading)
        assertEquals("8X-THR-1", routes.first().firstLegEtaQuery?.routeVariant)
    }

    @Test
    fun fallsBackToTableTextWhenAriaLabelIsMissing() {
        val routes = CitybusRouteParser.parse(
            """
            <div id="routelist2">
                <table>
                    <tr>
                        <td>
                            <table class="routenocell"><tr><td>82X</td></tr></table>
                            <table class="routenocell"><tr><td>102</td></tr></table>
                        </td>
                        <td>預計<br>34 分鐘</td>
                    </tr>
                    <tr><td>${'$'}7.5 免費 *</td></tr>
                    <tr><td>步行距離(約) 456米</td></tr>
                </table>
            </div>
            """.trimIndent()
        )

        assertEquals(1, routes.size)
        assertEquals("82X \u2192 102", routes.first().routeName)
        assertEquals(7.5, routes.first().priceHkd, 0.001)
        assertEquals(34, routes.first().durationMinutes)
        assertEquals(456, routes.first().walkingDistanceMeters)
    }

    @Test
    fun skipsCandidateWhenWalkingDistanceIsMissing() {
        val routes = CitybusRouteParser.parse(
            """
            <div id="routelist2">
                <table aria-label="8X 港元8.1預計45分鐘"></table>
            </div>
            """.trimIndent()
        )

        assertEquals(emptyList<Any>(), routes)
    }

    @Test
    fun returnsEmptyWhenRouteListHasNoValidCandidates() {
        val routes = CitybusRouteParser.parse(
            """
            <div id="routelist2">
                <table aria-label="No Result"></table>
            </div>
            """.trimIndent()
        )

        assertEquals(emptyList<Any>(), routes)
    }

    @Test
    fun throwsWhenRouteListIsMissing() {
        assertThrows(CitybusRouteParseException::class.java) {
            CitybusRouteParser.parse("<div id=\"other\"></div>")
        }
    }

    companion object {
        private val SAMPLE_HTML = """
            <div id="p2p_routelist">
                <div id="routelist2 " style="height:calc(100%-175px);">
                    <table aria-label="82X 港元7.5 至 102 港元12.9預計34分鐘 步行距離(約)456米"></table>
                    <table aria-label="82X 港元7.5 至 99 港元9.7預計36分鐘 步行距離(約)462米"></table>
                    <table aria-label="8P 港元8.1 至 106 港元7.7預計36分鐘 步行距離(約)649米"></table>
                    <table aria-label="8P 港元8.1 至 102 港元7.7預計37分鐘 步行距離(約)649米"></table>
                    <table aria-label="8P 港元8.1 至 110 港元7.7預計38分鐘 步行距離(約)675米"></table>
                    <table aria-label="8P 港元8.1 至 A11 港元6.8預計39分鐘 步行距離(約)673米"></table>
                    <table aria-label="82X 港元7.5 至 85 港元5.2預計40分鐘 步行距離(約)372米"></table>
                    <table aria-label="82X 港元7.5 至 112 港元12.2預計41分鐘 步行距離(約)608米"></table>
                    <table aria-label="8P 港元8.1 至 10 港元4.8預計42分鐘 步行距離(約)561米"></table>
                    <table aria-label="82X 港元7.5 至 81 港元5.5預計42分鐘 步行距離(約)458米"></table>
                    <table aria-label="8X 港元8.1預計45分鐘 步行距離(約)438米"></table>
                    <table aria-label="106 港元12.9預計47分鐘 步行距離(約)420米"></table>
                    <table aria-label="85 港元5.2預計56分鐘 步行距離(約)296米"></table>
                    <table aria-label="8H 港元7.0預計73分鐘 步行距離(約)444米"></table>
                </div>
            </div>
        """.trimIndent()

        private val CENTRAL_SAMPLE_HTML = """
            <div id="p2p_routelist">
                <div id="routelist2 " style="height:calc(100%-175px);">
                    <table aria-label="8X 港元8.1 至 1 免費 *預計93分鐘 步行距離(約)450米"></table>
                    <table aria-label="8X 港元8.1 至 10 免費 *預計74分鐘 步行距離(約)355米"></table>
                    <table aria-label="788 港元8.7預計35分鐘 步行距離(約)350米"></table>
                    <table aria-label="780 港元8.7預計54分鐘 步行距離(約)488米"></table>
                </div>
            </div>
        """.trimIndent()

        private val WALKING_MODE_SAMPLE_HTML = routeListHtml(
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

        private val FARE_MODE_SAMPLE_HTML = routeListHtml(
            "8X 港元8.1 至 10 免費 *預計74分鐘 步行距離(約)355米",
            "8X 港元8.1 至 1 免費 *預計104分鐘 步行距離(約)450米",
            "780 港元8.7預計41分鐘 步行距離(約)488米",
            "788 港元8.7預計29分鐘 步行距離(約)350米"
        )

        private val TIME_MODE_SAMPLE_HTML = routeListHtml(
            "789 港元8.7 至 619 港元7.7預計28分鐘 步行距離(約)363米",
            "789 港元8.7 至 15 港元4.9預計29分鐘 步行距離(約)330米",
            "788 港元8.7預計29分鐘 步行距離(約)350米",
            "780 港元8.7預計41分鐘 步行距離(約)488米"
        )

        private fun routeListHtml(vararg labels: String): String {
            return labels.joinToString(
                separator = "\n",
                prefix = "<div id=\"p2p_routelist\"><div id=\"routelist2 \">\n",
                postfix = "\n</div></div>"
            ) { label -> "<table aria-label=\"$label\"></table>" }
        }

        private fun routeTableHtml(label: String, info: String): String {
            return """
                <div id="routelist2">
                    <table aria-label="$label" onclick="showroutep2p('$info','0','12:00|*|30');"></table>
                </div>
            """.trimIndent()
        }
    }
}
