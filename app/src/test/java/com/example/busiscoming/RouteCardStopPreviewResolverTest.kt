package com.example.busiscoming

import com.example.busiscoming.data.repository.CitybusRouteParser
import com.example.busiscoming.data.repository.CitybusRouteStopResolver
import com.example.busiscoming.data.repository.CitybusStopNameResolver
import com.example.busiscoming.data.repository.RouteCardStopPreviewResolver
import java.io.IOException
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteCardStopPreviewResolverTest {
    @Test
    fun buildsRouteStopAndStopUrls() {
        val routeStopResolver = CitybusRouteStopResolver()
        val stopNameResolver = CitybusStopNameResolver()

        assertEquals(
            "https://rt.data.gov.hk/v2/transport/citybus/route-stop/CTB/8X/outbound",
            routeStopResolver.buildRouteStopUrl("CTB", "8X", "outbound").toString()
        )
        assertEquals(
            "https://rt.data.gov.hk/v2/transport/citybus/stop/001227",
            stopNameResolver.buildStopUrl("001227").toString()
        )
    }

    @Test
    fun resolvesPreviewFromFirstBoardingAndLastAlightingStops() {
        val resolver = previewResolver()
        val route = parsedRoute(
            "8X 港元8.1 至 1 免費 *預計93分鐘 步行距離(約)450米",
            "2|*|CTB||8X-THR-1||6||31||O|*|CTB||1-MAF-1||5||15||I|*|"
        )

        val preview = resolver.resolvePreview(route)

        assertEquals("樂軒臺", preview?.boardingStopName)
        assertEquals("中環碼頭", preview?.alightingStopName)
        assertEquals("上車 樂軒臺  \u2192  下車 中環碼頭", preview?.displayText())
    }

    @Test
    fun cachesSuccessfulPreviewForOneDay() {
        var now = 1_000L
        var routeStopCalls = 0
        var stopCalls = 0
        val resolver = previewResolver(
            clock = { now },
            routeStopFetcher = { url ->
                routeStopCalls += 1
                routeStopResponse(url)
            },
            stopFetcher = { url ->
                stopCalls += 1
                stopResponse(url)
            }
        )
        val route = parsedRoute(
            "8X 港元8.1預計45分鐘 步行距離(約)438米",
            "1|*|CTB||8X-THR-1||6||31||O|*|"
        )

        resolver.resolvePreview(route)
        now += 1_000L
        resolver.resolvePreview(route)

        assertEquals(1, routeStopCalls)
        assertEquals(2, stopCalls)
    }

    @Test
    fun refreshesPreviewAfterOneDay() {
        var now = 1_000L
        var routeStopCalls = 0
        val resolver = previewResolver(
            clock = { now },
            routeStopFetcher = { url ->
                routeStopCalls += 1
                routeStopResponse(url)
            }
        )
        val route = parsedRoute(
            "8X 港元8.1預計45分鐘 步行距離(約)438米",
            "1|*|CTB||8X-THR-1||6||31||O|*|"
        )

        resolver.resolvePreview(route)
        now += 86_400_001L
        resolver.resolvePreview(route)

        assertEquals(2, routeStopCalls)
    }

    @Test
    fun doesNotCacheFailedPreview() {
        var routeStopCalls = 0
        val resolver = previewResolver(
            routeStopFetcher = {
                routeStopCalls += 1
                throw IOException("route-stop failed")
            }
        )
        val route = parsedRoute(
            "8X 港元8.1預計45分鐘 步行距離(約)438米",
            "1|*|CTB||8X-THR-1||6||31||O|*|"
        )

        assertNull(resolver.resolvePreview(route))
        assertNull(resolver.resolvePreview(route))

        assertEquals(2, routeStopCalls)
    }

    @Test
    fun isolatesStopNameCacheByLanguage() {
        var stopCalls = 0
        val stopNameResolver = CitybusStopNameResolver(
            stopFetcher = {
                stopCalls += 1
                """{"data":{"stop":"001227","name_tc":"樂軒臺","name_en":"Lok Hin Terrace"}}"""
            }
        )

        assertEquals("樂軒臺", stopNameResolver.resolveStopName("CTB", "001227", "0"))
        assertEquals("Lok Hin Terrace", stopNameResolver.resolveStopName("CTB", "001227", "1"))

        assertEquals(2, stopCalls)
    }

    private fun previewResolver(
        clock: () -> Long = { 1_000L },
        routeStopFetcher: (URL) -> String = ::routeStopResponse,
        stopFetcher: (URL) -> String = ::stopResponse
    ): RouteCardStopPreviewResolver {
        return RouteCardStopPreviewResolver(
            routeStopResolver = CitybusRouteStopResolver(clock = clock, routeStopFetcher = routeStopFetcher),
            stopNameResolver = CitybusStopNameResolver(clock = clock, stopFetcher = stopFetcher),
            clock = clock
        )
    }

    private fun parsedRoute(label: String, rawInfo: String) = CitybusRouteParser.parse(
        """
        <div id="routelist2">
            <table aria-label="$label" onclick="showroutep2p('$rawInfo','0','12:00|*|30');"></table>
        </div>
        """.trimIndent()
    ).first()

    companion object {
        private fun routeStopResponse(url: URL): String {
            return when {
                url.toString().contains("/8X/outbound") -> {
                    """{"data":[{"co":"CTB","route":"8X","dir":"O","seq":6,"stop":"001227"},{"co":"CTB","route":"8X","dir":"O","seq":31,"stop":"009999"}]}"""
                }
                url.toString().contains("/1/inbound") -> {
                    """{"data":[{"co":"CTB","route":"1","dir":"I","seq":15,"stop":"002222"}]}"""
                }
                else -> """{"data":[]}"""
            }
        }

        private fun stopResponse(url: URL): String {
            return when {
                url.toString().endsWith("/001227") -> {
                    """{"data":{"stop":"001227","name_tc":"樂軒臺, 柴灣道","name_en":"Lok Hin Terrace"}}"""
                }
                url.toString().endsWith("/009999") -> {
                    """{"data":{"stop":"009999","name_tc":"健康村","name_en":"Healthy Village"}}"""
                }
                url.toString().endsWith("/002222") -> {
                    """{"data":{"stop":"002222","name_tc":"中環碼頭","name_en":"Central Pier"}}"""
                }
                else -> """{"data":{}}"""
            }
        }
    }
}
