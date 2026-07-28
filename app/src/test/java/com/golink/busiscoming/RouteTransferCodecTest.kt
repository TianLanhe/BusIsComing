package com.golink.busiscoming

import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfig
import com.golink.busiscoming.data.transfer.RouteTransferCodec
import com.golink.busiscoming.data.transfer.RouteTransferError
import com.golink.busiscoming.data.transfer.RouteTransferException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTransferCodecTest {
    private val origin = Place("柴灣站", 22.2642, 114.2371)
    private val destination = Place("中環碼頭", 22.2878, 114.1582)

    @Test
    fun encodeAndDecodeRoundTripPreservesOnlyPortableRouteFields() {
        val route = RouteConfig(
            id = 42,
            name = "  上班  ",
            origin = origin,
            destination = destination,
            usageCount = 99,
            lastUsedAt = 1_725_000_000_000
        )

        val encoded = RouteTransferCodec.encode(
            routes = listOf(route),
            exportedAtUtc = "2026-07-16T10:30:00Z"
        )
        val root = JSONObject(encoded.toString(Charsets.UTF_8))
        val routeJson = root.getJSONArray("routes").getJSONObject(0)

        assertEquals(setOf("format", "version", "exportedAt", "routes"), root.keys().asSequence().toSet())
        assertEquals(setOf("name", "origin", "destination"), routeJson.keys().asSequence().toSet())
        assertEquals(setOf("name", "latitude", "longitude"), routeJson.getJSONObject("origin").keys().asSequence().toSet())
        assertFalse(encoded.toString(Charsets.UTF_8).contains("usageCount"))
        assertFalse(encoded.toString(Charsets.UTF_8).contains("lastUsedAt"))
        assertFalse(encoded.toString(Charsets.UTF_8).contains("\"id\""))
        assertFalse(encoded.toString(Charsets.UTF_8).contains("fingerprint"))
        assertFalse(encoded.toString(Charsets.UTF_8).contains("pinnedAt"))
        assertFalse(encoded.toString(Charsets.UTF_8).contains("route_result_pins"))
        assertFalse(encoded.toString(Charsets.UTF_8).contains("\"token\""))

        val decoded = RouteTransferCodec.decode(encoded)

        assertEquals("2026-07-16T10:30:00Z", decoded.exportedAtUtc)
        assertEquals(0, decoded.duplicateCount)
        assertEquals("上班", decoded.routes.single().name)
        assertEquals(origin, decoded.routes.single().origin)
        assertEquals(destination, decoded.routes.single().destination)
    }

    @Test
    fun decodeRejectsMalformedJsonAndStrictSchemaViolations() {
        assertDecodeError("not json", RouteTransferError.MALFORMED_JSON)
        assertDecodeError(validJson().replace("\"version\":1,", ""), RouteTransferError.INVALID_SCHEMA)
        assertDecodeError(validJson().replace("\"routes\":[", "\"extra\":true,\"routes\":["), RouteTransferError.INVALID_SCHEMA)
        assertDecodeError(validJson().replace("\"name\":\"上班\"", "\"name\":7"), RouteTransferError.INVALID_SCHEMA)
        assertDecodeError(validJson().replace(RouteTransferCodec.FORMAT, "other.format"), RouteTransferError.INVALID_FORMAT)
        assertDecodeError(validJson().replace("\"version\":1", "\"version\":2"), RouteTransferError.UNSUPPORTED_VERSION)
        assertDecodeError(validJson().replace("2026-07-16T10:30:00Z", "16/07/2026"), RouteTransferError.INVALID_SCHEMA)
    }

    @Test
    fun decodeRejectsInvalidRouteContentAndCardinality() {
        assertDecodeError(validJson().replace("\"上班\"", "\"   \""), RouteTransferError.INVALID_ROUTE)
        assertDecodeError(validJson().replace("22.2642", "91.0"), RouteTransferError.INVALID_ROUTE)
        assertDecodeError(validJson().replace("114.2371", "181.0"), RouteTransferError.INVALID_ROUTE)
        assertDecodeError(validJson().replace("22.2642", "\"NaN\""), RouteTransferError.INVALID_SCHEMA)
        assertDecodeError(validJson().replace(originJson(), destinationJson()), RouteTransferError.INVALID_ROUTE)
        assertDecodeError(JSONObject(validJson()).put("routes", org.json.JSONArray()).toString(), RouteTransferError.EMPTY_ROUTES)

        val route = JSONObject(validJson()).getJSONArray("routes").getJSONObject(0).toString()
        val tooManyRoutes = org.json.JSONArray()
        repeat(501) { tooManyRoutes.put(JSONObject(route)) }
        val tooMany = JSONObject(validJson()).put("routes", tooManyRoutes).toString()
        assertDecodeError(tooMany, RouteTransferError.TOO_MANY_ROUTES)

        val maximumRoutes = org.json.JSONArray()
        repeat(500) { maximumRoutes.put(JSONObject(route).put("name", "路線 $it")) }
        assertEquals(
            500,
            RouteTransferCodec.decode(JSONObject(validJson()).put("routes", maximumRoutes).toString().toByteArray()).routes.size
        )
    }

    @Test
    fun decodeKeepsFirstExactDuplicateButRetainsDistinctRoutes() {
        val first = JSONObject(validJson()).getJSONArray("routes").getJSONObject(0)
        val exactDuplicate = JSONObject(first.toString())
        val sameEndpointsDifferentName = JSONObject(first.toString()).put("name", "假日")
        val sameNameDifferentDestination = JSONObject(first.toString()).put(
            "destination",
            JSONObject().put("name", "灣仔站").put("latitude", 22.277).put("longitude", 114.173)
        )
        val json = JSONObject(validJson()).put(
            "routes",
            org.json.JSONArray()
                .put(first)
                .put(exactDuplicate)
                .put(sameEndpointsDifferentName)
                .put(sameNameDifferentDestination)
        )

        val decoded = RouteTransferCodec.decode(json.toString().toByteArray())

        assertEquals(1, decoded.duplicateCount)
        assertEquals(3, decoded.routes.size)
        assertEquals(listOf("上班", "假日", "上班"), decoded.routes.map { it.name })
    }

    @Test
    fun bundledFixturesCoverValidAndInvalidFiles() {
        val valid = requireNotNull(javaClass.getResourceAsStream("/route-transfer/valid-v1.bicroutes")).use { it.readBytes() }
        val invalid = requireNotNull(javaClass.getResourceAsStream("/route-transfer/invalid-unknown-field.bicroutes")).use { it.readBytes() }

        assertEquals(2, RouteTransferCodec.decode(valid).routes.size)
        try {
            RouteTransferCodec.decode(invalid)
            throw AssertionError("Expected invalid fixture to fail")
        } catch (error: RouteTransferException) {
            assertEquals(RouteTransferError.INVALID_SCHEMA, error.error)
        }
    }

    private fun assertDecodeError(json: String, expected: RouteTransferError) {
        try {
            RouteTransferCodec.decode(json.toByteArray())
            throw AssertionError("Expected $expected")
        } catch (error: RouteTransferException) {
            assertEquals(expected, error.error)
        }
    }

    private fun validJson(): String = """
        {
          "format":"${RouteTransferCodec.FORMAT}",
          "version":1,
          "exportedAt":"2026-07-16T10:30:00Z",
          "routes":[{
            "name":"上班",
            "origin":${originJson()},
            "destination":${destinationJson()}
          }]
        }
    """.trimIndent()

    private fun originJson(): String = """{"name":"柴灣站","latitude":22.2642,"longitude":114.2371}"""

    private fun destinationJson(): String = """{"name":"中環碼頭","latitude":22.2878,"longitude":114.1582}"""
}
