package com.example.busiscoming

import com.example.busiscoming.data.location.AndroidRequestIdentity
import com.example.busiscoming.data.location.CurrentLocationSnapshot
import com.example.busiscoming.data.location.GoogleReverseGeocodingCache
import com.example.busiscoming.data.location.GoogleReverseGeocodingCacheKey
import com.example.busiscoming.data.location.GoogleReverseGeocodingHttpResponse
import com.example.busiscoming.data.location.GoogleReverseGeocodingParseResult
import com.example.busiscoming.data.location.GoogleReverseGeocodingParser
import com.example.busiscoming.data.location.GoogleReverseGeocodingPlaceNameResolver
import com.example.busiscoming.data.location.GoogleReverseGeocodingRequest
import com.example.busiscoming.data.location.GoogleReverseGeocodingRequestContext
import com.example.busiscoming.data.location.PlaceNameResolutionResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleReverseGeocodingPlaceNameResolverTest {
    private val snapshot = CurrentLocationSnapshot(
        latitude = 22.285978,
        longitude = 114.158697,
        accuracyMeters = 12f,
        elapsedRealtimeMillis = 1_000L
    )
    private val identity = AndroidRequestIdentity(
        packageName = "com.example.busiscoming",
        certificateSha1 = "2E03E4DDAA407EC488CCB3CC68E2C82A80308A95"
    )

    @Test
    fun requestUsesV4PathHeadersFieldMaskLanguageAndRegion() {
        val resolver = resolver()
        val request = resolver.buildRequest(
            snapshot,
            GoogleReverseGeocodingRequestContext(
                apiKey = "test-key",
                languageCode = "en",
                identity = identity
            )
        )

        assertEquals(
            "https://geocode.googleapis.com/v4/geocode/location/22.285978,114.158697?languageCode=en&regionCode=HK",
            request.url.toString()
        )
        assertEquals("test-key", request.headers["X-Goog-Api-Key"])
        assertEquals(
            "results.formattedAddress,results.types,results.addressComponents.longText,results.addressComponents.types",
            request.headers["X-Goog-FieldMask"]
        )
        assertEquals("com.example.busiscoming", request.headers["X-Android-Package"])
        assertEquals(
            "2E03E4DDAA407EC488CCB3CC68E2C82A80308A95",
            request.headers["X-Android-Cert"]
        )
        assertFalse(request.url.query.contains("key"))
        assertFalse(request.url.query.contains("types"))
        assertFalse(request.url.query.contains("granularity"))
    }

    @Test
    fun missingKeyDoesNotFetch() {
        var fetchCount = 0
        val resolver = resolver(
            apiKeyProvider = { " " },
            fetcher = {
                fetchCount += 1
                ok("中環")
            }
        )

        val result = resolver.resolveSynchronously(snapshot)

        assertEquals(PlaceNameResolutionResult.Failure, result)
        assertEquals(0, fetchCount)
    }

    @Test
    fun missingAndroidIdentityDoesNotFetch() {
        var fetchCount = 0
        val resolver = resolver(
            identityProvider = { null },
            fetcher = {
                fetchCount += 1
                ok("中環")
            }
        )

        val result = resolver.resolveSynchronously(snapshot)

        assertEquals(PlaceNameResolutionResult.Failure, result)
        assertEquals(0, fetchCount)
    }

    @Test
    fun parserPrefersStreetAddress() {
        val result = GoogleReverseGeocodingParser.parse(
            resultsJson(
                resultJson("第一個地點", "premise"),
                resultJson("皇后大道中", "street_address")
            )
        )

        assertEquals(GoogleReverseGeocodingParseResult.Success("皇后大道中"), result)
    }

    @Test
    fun parserSkipsCoarseAddressBeforeFallback() {
        val result = GoogleReverseGeocodingParser.parse(
            resultsJson(
                resultJson("香港", "country", "political"),
                resultJson("中環碼頭", "transit_station")
            )
        )

        assertEquals(GoogleReverseGeocodingParseResult.Success("中環碼頭"), result)
    }

    @Test
    fun parserFallsBackToFirstAddressWhenOnlyCoarseExists() {
        val result = GoogleReverseGeocodingParser.parse(
            resultsJson(
                resultJson("香港", "country", "political")
            )
        )

        assertEquals(GoogleReverseGeocodingParseResult.Success("香港"), result)
    }

    @Test
    fun parserFallsBackToAddressComponentsFromSingleResult() {
        val result = GoogleReverseGeocodingParser.parse(
            """
            {
              "results": [
                {
                  "types": ["street_address"],
                  "addressComponents": [
                    {"longText": "遮打道"},
                    {"longText": "中環"},
                    {"longText": "香港"},
                    {"longText": "香港"},
                    {"longText": "額外片段"}
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(GoogleReverseGeocodingParseResult.Success("遮打道，中環，香港，額外片段"), result)
    }

    @Test
    fun parserRejectsPlusCodeAndEmptyResults() {
        val plusCodeOnly = GoogleReverseGeocodingParser.parse(
            resultsJson(resultJson("7JQ5+9F Hong Kong", "plus_code"))
        )
        val empty = GoogleReverseGeocodingParser.parse("""{"results": []}""")

        assertEquals(GoogleReverseGeocodingParseResult.EmptyResults, plusCodeOnly)
        assertEquals(GoogleReverseGeocodingParseResult.EmptyResults, empty)
    }

    @Test
    fun parserReportsApiErrorAndMalformedJson() {
        assertEquals(
            GoogleReverseGeocodingParseResult.ApiError,
            GoogleReverseGeocodingParser.parse("""{"error": {"code": 403}}""")
        )
        assertEquals(
            GoogleReverseGeocodingParseResult.MalformedJson,
            GoogleReverseGeocodingParser.parse("not json")
        )
    }

    @Test
    fun successfulResultIsCachedByE4AndLanguageUntilTtl() {
        var now = 10_000L
        var fetchCount = 0
        val cache = GoogleReverseGeocodingCache()
        val resolver = resolver(
            nowElapsedMillis = { now },
            cache = cache,
            fetcher = {
                fetchCount += 1
                ok("中環")
            }
        )
        val nearbySnapshot = snapshot.copy(latitude = 22.285981, longitude = 114.158699)

        assertEquals(PlaceNameResolutionResult.Success("中環"), resolver.resolveSynchronously(snapshot))
        assertEquals(PlaceNameResolutionResult.Success("中環"), resolver.resolveSynchronously(nearbySnapshot))
        now += GoogleReverseGeocodingCache.CACHE_TTL_MS + 1
        assertEquals(PlaceNameResolutionResult.Success("中環"), resolver.resolveSynchronously(snapshot))

        assertEquals(2, fetchCount)
        assertEquals(
            GoogleReverseGeocodingCacheKey(222860, 1141587, "zh-Hant"),
            GoogleReverseGeocodingCacheKey.from(snapshot, "zh-Hant")
        )
    }

    @Test
    fun failedResultIsNotCached() {
        var fetchCount = 0
        val resolver = resolver(
            fetcher = {
                fetchCount += 1
                if (fetchCount == 1) {
                    GoogleReverseGeocodingHttpResponse(500, "{}")
                } else {
                    ok("中環")
                }
            }
        )

        assertEquals(PlaceNameResolutionResult.Failure, resolver.resolveSynchronously(snapshot))
        assertEquals(PlaceNameResolutionResult.Success("中環"), resolver.resolveSynchronously(snapshot))
        assertEquals(2, fetchCount)
    }

    @Test
    fun sameCacheKeyInFlightRequestsShareOneFetch() {
        val fetchStarted = CountDownLatch(1)
        val releaseFetch = CountDownLatch(1)
        val callbacksDone = CountDownLatch(2)
        val executor = Executors.newSingleThreadExecutor()
        var fetchCount = 0
        val results = mutableListOf<PlaceNameResolutionResult>()
        val resolver = resolver(
            requestExecutor = executor,
            fetcher = {
                fetchCount += 1
                fetchStarted.countDown()
                assertTrue(releaseFetch.await(2, TimeUnit.SECONDS))
                ok("中環")
            }
        )

        resolver.resolve(snapshot) { result ->
            results += result
            callbacksDone.countDown()
        }
        assertTrue(fetchStarted.await(2, TimeUnit.SECONDS))
        resolver.resolve(snapshot.copy(latitude = 22.285981)) { result ->
            results += result
            callbacksDone.countDown()
        }
        releaseFetch.countDown()

        assertTrue(callbacksDone.await(2, TimeUnit.SECONDS))
        assertEquals(1, fetchCount)
        assertEquals(
            listOf(
                PlaceNameResolutionResult.Success("中環"),
                PlaceNameResolutionResult.Success("中環")
            ),
            results
        )
        executor.shutdownNow()
    }

    private fun resolver(
        apiKeyProvider: () -> String = { "test-key" },
        languageCodeProvider: () -> String = { GoogleReverseGeocodingPlaceNameResolver.DEFAULT_LANGUAGE_CODE },
        identityProvider: () -> AndroidRequestIdentity? = { identity },
        fetcher: (GoogleReverseGeocodingRequest) -> GoogleReverseGeocodingHttpResponse = { ok("中環") },
        nowElapsedMillis: () -> Long = { 1_000L },
        requestExecutor: java.util.concurrent.Executor = java.util.concurrent.Executor { runnable -> runnable.run() },
        cache: GoogleReverseGeocodingCache = GoogleReverseGeocodingCache()
    ): GoogleReverseGeocodingPlaceNameResolver {
        return GoogleReverseGeocodingPlaceNameResolver(
            apiKeyProvider = apiKeyProvider,
            languageCodeProvider = languageCodeProvider,
            identityProvider = identityProvider,
            fetcher = fetcher,
            nowElapsedMillis = nowElapsedMillis,
            callbackExecutor = { runnable -> runnable.run() },
            requestExecutor = requestExecutor,
            cache = cache,
            failureLogger = {}
        )
    }

    private fun GoogleReverseGeocodingPlaceNameResolver.resolveSynchronously(
        snapshot: CurrentLocationSnapshot
    ): PlaceNameResolutionResult {
        var result: PlaceNameResolutionResult? = null
        resolve(snapshot) {
            result = it
        }
        return checkNotNull(result)
    }

    private fun ok(address: String): GoogleReverseGeocodingHttpResponse {
        return GoogleReverseGeocodingHttpResponse(
            statusCode = 200,
            body = resultsJson(resultJson(address, "street_address"))
        )
    }

    private fun resultJson(address: String, vararg types: String): String {
        val typeValues = types.joinToString(",") { "\"$it\"" }
        return """{"formattedAddress":"$address","types":[$typeValues]}"""
    }

    private fun resultsJson(vararg results: String): String {
        return """{"results":[${results.joinToString(",")}]}"""
    }
}
