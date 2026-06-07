package com.example.busiscoming

import com.example.busiscoming.data.model.P2pRouteLeg
import com.example.busiscoming.data.model.P2pRoutePlan
import com.example.busiscoming.data.repository.CitybusP2pStopMapParser
import com.example.busiscoming.data.repository.CitybusP2pStopMapResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitybusP2pStopMapResolverTest {
    @Test
    fun parsesSingleLegShowstops2Response() {
        val stopMap = CitybusP2pStopMapParser.parse(
            response = singleLegResponse(),
            rawInfo = SINGLE_RAW_INFO,
            lang = "0",
            plan = singleLegPlan()
        )

        val boarding = stopMap.findStop(0, "8X-THR-1", 6)
        val alighting = stopMap.findStop(0, "8X-THR-1", 20)

        assertEquals("001227", boarding?.stopId)
        assertEquals("樂軒臺", boarding?.displayName)
        assertEquals("001364", alighting?.stopId)
        assertEquals("長康街", alighting?.displayName)
    }

    @Test
    fun parsesMultiLegShowstops2ResponseWithSeparateLegIndexes() {
        val stopMap = CitybusP2pStopMapParser.parse(
            response = multiLegResponse(),
            rawInfo = MULTI_RAW_INFO,
            lang = "0",
            plan = multiLegPlan()
        )

        assertEquals("001227", stopMap.findStop(0, "82X-ISR-1", 6)?.stopId)
        assertEquals("001265", stopMap.findStop(0, "82X-ISR-1", 9)?.stopId)
        assertEquals("001276", stopMap.findStop(1, "102-MEF-1", 12)?.stopId)
        assertEquals("001364", stopMap.findStop(1, "102-MEF-1", 15)?.stopId)
    }

    @Test
    fun returnsEmptyStopMapForInvalidResponse() {
        val stopMap = CitybusP2pStopMapParser.parse(
            response = "<iframe></iframe>",
            rawInfo = SINGLE_RAW_INFO,
            lang = "0",
            plan = singleLegPlan()
        )

        assertTrue(stopMap.stops.isEmpty())
    }

    @Test
    fun buildsShowstops2Url() {
        val url = CitybusP2pStopMapResolver().buildStopMapUrl(SINGLE_RAW_INFO, "0").toString()

        assertTrue(url.startsWith("https://mobile.citybus.com.hk/nwp3/showstops2.php?r="))
        assertTrue(url.contains("8X-THR-1"))
        assertTrue(url.endsWith("&l=0"))
    }

    private fun singleLegPlan(): P2pRoutePlan {
        return P2pRoutePlan(
            rawInfo = SINGLE_RAW_INFO,
            lang = "0",
            legs = listOf(
                P2pRouteLeg("CTB", "8X-THR-1", "8X", 6, 20, "O", "outbound")
            )
        )
    }

    private fun multiLegPlan(): P2pRoutePlan {
        return P2pRoutePlan(
            rawInfo = MULTI_RAW_INFO,
            lang = "0",
            legs = listOf(
                P2pRouteLeg("CTB", "82X-ISR-1", "82X", 6, 9, "O", "outbound"),
                P2pRouteLeg("CTB", "102-MEF-1", "102", 12, 15, "O", "outbound")
            )
        )
    }

    private fun singleLegResponse(): String {
        return """
            <iframe onload="
                addstoponmap('001227',114.24156861053,22.264883822091,'S','6','6 - 樂軒臺, 柴灣道','8X-THR-1','O','N','114.24156861053','22.264883822091');
                addstoponmap('001280',114.19937984053,22.290876932091,'0','19','19 - 新都城大廈, 英皇道','8X-THR-1','O','N','114.19937984053','22.290876932091');
                addstoponmap('001364',114.19594569053,22.290176642091,'E','20','20 - 長康街, 英皇道','8X-THR-1','O','N','114.19594569053','22.290176642091');
            "></iframe>
        """.trimIndent()
    }

    private fun multiLegResponse(): String {
        return """
            <iframe onload="
                addstoponmap('001227',114.24156861053,22.264883822091,'S','6','6 - 樂軒臺, 柴灣道','82X-ISR-1','O','N','114.24156861053','22.264883822091');
                addstoponmap('001265',114.20650947053,22.292090752091,'E','9','9 - 健康村, 英皇道','82X-ISR-1','O','N','114.20650947053','22.292090752091');
                addstoponmap('001276',114.20644476053,22.291904002091,'S','12','12 - 健康村, 英皇道','102-MEF-1','O','N','114.20644476053','22.291904002091');
                addstoponmap('001364',114.19623343053,22.290234382091,'E','15','15 - 長康街, 英皇道','102-MEF-1','O','N','114.19623343053','22.290234382091');
            "></iframe>
        """.trimIndent()
    }

    private companion object {
        private const val SINGLE_RAW_INFO = "1|*|CTB||8X-THR-1||6||20||O|*|"
        private const val MULTI_RAW_INFO = "2|*|CTB||82X-ISR-1||6||9||O|*|CTB||102-MEF-1||12||15||O|*|"
    }
}
