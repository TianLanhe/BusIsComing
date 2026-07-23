package com.golink.busiscoming

import com.golink.busiscoming.data.localization.AppLanguage
import com.golink.busiscoming.data.model.InitialInstallChannel
import com.golink.busiscoming.data.model.UpdateChannel
import com.golink.busiscoming.data.model.UpdateFailureKind
import com.golink.busiscoming.data.update.AppUpdateLinks
import com.golink.busiscoming.data.update.HttpWebsiteUpdateSource
import com.golink.busiscoming.data.update.InitialInstallChannelDetector
import com.golink.busiscoming.data.update.InstallSourceReader
import com.golink.busiscoming.data.update.PlayUpdateResult
import com.golink.busiscoming.data.update.PlayUpdateResultMapper
import com.golink.busiscoming.data.update.UpdateChannelDecision
import com.golink.busiscoming.data.update.UpdateChannelResolver
import com.golink.busiscoming.data.update.UpdatePolicy
import com.golink.busiscoming.data.update.WebsiteMetadataParser
import com.golink.busiscoming.data.update.WebsiteUpdateResult
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.UpdateAvailability
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateSourcesTest {
    @Test
    fun installerDetectorHandlesPlayOtherNullAndFailure() {
        assertEquals(
            InitialInstallChannel.PLAY,
            InitialInstallChannelDetector.detect(reader("com.android.vending"))
        )
        assertEquals(
            InitialInstallChannel.NON_PLAY,
            InitialInstallChannelDetector.detect(reader("com.android.packageinstaller"))
        )
        assertEquals(
            InitialInstallChannel.UNKNOWN_NON_PLAY,
            InitialInstallChannelDetector.detect(reader(null))
        )
        assertEquals(
            InitialInstallChannel.UNKNOWN_NON_PLAY,
            InitialInstallChannelDetector.detect(object : InstallSourceReader {
                override fun installerPackageName(): String? = error("package manager failed")
            })
        )
    }

    @Test
    fun playMapperSeparatesAvailableNoUpdateNotOwnedAndTemporaryFailure() {
        assertTrue(
            PlayUpdateResultMapper.success(
                availability = UpdateAvailability.UPDATE_AVAILABLE,
                availableVersionCode = 8,
                stalenessDays = 3,
                flexibleAllowed = true,
                downloaded = false
            ) is PlayUpdateResult.Available
        )
        assertEquals(
            PlayUpdateResult.NotAvailable,
            PlayUpdateResultMapper.success(
                availability = UpdateAvailability.UPDATE_NOT_AVAILABLE,
                availableVersionCode = 6,
                stalenessDays = null,
                flexibleAllowed = false,
                downloaded = false
            )
        )
        assertEquals(
            PlayUpdateResult.AppNotOwned,
            PlayUpdateResultMapper.failure(InstallErrorCode.ERROR_APP_NOT_OWNED)
        )
        assertEquals(
            UpdateFailureKind.PLAY_TEMPORARY,
            (PlayUpdateResultMapper.failure(InstallErrorCode.ERROR_INTERNAL_ERROR)
                as PlayUpdateResult.Failed).kind
        )
    }

    @Test
    fun resolverKeepsPlayAuthorityAndUsesWebsiteOnlyWithoutPlay() {
        val cases = listOf(
            ResolverCase(true, InitialInstallChannel.NON_PLAY, availablePlay(), UpdateChannelDecision.PLAY),
            ResolverCase(true, InitialInstallChannel.NON_PLAY, PlayUpdateResult.NotAvailable, UpdateChannelDecision.PLAY),
            ResolverCase(true, InitialInstallChannel.NON_PLAY, PlayUpdateResult.AppNotOwned, UpdateChannelDecision.PLAY_WITH_WEBSITE_METADATA),
            ResolverCase(true, InitialInstallChannel.NON_PLAY, PlayUpdateResult.Failed(UpdateFailureKind.PLAY_TEMPORARY), UpdateChannelDecision.PLAY_FAILED),
            ResolverCase(false, InitialInstallChannel.NON_PLAY, null, UpdateChannelDecision.WEBSITE),
            ResolverCase(false, InitialInstallChannel.UNKNOWN_NON_PLAY, null, UpdateChannelDecision.WEBSITE),
            ResolverCase(false, InitialInstallChannel.PLAY, null, UpdateChannelDecision.PLAY_UNAVAILABLE)
        )

        cases.forEach { case ->
            assertEquals(
                case.expected,
                UpdateChannelResolver.resolve(
                    playPackageAvailable = case.playAvailable,
                    initialInstallChannel = case.initialChannel,
                    playResult = case.playResult
                )
            )
        }
    }

    @Test
    fun websiteMetadataParserAcceptsDeployedResponseWithoutApplicationIdAndRelativeDownloadUrl() {
        val now = hkTime("2026-07-23 12:00:00")
        val result = WebsiteMetadataParser.parse(
            responseUrl = "https://www.busiscoming.com/api/downloads/android/latest/metadata",
            body = validWebsiteMetadata(versionCode = 8),
            installedVersionCode = 6L,
            checkedAt = now
        )

        val available = result as WebsiteUpdateResult.Available
        assertEquals(8L, available.snapshot.availableVersionCode)
        assertEquals("1.0", available.snapshot.availableVersionName)
        assertEquals(UpdateChannel.WEBSITE, available.snapshot.channel)
        assertEquals(hkTime("2026-07-20 00:00:00"), available.snapshot.availableSinceAt)
    }

    @Test
    fun websiteMetadataParserAlsoAcceptsEquivalentOfficialAbsoluteDownloadUrl() {
        val result = WebsiteMetadataParser.parse(
            responseUrl = "https://www.busiscoming.com/api/downloads/android/latest/metadata",
            body = validWebsiteMetadata(versionCode = 8).replace(
                "\"downloadUrl\":\"/api/downloads/android/latest\"",
                "\"downloadUrl\":\"https://www.busiscoming.com/api/downloads/android/latest\""
            ),
            installedVersionCode = 6L,
            checkedAt = 1L
        )

        assertTrue(result is WebsiteUpdateResult.Available)
    }

    @Test
    fun websiteMetadataParserDoesNotUseOptionalApplicationIdForVersionDecision() {
        val result = WebsiteMetadataParser.parse(
            responseUrl = "https://www.busiscoming.com/api/downloads/android/latest/metadata",
            body = validWebsiteMetadata(versionCode = 8).replace(
                "\"status\":\"available\",",
                "\"status\":\"available\",\"applicationId\":\"untrusted.echo\","
            ),
            installedVersionCode = 6L,
            checkedAt = 1L
        )

        assertTrue(result is WebsiteUpdateResult.Available)
    }

    @Test
    fun websiteMetadataParserRejectsInvalidFieldsAndDownloadLocations() {
        val valid = validWebsiteMetadata(versionCode = 8)
        val cases = listOf(
            valid.replace("\"versionName\":\"1.0\",", ""),
            valid.replace("2026-07-20", "20/07/2026"),
            valid.replace("\"versionCode\":8", "\"versionCode\":8.5"),
            valid.replace("\"sizeBytes\":5889119", "\"sizeBytes\":5889119.5"),
            metadataWithDownloadUrl(valid, "/api/downloads/android/other"),
            metadataWithDownloadUrl(valid, "//evil.example/api/downloads/android/latest"),
            metadataWithDownloadUrl(valid, "http://www.busiscoming.com/api/downloads/android/latest"),
            metadataWithDownloadUrl(valid, "https://evil.example/api/downloads/android/latest"),
            metadataWithDownloadUrl(valid, "https://www.busiscoming.com:444/api/downloads/android/latest"),
            metadataWithDownloadUrl(valid, "https://www.busiscoming.com/api/downloads/android/latest?source=app"),
            metadataWithDownloadUrl(valid, "https://www.busiscoming.com/api/downloads/android/latest#fragment")
        )

        cases.forEach { body ->
            assertTrue(
                WebsiteMetadataParser.parse(
                    responseUrl = "https://www.busiscoming.com/api/downloads/android/latest/metadata",
                    body = body,
                    installedVersionCode = 6L,
                    checkedAt = 1L
                ) is WebsiteUpdateResult.Failed
            )
        }
        assertTrue(
            WebsiteMetadataParser.parse(
                responseUrl = "https://evil.example/metadata",
                body = valid,
                installedVersionCode = 6L,
                checkedAt = 1L
            ) is WebsiteUpdateResult.Failed
        )
    }

    @Test
    fun websiteMetadataParserRejectsAlteredMetadataEndpointUrl() {
        val valid = validWebsiteMetadata(versionCode = 8)
        val responseUrls = listOf(
            "http://www.busiscoming.com/api/downloads/android/latest/metadata",
            "https://evil.example/api/downloads/android/latest/metadata",
            "https://www.busiscoming.com:444/api/downloads/android/latest/metadata",
            "https://www.busiscoming.com/api/downloads/android/latest/metadata?source=app",
            "https://www.busiscoming.com/api/downloads/android/latest/metadata#fragment",
            "https://user@www.busiscoming.com/api/downloads/android/latest/metadata"
        )

        responseUrls.forEach { responseUrl ->
            assertTrue(
                WebsiteMetadataParser.parse(
                    responseUrl = responseUrl,
                    body = valid,
                    installedVersionCode = 6L,
                    checkedAt = 1L
                ) is WebsiteUpdateResult.Failed
            )
        }
    }

    @Test
    fun websiteMetadataAtInstalledVersionIsUpToDate() {
        val result = WebsiteMetadataParser.parse(
            responseUrl = "https://www.busiscoming.com/api/downloads/android/latest/metadata",
            body = validWebsiteMetadata(versionCode = 6),
            installedVersionCode = 6L,
            checkedAt = 1L
        )

        assertTrue(result is WebsiteUpdateResult.UpToDate)
    }

    @Test
    fun deployedWebsiteShapeFeedsVersionAndThreeDayPolicyWithoutWaiting() {
        val availableSince = hkTime("2026-07-20 00:00:00")
        val result = WebsiteMetadataParser.parse(
            responseUrl = "https://www.busiscoming.com/api/downloads/android/latest/metadata",
            body = validWebsiteMetadata(versionCode = 8),
            installedVersionCode = 7L,
            checkedAt = availableSince
        ) as WebsiteUpdateResult.Available

        val times = listOf(
            availableSince + UpdatePolicy.DEFER_INTERVAL_MILLIS - 1L to false,
            availableSince + UpdatePolicy.DEFER_INTERVAL_MILLIS to true,
            availableSince + UpdatePolicy.DEFER_INTERVAL_MILLIS + 1L to true
        )
        times.forEach { (now, expected) ->
            assertEquals(expected, UpdatePolicy { now }.hasReachedReminderAge(
                result.snapshot.availableSinceAt!!
            ))
        }
    }

    @Test
    fun httpWebsiteSourceEnforcesStatusCacheHeaderAndNetworkFailures() {
        val validBody = validWebsiteMetadata(versionCode = 8)

        assertTrue(checkHttpSource(
            FakeHttpURLConnection(
                responseCodeValue = HttpURLConnection.HTTP_OK,
                cacheControl = "private, no-store",
                body = validBody
            )
        ) is WebsiteUpdateResult.Available)
        assertEquals(
            UpdateFailureKind.NETWORK,
            (checkHttpSource(
                FakeHttpURLConnection(
                    responseCodeValue = HttpURLConnection.HTTP_INTERNAL_ERROR,
                    cacheControl = "no-store",
                    body = ""
                )
            ) as WebsiteUpdateResult.Failed).kind
        )
        assertEquals(
            UpdateFailureKind.INVALID_METADATA,
            (checkHttpSource(
                FakeHttpURLConnection(
                    responseCodeValue = HttpURLConnection.HTTP_OK,
                    cacheControl = "max-age=300",
                    body = validBody
                )
            ) as WebsiteUpdateResult.Failed).kind
        )

        var networkFailure: WebsiteUpdateResult? = null
        HttpWebsiteUpdateSource(
            requestExecutor = { it.run() },
            connectionFactory = { throw IllegalStateException("offline") }
        ).check(installedVersionCode = 6L, checkedAt = 1L) {
            networkFailure = it
        }
        assertEquals(
            UpdateFailureKind.NETWORK,
            (networkFailure as WebsiteUpdateResult.Failed).kind
        )
    }

    @Test
    fun websiteUpdatePagesAreFixedPerEffectiveLanguage() {
        assertEquals(
            "https://www.busiscoming.com/zh-hant/#download",
            AppUpdateLinks.websiteDownloadPage(AppLanguage.TRADITIONAL_CHINESE)
        )
        assertEquals(
            "https://www.busiscoming.com/zh-hans/#download",
            AppUpdateLinks.websiteDownloadPage(AppLanguage.SIMPLIFIED_CHINESE)
        )
        assertEquals(
            "https://www.busiscoming.com/en/#download",
            AppUpdateLinks.websiteDownloadPage(AppLanguage.ENGLISH)
        )
    }

    private fun reader(packageName: String?) = object : InstallSourceReader {
        override fun installerPackageName(): String? = packageName
    }

    private fun availablePlay() = PlayUpdateResult.Available(
        availableVersionCode = 8L,
        stalenessDays = 3,
        flexibleAllowed = true,
        downloaded = false
    )

    private fun validWebsiteMetadata(versionCode: Int): String = """
        {
          "platform":"android",
          "status":"available",
          "versionName":"1.0",
          "versionCode":$versionCode,
          "fileName":"BusIsComing.apk",
          "sizeBytes":5889119,
          "lastUpdated":"2026-07-20",
          "downloadUrl":"/api/downloads/android/latest"
        }
    """.trimIndent()

    private fun metadataWithDownloadUrl(metadata: String, downloadUrl: String): String =
        metadata.replace(
            "\"downloadUrl\":\"/api/downloads/android/latest\"",
            "\"downloadUrl\":\"$downloadUrl\""
        )

    private fun checkHttpSource(connection: HttpURLConnection): WebsiteUpdateResult {
        var result: WebsiteUpdateResult? = null
        HttpWebsiteUpdateSource(
            requestExecutor = { it.run() },
            connectionFactory = { connection }
        ).check(installedVersionCode = 6L, checkedAt = 1L) {
            result = it
        }
        return checkNotNull(result)
    }

    private fun hkTime(value: String): Long = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.ROOT
    ).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
    }.parse(value)!!.time

    private data class ResolverCase(
        val playAvailable: Boolean,
        val initialChannel: InitialInstallChannel,
        val playResult: PlayUpdateResult?,
        val expected: UpdateChannelDecision
    )
}

private class FakeHttpURLConnection(
    responseCodeValue: Int,
    private val cacheControl: String?,
    body: String
) : HttpURLConnection(URL(WebsiteMetadataParser.METADATA_URL)) {
    private val responseCodeResult = responseCodeValue
    private val responseBody = body.toByteArray(Charsets.UTF_8)

    override fun connect() = Unit

    override fun disconnect() = Unit

    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int = responseCodeResult

    override fun getHeaderField(name: String?): String? =
        if (name.equals("Cache-Control", ignoreCase = true)) cacheControl else null

    override fun getInputStream(): InputStream = ByteArrayInputStream(responseBody)
}
