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
    private val destination = Place("終點", 22.28851621, 114.19628118)

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
                "&elat=22.28851621" +
                "&elon=114.19628118" +
                "&t=2026-06-03%2012%3A41" +
                "&leg=2" +
                "&m1=T" +
                "&l=0",
            url
        )
        assertFalse(url.contains("ws="))
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
            assertFalse(url.contains("ws="))
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
                "T" -> routeHtml(
                    "788 港元8.7預計35分鐘 步行距離(約)350米",
                    "8X 港元8.1預計45分鐘 步行距離(約)438米"
                )
                "F" -> routeHtml(
                    "8X 港元8.1預計45分鐘 步行距離(約)438米",
                    "8X 港元8.1 至 1 免費 *預計93分鐘 步行距離(約)450米"
                )
                "W" -> routeHtml(
                    "8X 港元8.1 至 1 免費 *預計74分鐘 步行距離(約)355米"
                )
                else -> error("Unexpected m1")
            }
        }

        val routes = repository.searchRoutes(origin, destination)

        assertEquals(setOf("T", "F", "W"), requestedUrls.map { it.queryParam("m1") }.toSet())
        assertEquals(1, requestedUrls.map { it.queryParam("t") }.distinct().size)
        assertEquals(
            listOf("788", "8X", "8X \u2192 1", "8X \u2192 1"),
            routes.map { it.routeName }
        )
        assertEquals(listOf(35, 45, 74, 93), routes.map { it.durationMinutes })
        assertEquals(listOf(350, 438, 355, 450), routes.map { it.walkingDistanceMeters })
        assertEquals(listOf(8.7, 8.1, 8.1, 8.1), routes.map { it.priceHkd })
    }

    @Test
    fun returnsSuccessfulModeResultsWhenSomeModesFail() {
        val repository = CitybusBusRouteRepository(clock = { fixedQueryTimestamp }) { url, _ ->
            if (url.queryParam("m1") == "W") {
                routeHtml("788 港元8.7預計35分鐘 步行距離(約)350米")
            } else {
                throw IOException("mode failed")
            }
        }

        val routes = repository.searchRoutes(origin, destination)

        assertEquals(listOf("788"), routes.map { it.routeName })
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
    }
}
