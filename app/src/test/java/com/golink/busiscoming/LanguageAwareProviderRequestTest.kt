package com.golink.busiscoming

import com.golink.busiscoming.data.localization.AppLanguage
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.localization.LanguageSnapshot
import com.golink.busiscoming.data.location.AndroidRequestIdentity
import com.golink.busiscoming.data.location.CurrentLocationSnapshot
import com.golink.busiscoming.data.location.GoogleReverseGeocodingPlaceNameResolver
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.repository.CitybusBusRouteRepository
import com.golink.busiscoming.data.repository.CitybusPlaceSearchRepository
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageAwareProviderRequestTest {
    private val origin = Place("Origin", 22.28, 114.16)
    private val destination = Place("Destination", 22.31, 114.18)

    @Test
    fun citybusPlaceAndRouteRequestsUseEachSnapshotLanguageAndKeepRequiredParameters() {
        listOf(
            AppLanguage.TRADITIONAL_CHINESE to "0",
            AppLanguage.SIMPLIFIED_CHINESE to "2",
            AppLanguage.ENGLISH to "1"
        ).forEach { (language, expectedCode) ->
            val snapshot = snapshot(language)
            val placeUrl = CitybusPlaceSearchRepository(
                languageSnapshotProvider = { snapshot },
                clock = { 123L }
            ).buildSearchUrl("Central", 123L)
            val placeParams = placeUrl.queryParams()
            assertEquals(expectedCode, placeParams["l"])
            assertEquals("Central", placeParams["q"])
            assertEquals("100", placeParams["limit"])
            assertEquals("123", placeParams["timestamp"])

            val routeUrl = CitybusBusRouteRepository(
                languageSnapshotProvider = { snapshot },
                clock = { 0L }
            ).buildRouteUrl(origin, destination, "2026-07-17 10:00")
            val routeParams = routeUrl.queryParams()
            assertEquals(expectedCode, routeParams["l"])
            assertEquals("1.3", routeParams["ws"])
            assertEquals("2", routeParams["leg"])
            assertEquals("T", routeParams["m1"])
            assertEquals("2026-07-17 10:00", routeParams["t"])
        }
    }

    @Test
    fun googleRequestUsesSnapshotLanguageAndHongKongRegion() {
        listOf(
            AppLanguage.TRADITIONAL_CHINESE to "zh-Hant",
            AppLanguage.SIMPLIFIED_CHINESE to "zh-Hans",
            AppLanguage.ENGLISH to "en"
        ).forEach { (language, expectedCode) ->
            val snapshot = snapshot(language)
            val resolver = GoogleReverseGeocodingPlaceNameResolver(
                apiKeyProvider = { "key" },
                languageSnapshotProvider = { snapshot },
                identityProvider = { AndroidRequestIdentity("com.golink.busiscoming", "AA") }
            )
            val request = resolver.buildRequest(
                CurrentLocationSnapshot(22.281, 114.158, 10f, 1L)
            )
            val params = request.url.queryParams()
            assertEquals(expectedCode, params["languageCode"])
            assertEquals("HK", params["regionCode"])
            assertTrue(request.headers.keys.containsAll(setOf("X-Goog-Api-Key", "X-Goog-FieldMask", "X-Android-Package", "X-Android-Cert")))
        }
    }

    private fun snapshot(language: AppLanguage): LanguageSnapshot =
        LanguageSnapshot.create(AppLanguageChoice.FOLLOW_SYSTEM, language, 4L)

    private fun java.net.URL.queryParams(): Map<String, String> = query
        .split("&")
        .associate { part ->
            val key = part.substringBefore("=")
            key to URLDecoder.decode(part.substringAfter("="), Charsets.UTF_8.name())
        }
}
