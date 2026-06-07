package com.example.busiscoming

import com.example.busiscoming.data.repository.CitybusP2pStopMapResolver
import com.example.busiscoming.data.repository.CitybusRouteParser
import com.example.busiscoming.data.repository.RouteCardStopPreviewResolver
import java.io.IOException
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteCardStopPreviewResolverTest {
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
        assertEquals("樂軒臺  \u2192  中環碼頭", preview?.displayText())
    }

    @Test
    fun resolvesMisaligned8xAlightingStopFromP2pVariant() {
        val resolver = previewResolver(
            stopMapFetcher = {
                """
                <iframe onload="
                    addstoponmap('001227',114.24156861053,22.264883822091,'S','6','6 - 樂軒臺, 柴灣道','8X-THR-1','O','N','114.24156861053','22.264883822091');
                    addstoponmap('001280',114.19937984053,22.290876932091,'0','19','19 - 新都城大廈, 英皇道','8X-THR-1','O','N','114.19937984053','22.290876932091');
                    addstoponmap('001364',114.19594569053,22.290176642091,'E','20','20 - 長康街, 英皇道','8X-THR-1','O','N','114.19594569053','22.290176642091');
                "></iframe>
                """.trimIndent()
            }
        )
        val route = parsedRoute(
            "8X 港元8.1預計45分鐘 步行距離(約)438米",
            "1|*|CTB||8X-THR-1||6||20||O|*|"
        )

        val preview = resolver.resolvePreview(route)

        assertEquals("樂軒臺", preview?.boardingStopName)
        assertEquals("長康街", preview?.alightingStopName)
    }

    @Test
    fun cachesSuccessfulPreviewForOneDay() {
        var now = 1_000L
        var stopMapCalls = 0
        val resolver = previewResolver(
            clock = { now },
            stopMapFetcher = {
                stopMapCalls += 1
                stopMapResponse()
            }
        )
        val route = parsedRoute(
            "8X 港元8.1預計45分鐘 步行距離(約)438米",
            "1|*|CTB||8X-THR-1||6||31||O|*|"
        )

        resolver.resolvePreview(route)
        now += 1_000L
        resolver.resolvePreview(route)

        assertEquals(1, stopMapCalls)
    }

    @Test
    fun refreshesPreviewAfterOneDay() {
        var now = 1_000L
        var stopMapCalls = 0
        val resolver = previewResolver(
            clock = { now },
            stopMapFetcher = {
                stopMapCalls += 1
                stopMapResponse()
            }
        )
        val route = parsedRoute(
            "8X 港元8.1預計45分鐘 步行距離(約)438米",
            "1|*|CTB||8X-THR-1||6||31||O|*|"
        )

        resolver.resolvePreview(route)
        now += 86_400_001L
        resolver.resolvePreview(route)

        assertEquals(2, stopMapCalls)
    }

    @Test
    fun doesNotCacheFailedPreview() {
        var stopMapCalls = 0
        val resolver = previewResolver(
            stopMapFetcher = {
                stopMapCalls += 1
                throw IOException("showstops2 failed")
            }
        )
        val route = parsedRoute(
            "8X 港元8.1預計45分鐘 步行距離(約)438米",
            "1|*|CTB||8X-THR-1||6||31||O|*|"
        )

        assertNull(resolver.resolvePreview(route))
        assertNull(resolver.resolvePreview(route))

        assertEquals(2, stopMapCalls)
    }

    private fun previewResolver(
        clock: () -> Long = { 1_000L },
        stopMapFetcher: (URL) -> String = { stopMapResponse() }
    ): RouteCardStopPreviewResolver {
        return RouteCardStopPreviewResolver(
            stopMapResolver = CitybusP2pStopMapResolver(
                clock = clock,
                stopMapFetcher = { url, _ -> stopMapFetcher(url) }
            ),
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
        private fun stopMapResponse(): String {
            return """
                <iframe onload="
                    addstoponmap('001227',114.24156861053,22.264883822091,'S','6','6 - 樂軒臺, 柴灣道','8X-THR-1','O','N','114.24156861053','22.264883822091');
                    addstoponmap('009999',114.10000000000,22.200000000000,'E','31','31 - 健康村, 英皇道','8X-THR-1','O','N','114.10000000000','22.200000000000');
                    addstoponmap('002222',114.15000000000,22.280000000000,'E','15','15 - 中環碼頭, 民光街','1-MAF-1','I','N','114.15000000000','22.280000000000');
                "></iframe>
            """.trimIndent()
        }
    }
}
