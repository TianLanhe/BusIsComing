package com.example.busiscomming

import com.example.busiscomming.data.repository.CitybusPlaceSearchRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitybusPlaceSearchRepositoryTest {
    private val repository = CitybusPlaceSearchRepository(clock = { 1234567890L })

    @Test
    fun buildsSearchUrlWithEncodedKeywordAndTimestamp() {
        val url = repository.buildSearchUrl("會展", 1234567890L).toString()

        assertEquals(
            "https://mobile.citybus.com.hk/nwp3/bsearch_p3.php?l=0&q=%E6%9C%83%E5%B1%95&limit=100&timestamp=1234567890",
            url
        )
    }

    @Test
    fun exposesRequiredRequestHeaders() {
        val headers = repository.requestHeaders()

        assertEquals("*/*", headers["Accept"])
        assertEquals("https://mobile.citybus.com.hk/nwp3/", headers["Referer"])
        assertEquals("XMLHttpRequest", headers["X-Requested-With"])
        assertTrue(headers["User-Agent"].orEmpty().contains("Chrome/148.0.0.0"))
        assertTrue(headers["Cookie"].orEmpty().contains("ETWEBID=6a1ecbeae8d60"))
    }
}
