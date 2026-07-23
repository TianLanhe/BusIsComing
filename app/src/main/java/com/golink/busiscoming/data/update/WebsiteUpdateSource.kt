package com.golink.busiscoming.data.update

import com.golink.busiscoming.data.model.UpdateChannel
import com.golink.busiscoming.data.model.UpdateFailureKind
import com.golink.busiscoming.data.model.UpdateSnapshot
import com.golink.busiscoming.data.model.UpdateSnapshotState
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import org.json.JSONObject

sealed interface WebsiteUpdateResult {
    data class Available(val snapshot: UpdateSnapshot) : WebsiteUpdateResult
    data class UpToDate(val snapshot: UpdateSnapshot) : WebsiteUpdateResult
    data class Failed(val kind: UpdateFailureKind) : WebsiteUpdateResult
}

object WebsiteMetadataParser {
    fun parse(
        responseUrl: String,
        body: String,
        installedVersionCode: Long,
        checkedAt: Long
    ): WebsiteUpdateResult {
        return try {
            val source = URL(responseUrl)
            if (
                source.protocol != HTTPS || source.host != OFFICIAL_HOST ||
                source.path != METADATA_PATH || !source.hasSafeAuthorityAndSuffix()
            ) {
                return WebsiteUpdateResult.Failed(UpdateFailureKind.INVALID_METADATA)
            }
            val json = JSONObject(body)
            val platform = json.requiredString("platform")
            val status = json.requiredString("status")
            val versionName = json.requiredString("versionName")
            val versionCode = json.requiredLong("versionCode")
            val fileName = json.requiredString("fileName")
            val sizeBytes = json.requiredLong("sizeBytes")
            val lastUpdated = json.requiredString("lastUpdated")
            val downloadUrl = json.requiredString("downloadUrl")
            if (
                platform != "android" || status != "available" ||
                versionCode <= 0L ||
                versionName.isBlank() || !fileName.endsWith(".apk", ignoreCase = true) ||
                sizeBytes <= 0L || !isAllowedDownloadUrl(downloadUrl)
            ) {
                return WebsiteUpdateResult.Failed(UpdateFailureKind.INVALID_METADATA)
            }
            val availableSinceAt = parseHongKongDate(lastUpdated)
                ?: return WebsiteUpdateResult.Failed(UpdateFailureKind.INVALID_METADATA)
            if (versionCode <= installedVersionCode) {
                WebsiteUpdateResult.UpToDate(
                    UpdateSnapshot.upToDate(
                        installedVersionCode = installedVersionCode,
                        channel = UpdateChannel.WEBSITE,
                        checkedAt = checkedAt
                    )
                )
            } else {
                WebsiteUpdateResult.Available(
                    UpdateSnapshot(
                        state = UpdateSnapshotState.UPDATE_AVAILABLE,
                        channel = UpdateChannel.WEBSITE,
                        installedVersionCode = installedVersionCode,
                        availableVersionCode = versionCode,
                        availableVersionName = versionName,
                        availableSinceAt = availableSinceAt,
                        firstSeenAt = checkedAt,
                        checkedAt = checkedAt,
                        flexibleAllowed = false
                    )
                )
            }
        } catch (_: Exception) {
            WebsiteUpdateResult.Failed(UpdateFailureKind.INVALID_METADATA)
        }
    }

    private fun JSONObject.requiredString(key: String): String =
        getString(key).trim().takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Missing $key")

    private fun JSONObject.requiredLong(key: String): Long {
        val value = get(key)
        return when (value) {
            is Byte -> value.toLong()
            is Short -> value.toLong()
            is Int -> value.toLong()
            is Long -> value
            else -> throw IllegalArgumentException("Invalid $key")
        }
    }

    private fun parseHongKongDate(value: String): Long? {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
        }
        val parsed = runCatching { formatter.parse(value) }.getOrNull() ?: return null
        return parsed.takeIf { formatter.format(it) == value }?.time
    }

    private fun isAllowedDownloadUrl(value: String): Boolean {
        if (value == DOWNLOAD_PATH) return true
        val url = runCatching { URL(value) }.getOrNull() ?: return false
        return url.protocol == HTTPS &&
            url.host == OFFICIAL_HOST &&
            url.path == DOWNLOAD_PATH &&
            url.hasSafeAuthorityAndSuffix()
    }

    private fun URL.hasSafeAuthorityAndSuffix(): Boolean =
        userInfo == null &&
            (port == -1 || port == DEFAULT_HTTPS_PORT) &&
            query == null &&
            ref == null

    const val METADATA_URL =
        "https://www.busiscoming.com/api/downloads/android/latest/metadata"
    private const val HTTPS = "https"
    private const val DEFAULT_HTTPS_PORT = 443
    private const val OFFICIAL_HOST = "www.busiscoming.com"
    private const val METADATA_PATH = "/api/downloads/android/latest/metadata"
    private const val DOWNLOAD_PATH = "/api/downloads/android/latest"
}

interface WebsiteUpdateSource {
    fun check(installedVersionCode: Long, checkedAt: Long, callback: (WebsiteUpdateResult) -> Unit)
}

class HttpWebsiteUpdateSource(
    private val requestExecutor: Executor = SHARED_EXECUTOR,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) : WebsiteUpdateSource {
    override fun check(
        installedVersionCode: Long,
        checkedAt: Long,
        callback: (WebsiteUpdateResult) -> Unit
    ) {
        requestExecutor.execute {
            callback(request(installedVersionCode, checkedAt))
        }
    }

    private fun request(installedVersionCode: Long, checkedAt: Long): WebsiteUpdateResult {
        val connection = try {
            connectionFactory(URL(WebsiteMetadataParser.METADATA_URL))
        } catch (_: Exception) {
            return WebsiteUpdateResult.Failed(UpdateFailureKind.NETWORK)
        }
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                WebsiteUpdateResult.Failed(UpdateFailureKind.NETWORK)
            } else if (!connection.getHeaderField("Cache-Control").orEmpty().contains("no-store")) {
                WebsiteUpdateResult.Failed(UpdateFailureKind.INVALID_METADATA)
            } else {
                WebsiteMetadataParser.parse(
                    responseUrl = connection.url.toString(),
                    body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() },
                    installedVersionCode = installedVersionCode,
                    checkedAt = checkedAt
                )
            }
        } catch (_: Exception) {
            WebsiteUpdateResult.Failed(UpdateFailureKind.NETWORK)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val SHARED_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "app-update-website").apply { isDaemon = true }
        }
    }
}
