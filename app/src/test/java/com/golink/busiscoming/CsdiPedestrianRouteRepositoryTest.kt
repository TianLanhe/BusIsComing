package com.golink.busiscoming

import com.golink.busiscoming.data.model.PedestrianCoordinate
import com.golink.busiscoming.data.model.PedestrianRouteRounding
import com.golink.busiscoming.data.repository.CsdiPedestrianFailureKind
import com.golink.busiscoming.data.repository.CsdiPedestrianParseException
import com.golink.busiscoming.data.repository.CsdiPedestrianRequest
import com.golink.busiscoming.data.repository.CsdiPedestrianRequestBuilder
import com.golink.busiscoming.data.repository.CsdiPedestrianResponse
import com.golink.busiscoming.data.repository.CsdiPedestrianRouteParser
import com.golink.busiscoming.data.repository.HttpCsdiPedestrianRouteSource
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CsdiPedestrianRouteRepositoryTest {
    private val start = PedestrianCoordinate(latitude = 22.30000, longitude = 114.10000)
    private val end = PedestrianCoordinate(latitude = 22.30100, longitude = 114.10100)

    @Test
    fun requestBuilderUsesFixedLanguageIndependentWgs84Contract() {
        val request = CsdiPedestrianRequest(start, end)
        val url = CsdiPedestrianRequestBuilder.buildUrl(request)
        val parameters = queryParameters(url.query)

        assertEquals("https", url.protocol)
        assertEquals("mapapi.hkmapservice.gov.hk", url.host)
        assertEquals("/PedRoute/NAServer/route/solve", url.path)
        assertEquals("3", parameters["travelMode"])
        assertEquals("esriNAUMeters", parameters["directionsLengthUnits"])
        assertEquals("en", parameters["directionsLanguage"])
        assertEquals("4326", parameters["outSR"])
        assertEquals("json", parameters["f"])
        assertEquals("true", parameters["returnZ"])
        assertEquals("NA Campus", parameters["directionStyleName"])
        assertEquals(
            setOf(
                "stops",
                "travelMode",
                "directionsLengthUnits",
                "directionsLanguage",
                "outSR",
                "f",
                "returnZ",
                "directionStyleName"
            ),
            parameters.keys
        )

        val features = JSONObject(parameters.getValue("stops")).getJSONArray("features")
        assertEquals(2, features.length())
        val startFeature = features.getJSONObject(0)
        val endFeature = features.getJSONObject(1)
        assertEquals("Start", startFeature.getJSONObject("attributes").getString("Name"))
        assertEquals(114.10000, startFeature.getJSONObject("geometry").getDouble("x"), 0.0)
        assertEquals(22.30000, startFeature.getJSONObject("geometry").getDouble("y"), 0.0)
        assertEquals("End", endFeature.getJSONObject("attributes").getString("Name"))
        assertEquals(114.10100, endFeature.getJSONObject("geometry").getDouble("x"), 0.0)
        assertEquals(22.30100, endFeature.getJSONObject("geometry").getDouble("y"), 0.0)
        assertEquals(setOf("x", "y"), jsonKeys(startFeature.getJSONObject("geometry")))
        assertEquals(setOf("x", "y"), jsonKeys(endFeature.getJSONObject("geometry")))
    }

    @Test
    fun requestKeyRoundsToSixDecimalsAndPreservesDirectionWithoutLanguage() {
        val forward = CsdiPedestrianRequest(
            start = PedestrianCoordinate(22.30000049, 114.10000051),
            end = PedestrianCoordinate(22.30100051, 114.10100049)
        ).key
        val sameRoundedCoordinates = CsdiPedestrianRequest(
            start = PedestrianCoordinate(22.30000040, 114.10000054),
            end = PedestrianCoordinate(22.30100054, 114.10100040)
        ).key
        val reverse = CsdiPedestrianRequest(end, start).key

        assertEquals(forward, sameRoundedCoordinates)
        assertEquals("22.300000,114.100001->22.301001,114.101000;travelMode=3", forward.toString())
        assertNotEquals(forward, reverse)
    }

    @Test
    fun parserKeepsEveryPathBoundaryAndIgnoresZValues() {
        val result = CsdiPedestrianRouteParser.parse(
            body = resourceText("csdi/pedestrian-valid-multi-z.json"),
            request = CsdiPedestrianRequest(start, end)
        )

        assertEquals(200.2, result.rawDistanceMeters, 0.0)
        assertEquals(3.34, result.rawTimeMinutes, 0.0)
        assertEquals(2, result.paths.size)
        assertEquals(2, result.paths[0].points.size)
        assertEquals(PedestrianCoordinate(22.30000, 114.10000), result.paths[0].points.first())
        assertEquals(PedestrianCoordinate(22.30100, 114.10100), result.paths[1].points.last())
        assertNotEquals(result.paths[0].points.last(), result.paths[1].points.first())
    }

    @Test
    fun parserAcceptsEndpointDeviationInsideThirtyMeters() {
        val result = CsdiPedestrianRouteParser.parse(
            body = resourceText("csdi/pedestrian-valid-single.json"),
            request = CsdiPedestrianRequest(start, end)
        )

        assertEquals(1, result.paths.size)
        assertEquals(123.01, result.rawDistanceMeters, 0.0)
    }

    @Test
    fun parserRejectsMissingNonFiniteEmptyAndEndpointMismatchResponses() {
        val cases = listOf(
            "csdi/pedestrian-missing-field.json" to CsdiPedestrianFailureKind.INVALID_RESPONSE,
            "csdi/pedestrian-non-finite.json" to CsdiPedestrianFailureKind.INVALID_RESPONSE,
            "csdi/pedestrian-empty-route.json" to CsdiPedestrianFailureKind.NO_ROUTE,
            "csdi/pedestrian-empty-path.json" to CsdiPedestrianFailureKind.INVALID_RESPONSE,
            "csdi/pedestrian-endpoint-outside.json" to CsdiPedestrianFailureKind.ENDPOINT_MISMATCH
        )

        cases.forEach { (fixture, expectedKind) ->
            val exception = assertThrows(CsdiPedestrianParseException::class.java) {
                CsdiPedestrianRouteParser.parse(
                    body = resourceText(fixture),
                    request = CsdiPedestrianRequest(start, end)
                )
            }
            assertEquals(expectedKind, exception.failureKind)
        }
    }

    @Test
    fun roundingSumsRawRouteDistancesBeforeCeilingEachPresentationValue() {
        assertEquals(101, PedestrianRouteRounding.totalDistanceMeters(listOf(50.1, 50.1)))
        assertEquals(51, PedestrianRouteRounding.segmentDistanceMeters(50.1))
        assertEquals(1, PedestrianRouteRounding.segmentMinutes(0.01))
        assertEquals(3, PedestrianRouteRounding.segmentMinutes(2.01))
        assertThrows(IllegalArgumentException::class.java) {
            PedestrianRouteRounding.totalDistanceMeters(listOf(10.0, Double.NaN))
        }
    }

    @Test
    fun httpSourceEnforcesOneDeadlineAcrossTheWholeAttempt() {
        val connection = BlockingHttpURLConnection()
        val source = HttpCsdiPedestrianRouteSource(
            connectionFactory = { connection },
            attemptTimeoutMillis = 30
        )

        val startedAt = System.nanoTime()
        val response = source.solve(CsdiPedestrianRequest(start, end))
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertEquals(
            CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.TIMEOUT),
            response
        )
        assertTrue("The whole attempt should stop at its shared deadline", elapsedMillis < 1_000)
        assertTrue(connection.wasDisconnected.get())
    }

    private fun queryParameters(query: String): Map<String, String> =
        query.split('&').associate { pair ->
            val (key, value) = pair.split('=', limit = 2)
            URLDecoder.decode(key, Charsets.UTF_8.name()) to
                URLDecoder.decode(value, Charsets.UTF_8.name())
        }

    private fun jsonKeys(json: JSONObject): Set<String> = buildSet {
        val keys = json.keys()
        while (keys.hasNext()) add(keys.next())
    }

    private fun resourceText(path: String): String {
        return requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Missing resource $path"
        }.bufferedReader().use { it.readText() }
    }

    private class BlockingHttpURLConnection : HttpURLConnection(URL("https://example.test")) {
        private val disconnected = CountDownLatch(1)
        val wasDisconnected = AtomicBoolean(false)

        override fun getResponseCode(): Int {
            disconnected.await(2, TimeUnit.SECONDS)
            throw IOException("Disconnected")
        }

        override fun disconnect() {
            wasDisconnected.set(true)
            disconnected.countDown()
        }

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit
    }
}
