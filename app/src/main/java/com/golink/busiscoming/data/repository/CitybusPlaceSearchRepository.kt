package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.localization.AppLanguageRuntime
import com.golink.busiscoming.data.localization.LanguageSnapshot
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CitybusPlaceSearchRepository(
    private val parser: CitybusPlaceParser = CitybusPlaceParser,
    private val languageSnapshotProvider: () -> LanguageSnapshot = AppLanguageRuntime::snapshot,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : PlaceSearchRepository {
    override fun searchPlaces(keyword: String): List<Place> {
        val normalizedKeyword = keyword.trim()
        if (normalizedKeyword.isBlank()) return emptyList()

        val connection = buildSearchUrl(normalizedKeyword, clock()).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            requestHeaders().forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }

            val statusCode = connection.responseCode
            val responseBody = if (statusCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }

            if (statusCode !in 200..299) {
                throw IOException("Citybus place search failed with HTTP $statusCode")
            }

            parser.parse(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    fun buildSearchUrl(
        keyword: String,
        timestamp: Long,
        language: String = languageSnapshotProvider().citybusLanguage
    ): URL {
        val encodedKeyword = URLEncoder.encode(keyword, Charsets.UTF_8.name())
        return URL(
            "$BASE_URL?l=$language&q=$encodedKeyword&limit=100&timestamp=$timestamp"
        )
    }

    fun requestHeaders(): Map<String, String> = emptyMap()

    companion object {
        private const val BASE_URL = "https://mobile.citybus.com.hk/nwp3/bsearch_p3.php"
        private const val TIMEOUT_MS = 10_000
    }
}
