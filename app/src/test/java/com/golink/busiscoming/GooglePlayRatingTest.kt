package com.golink.busiscoming

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import com.golink.busiscoming.data.localization.AppLanguage
import com.golink.busiscoming.data.model.InitialInstallChannel
import com.golink.busiscoming.data.update.GooglePlayRatingLinks
import com.golink.busiscoming.data.update.GooglePlayRatingNavigator
import com.golink.busiscoming.data.update.PlayStoreAvailability
import com.golink.busiscoming.data.update.PlayStoreAvailabilityDetector
import com.golink.busiscoming.data.update.PlayStoreEnvironment
import com.golink.busiscoming.data.update.PlayStorePackageState
import com.golink.busiscoming.data.update.RatingExternalAction
import com.golink.busiscoming.data.update.RatingExternalTarget
import com.golink.busiscoming.data.update.InitialInstallChannelDetector
import com.golink.busiscoming.data.update.InstallSourceReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePlayRatingTest {
    @Test
    fun `detector distinguishes available disabled missing and unusable`() {
        assertEquals(
            PlayStoreAvailability.AVAILABLE,
            detector(PlayStorePackageState.ENABLED, resolves = true).detect()
        )
        assertEquals(
            PlayStoreAvailability.DISABLED,
            detector(PlayStorePackageState.DISABLED, resolves = true).detect()
        )
        assertEquals(
            PlayStoreAvailability.MISSING,
            detector(PlayStorePackageState.MISSING, resolves = true).detect()
        )
        assertEquals(
            PlayStoreAvailability.UNUSABLE,
            detector(PlayStorePackageState.ENABLED, resolves = false).detect()
        )
        assertEquals(
            PlayStoreAvailability.UNUSABLE,
            PlayStoreAvailabilityDetector(object : PlayStoreEnvironment {
                override fun packageState(): PlayStorePackageState = throw SecurityException()
                override fun canResolveProductPage(): Boolean = true
            }).detect()
        )
    }

    @Test
    fun `non Play install source does not disqualify an available Play Store`() {
        val installChannel = InitialInstallChannelDetector.detect(object : InstallSourceReader {
            override fun installerPackageName(): String = "example.other.store"
        })

        assertEquals(InitialInstallChannel.NON_PLAY, installChannel)
        assertEquals(
            PlayStoreAvailability.AVAILABLE,
            detector(PlayStorePackageState.ENABLED, resolves = true).detect()
        )
    }

    @Test
    fun `rating product page is package restricted and has no browser fallback`() {
        val attempts = mutableListOf<RatingExternalTarget>()
        val navigator = GooglePlayRatingNavigator()

        assertFalse(
            navigator.openProductPage(context()) { _, target ->
                attempts += target
                throw ActivityNotFoundException()
            }
        )

        assertEquals(1, attempts.size)
        assertEquals(RatingExternalAction.PRODUCT_PAGE, attempts.single().action)
        assertEquals("com.android.vending", attempts.single().packageName)
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.golink.busiscoming",
            attempts.single().url
        )
    }

    @Test
    fun `recovery targets and official language links are centralized`() {
        val navigator = GooglePlayRatingNavigator()
        val targets = mutableListOf<RatingExternalTarget>()
        val starter: (Context, RatingExternalTarget) -> Unit = { _, target -> targets += target }

        assertTrue(navigator.openPlayAppSettings(context(), starter))
        assertTrue(navigator.openAppSettings(context(), starter))
        assertTrue(navigator.openOfficialHelp(context(), AppLanguage.TRADITIONAL_CHINESE, starter))

        assertEquals("package:com.android.vending", targets[0].url)
        assertEquals("package:com.golink.busiscoming", targets[1].url)
        assertEquals(
            "https://support.google.com/googleplay/answer/190860?hl=zh-HK",
            targets[2].url
        )
        assertTrue(GooglePlayRatingLinks.officialHelp(AppLanguage.SIMPLIFIED_CHINESE).endsWith("hl=zh-CN"))
        assertTrue(GooglePlayRatingLinks.officialHelp(AppLanguage.ENGLISH).endsWith("hl=en"))
    }

    @Test
    fun `all rating navigation failures remain recoverable`() {
        val navigator = GooglePlayRatingNavigator()

        assertFalse(navigator.openPlayAppSettings(context()) { _, _ -> throw SecurityException() })
        assertFalse(navigator.openAppSettings(context()) { _, _ -> throw IllegalStateException() })
        assertFalse(
            navigator.openOfficialHelp(context(), AppLanguage.ENGLISH) { _, _ ->
                throw ActivityNotFoundException()
            }
        )
    }

    private fun detector(
        state: PlayStorePackageState,
        resolves: Boolean
    ) = PlayStoreAvailabilityDetector(object : PlayStoreEnvironment {
        override fun packageState(): PlayStorePackageState = state
        override fun canResolveProductPage(): Boolean = resolves
    })

    private fun context(): Context = object : ContextWrapper(null) {
        override fun getPackageName(): String = "com.golink.busiscoming"
    }
}
