package com.example.busiscomming

import com.example.busiscomming.data.model.Place
import com.example.busiscomming.data.repository.CitybusBusRouteRepository
import java.io.IOException
import java.net.URL
import java.util.Collections
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
    fun searchesAllModesWithSameQueryTimeAndAggregatesUniqueResults() {
        val requestedUrls = Collections.synchronizedList(mutableListOf<URL>())
        val repository = CitybusBusRouteRepository(clock = { fixedQueryTimestamp }) { url, _ ->
            requestedUrls += url
            when (url.queryParam("m1")) {
                "T" -> routeHtml(*TIME_MODE_LABELS)
                "F" -> routeHtml(*FARE_MODE_LABELS)
                "W" -> routeHtml(*WALKING_MODE_LABELS)
                else -> error("Unexpected m1")
            }
        }

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
        val repository = CitybusBusRouteRepository(clock = { fixedQueryTimestamp }) { url, _ ->
            when (url.queryParam("m1")) {
                "T" -> routeHtml("8X 港元8.1 至 1 免費 *預計74分鐘 步行距離(約)355米")
                "F" -> routeHtml("8X 港元8.1 至 1 免費 *預計104分鐘 步行距離(約)450米")
                "W" -> routeHtml("8X 港元8.1 至 1 免費 *預計74分鐘 步行距離(約)355米")
                else -> error("Unexpected m1")
            }
        }

        val routes = repository.searchRoutes(origin, destination)

        assertEquals(2, routes.size)
        assertEquals(listOf(74, 104), routes.map { it.durationMinutes })
        assertEquals(listOf(355, 450), routes.map { it.walkingDistanceMeters })
    }

    @Test
    fun returnsSuccessfulModeResultsWhenSomeModesFail() {
        val repository = CitybusBusRouteRepository(clock = { fixedQueryTimestamp }) { url, _ ->
            if (url.queryParam("m1") == "W") {
                routeHtml("788 港元8.7預計29分鐘 步行距離(約)350米")
            } else {
                throw IOException("mode failed")
            }
        }

        val routes = repository.searchRoutes(origin, destination)

        assertEquals(listOf("788"), routes.map { it.routeName })
    }

    @Test
    fun returnsOtherModeResultsWhenOneModeHasNoValidCandidates() {
        val repository = CitybusBusRouteRepository(clock = { fixedQueryTimestamp }) { url, _ ->
            when (url.queryParam("m1")) {
                "T" -> routeHtml()
                "F" -> routeHtml("8X 港元8.1 至 10 免費 *預計74分鐘 步行距離(約)355米")
                "W" -> routeHtml("788 港元8.7預計29分鐘 步行距離(約)350米")
                else -> error("Unexpected m1")
            }
        }

        val routes = repository.searchRoutes(origin, destination)

        assertEquals(listOf("788", "8X \u2192 10"), routes.map { it.routeName })
    }

    @Test
    fun returnsEmptyWhenAllModesHaveNoValidCandidates() {
        val repository = CitybusBusRouteRepository(clock = { fixedQueryTimestamp }) { _, _ ->
            routeHtml()
        }

        val routes = repository.searchRoutes(origin, destination)

        assertEquals(emptyList<Any>(), routes)
    }

    @Test
    fun throwsWhenAllModesFail() {
        val repository = CitybusBusRouteRepository(clock = { fixedQueryTimestamp }) { _, _ ->
            throw IOException("mode failed")
        }

        assertThrows(IOException::class.java) {
            repository.searchRoutes(origin, destination)
        }
    }

    private fun routeHtml(vararg labels: String): String {
        return labels.joinToString(
            separator = "\n",
            prefix = "<div id=\"routelist2\">\n",
            postfix = "\n</div>"
        ) { label -> "<table aria-label=\"$label\"></table>" }
    }

    private fun URL.queryParam(name: String): String {
        return query.split("&")
            .first { it.startsWith("$name=") }
            .substringAfter("=")
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
