package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.Place
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CitybusPlaceSearchRepository(
    private val parser: CitybusPlaceParser = CitybusPlaceParser,
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

    fun buildSearchUrl(keyword: String, timestamp: Long): URL {
        val encodedKeyword = URLEncoder.encode(keyword, Charsets.UTF_8.name())
        return URL(
            "$BASE_URL?l=0&q=$encodedKeyword&limit=100&timestamp=$timestamp"
        )
    }

    fun requestHeaders(): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "Accept-Language" to "zh-CN,zh;q=0.9",
        "Connection" to "keep-alive",
        "Cookie" to CITYBUS_COOKIE,
        "Referer" to "https://mobile.citybus.com.hk/nwp3/",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin",
        "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36",
        "X-Requested-With" to "XMLHttpRequest",
        "sec-ch-ua" to "\"Chromium\";v=\"148\", \"Google Chrome\";v=\"148\", \"Not/A)Brand\";v=\"99\"",
        "sec-ch-ua-mobile" to "?0",
        "sec-ch-ua-platform" to "\"macOS\""
    )

    companion object {
        private const val BASE_URL = "https://mobile.citybus.com.hk/nwp3/bsearch_p3.php"
        private const val TIMEOUT_MS = 10_000
        private const val CITYBUS_COOKIE =
            "ETWEBID=6a1ecbeae8d60; PPFARE=1; LANG=TC; PHPSESSID=ev7984lo8uibj4kc3njbroe1a1; " +
                "6a1ecbeae877d=d390nlreb7ht18na2b0rser0p4; " +
                "__gads=ID=eb0f3fa592b3fb75:T=1780403182:RT=1780403182:S=ALNI_MbRV20ICxcKynl5JHsVVzonsmksRg; " +
                "__gpi=UID=000014428d894430:T=1780403182:RT=1780403182:S=ALNI_MZPUJxgA6yobYJEFzH6ZlTYJCvQPQ; " +
                "__eoi=ID=7a95018b76510f87:T=1780403182:RT=1780403182:S=AA-AfjZ0fHzxpoZ9Md5XFTPKrNaP; " +
                "FCCDCF=%5Bnull%2Cnull%2Cnull%2Cnull%2Cnull%2Cnull%2C%5B%5B32%2C%22%5B%5C%22f897bbf6-4b5c-4104-b988-23436f7e52a3%5C%22%2C%5B1780403183%2C593000000%5D%5D%22%5D%5D%5D; " +
                "FCNEC=%5B%5B%22AKsRol-vI493XkrY6BdozwK6Zzo0kHea2mP7U7t1uRYa1Oxoe2DvhTjl9jqZcYe99qqTASH2soAL2TF26nHoFYWErzON7fnHdgzFv05zqtKAGptTV1-GPtlQc1lAWF23mLnk3pHgrkcbz3IZfOaMmyhRO1VieNQfcw%3D%3D%22%5D%5D"
    }
}
