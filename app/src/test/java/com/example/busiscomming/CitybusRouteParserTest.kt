package com.example.busiscomming

import com.example.busiscomming.data.repository.CitybusRouteParseException
import com.example.busiscomming.data.repository.CitybusRouteParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    }

    @Test
    fun parsesSingleSegmentRoute() {
        val routes = CitybusRouteParser.parse(SAMPLE_HTML)
        val route = routes.first { it.routeName == "8X" }

        assertEquals(8.1, route.priceHkd, 0.001)
        assertEquals(45, route.durationMinutes)
        assertEquals(45, route.arrivalMinutes)
        assertEquals(0, route.transferCount)
    }

    @Test
    fun trimsRouteListIdWhitespace() {
        val routes = CitybusRouteParser.parse(
            """
            <div id="routelist2 ">
                <table aria-label="8X 港元8.1預計45分鐘"></table>
            </div>
            """.trimIndent()
        )

        assertEquals(listOf("8X"), routes.map { it.routeName })
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
                    <tr><td>${'$'}7.5 ${'$'}12.9</td></tr>
                </table>
            </div>
            """.trimIndent()
        )

        assertEquals(1, routes.size)
        assertEquals("82X \u2192 102", routes.first().routeName)
        assertEquals(20.4, routes.first().priceHkd, 0.001)
        assertEquals(34, routes.first().durationMinutes)
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
    }
}
