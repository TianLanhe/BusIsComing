package com.golink.busiscoming

import com.golink.busiscoming.data.localization.AppLanguage
import com.golink.busiscoming.data.model.InitialInstallChannel
import com.golink.busiscoming.data.model.UpdateChannel
import com.golink.busiscoming.data.model.UpdateFailureKind
import com.golink.busiscoming.data.update.AppUpdateLinks
import com.golink.busiscoming.data.update.InitialInstallChannelDetector
import com.golink.busiscoming.data.update.InstallSourceReader
import com.golink.busiscoming.data.update.PlayUpdateResult
import com.golink.busiscoming.data.update.PlayUpdateResultMapper
import com.golink.busiscoming.data.update.UpdateChannelDecision
import com.golink.busiscoming.data.update.UpdateChannelResolver
import com.golink.busiscoming.data.update.WebsiteMetadataParser
import com.golink.busiscoming.data.update.WebsiteUpdateResult
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.UpdateAvailability
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
    fun websiteMetadataParserAcceptsOnlyCompleteOfficialMetadata() {
        val now = hkTime("2026-07-23 12:00:00")
        val result = WebsiteMetadataParser.parse(
            responseUrl = "https://www.busiscoming.com/api/downloads/android/latest/metadata",
            body = validWebsiteMetadata(versionCode = 8),
            installedVersionCode = 6L,
            checkedAt = now
        )

        val available = result as WebsiteUpdateResult.Available
        assertEquals(8L, available.snapshot.availableVersionCode)
        assertEquals(UpdateChannel.WEBSITE, available.snapshot.channel)
        assertEquals(hkTime("2026-07-20 00:00:00"), available.snapshot.availableSinceAt)
    }

    @Test
    fun websiteMetadataParserRejectsMissingAppWrongHostAndInvalidDate() {
        val valid = validWebsiteMetadata(versionCode = 8)
        val cases = listOf(
            valid.replace("\"applicationId\":\"com.golink.busiscoming\",", ""),
            valid.replace("2026-07-20", "20/07/2026"),
            valid.replace("https://www.busiscoming.com/api/downloads/android/latest", "https://evil.example/app.apk"),
            valid.replace("\"versionCode\":8", "\"versionCode\":8.5"),
            valid.replace("\"sizeBytes\":123456", "\"sizeBytes\":123456.5")
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
          "applicationId":"com.golink.busiscoming",
          "versionName":"1.2",
          "versionCode":$versionCode,
          "fileName":"BusIsComing.apk",
          "sizeBytes":123456,
          "lastUpdated":"2026-07-20",
          "downloadUrl":"https://www.busiscoming.com/api/downloads/android/latest"
        }
    """.trimIndent()

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
