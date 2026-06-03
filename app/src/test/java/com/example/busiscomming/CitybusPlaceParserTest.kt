package com.example.busiscomming

import com.example.busiscomming.data.repository.CitybusPlaceParseException
import com.example.busiscomming.data.repository.CitybusPlaceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CitybusPlaceParserTest {
    @Test
    fun parsesNormalPlaceRows() {
        val response = """
            -- 關鍵字搜索 --|-- 關鍵字搜索 --|0|0
            獅子會自然教育中心展覽館<br>
            LIONS NATURE EDUCATION CENTRE EXHIBITION HALLS AND INFORMATION ROOM|獅子會自然教育中心展覽館|22.373935039800|114.263999242800
            會展站<br>
            Exhibition Centre Station|會展站|22.281604205483|114.174971227790
        """.trimIndent()

        val places = CitybusPlaceParser.parse(response)

        assertEquals(2, places.size)
        assertEquals("獅子會自然教育中心展覽館", places[0].name)
        assertEquals(22.373935039800, places[0].latitude, 0.0)
        assertEquals(114.263999242800, places[0].longitude, 0.0)
        assertEquals("會展站", places[1].name)
    }

    @Test
    fun noResultReturnsEmptyList() {
        val response = """
            -- 關鍵字搜索 --|-- 關鍵字搜索 --|0|0
            No Result
        """.trimIndent()

        assertEquals(emptyList<Any>(), CitybusPlaceParser.parse(response))
    }

    @Test
    fun skipsInvalidRowsWhenValidRowsExist() {
        val response = """
            -- 關鍵字搜索 --|-- 關鍵字搜索 --|0|0
            invalid row
            Exhibition Centre Station|會展站|22.281604205483|114.174971227790
        """.trimIndent()

        val places = CitybusPlaceParser.parse(response)

        assertEquals(1, places.size)
        assertEquals("會展站", places.first().name)
    }

    @Test
    fun emptyResponseThrows() {
        assertThrows(CitybusPlaceParseException::class.java) {
            CitybusPlaceParser.parse("")
        }
    }

    @Test
    fun malformedResponseThrows() {
        val response = """
            -- 關鍵字搜索 --|-- 關鍵字搜索 --|0|0
            bad|row|not-a-latitude|114.174971227790
        """.trimIndent()

        assertThrows(CitybusPlaceParseException::class.java) {
            CitybusPlaceParser.parse(response)
        }
    }
}
