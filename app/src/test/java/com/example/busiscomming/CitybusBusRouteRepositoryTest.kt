package com.example.busiscomming

import com.example.busiscomming.data.model.Place
import com.example.busiscomming.data.repository.CitybusBusRouteRepository
import org.junit.Assert.assertEquals
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
}
