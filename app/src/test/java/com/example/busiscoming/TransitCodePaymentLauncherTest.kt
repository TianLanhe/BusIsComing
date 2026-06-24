package com.example.busiscoming

import android.content.ActivityNotFoundException
import com.example.busiscoming.data.model.TransitCodePaymentTargets
import com.example.busiscoming.data.model.TransitCodeWalletInstallState
import com.example.busiscoming.ui.main.TransitCodePaymentLaunchLogger
import com.example.busiscoming.ui.main.TransitCodePaymentLaunchOutcome
import com.example.busiscoming.ui.main.TransitCodePaymentLauncher
import com.example.busiscoming.ui.main.TransitCodePaymentPackageDetector
import com.example.busiscoming.ui.main.TransitCodePaymentUriStarter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitCodePaymentLauncherTest {
    @Test
    fun onlyAlipayHkInstalledUsesAlipayHkSchemeThenHttps() {
        assertEquals(
            listOf(
                TransitCodePaymentTargets.ALIPAY_HK_SCHEME_URI,
                TransitCodePaymentTargets.ALIPAY_HK_RENDER_URL
            ),
            TransitCodePaymentTargets.forInstallState(
                TransitCodeWalletInstallState(
                    alipayHkInstalled = true,
                    alipayInstalled = false
                )
            ).map { it.uri }
        )
    }

    @Test
    fun onlyAlipayInstalledUsesAlipaySchemeThenHttps() {
        assertEquals(
            listOf(
                TransitCodePaymentTargets.ALIPAY_SCHEME_URI,
                TransitCodePaymentTargets.ALIPAY_RENDER_URL
            ),
            TransitCodePaymentTargets.forInstallState(
                TransitCodeWalletInstallState(
                    alipayHkInstalled = false,
                    alipayInstalled = true
                )
            ).map { it.uri }
        )
    }

    @Test
    fun bothWalletsInstalledPrioritizesAlipayHkThenAlipay() {
        assertEquals(
            listOf(
                TransitCodePaymentTargets.ALIPAY_HK_SCHEME_URI,
                TransitCodePaymentTargets.ALIPAY_HK_RENDER_URL,
                TransitCodePaymentTargets.ALIPAY_SCHEME_URI,
                TransitCodePaymentTargets.ALIPAY_RENDER_URL
            ),
            TransitCodePaymentTargets.forInstallState(
                TransitCodeWalletInstallState(
                    alipayHkInstalled = true,
                    alipayInstalled = true
                )
            ).map { it.uri }
        )
    }

    @Test
    fun noWalletInstalledUsesAlipayHkHttpsOnly() {
        assertEquals(
            listOf(TransitCodePaymentTargets.ALIPAY_HK_RENDER_URL),
            TransitCodePaymentTargets.forInstallState(
                TransitCodeWalletInstallState(
                    alipayHkInstalled = false,
                    alipayInstalled = false
                )
            ).map { it.uri }
        )
    }

    @Test
    fun launchFallsBackFromAlipayHkSchemeToHttpsWithoutTryingAlipayWhenOnlyAlipayHkInstalled() {
        val starter = RecordingUriStarter(
            failures = mapOf(
                TransitCodePaymentTargets.ALIPAY_HK_SCHEME_URI to ActivityNotFoundException("missing")
            )
        )

        val outcome = launcher(
            installedPackages = setOf(TransitCodePaymentTargets.ALIPAY_HK_PACKAGE_NAME),
            starter = starter
        ).launchTransitCode()

        assertStarted(
            outcome = outcome,
            startedUri = TransitCodePaymentTargets.ALIPAY_HK_RENDER_URL
        )
        assertEquals(
            listOf(
                TransitCodePaymentTargets.ALIPAY_HK_SCHEME_URI,
                TransitCodePaymentTargets.ALIPAY_HK_RENDER_URL
            ),
            starter.startedUris
        )
    }

    @Test
    fun launchFallsBackToAlipayOnlyAfterBothAlipayHkCandidatesFailWhenBothWalletsInstalled() {
        val starter = RecordingUriStarter(
            failures = mapOf(
                TransitCodePaymentTargets.ALIPAY_HK_SCHEME_URI to ActivityNotFoundException("missing"),
                TransitCodePaymentTargets.ALIPAY_HK_RENDER_URL to SecurityException("blocked")
            )
        )

        val outcome = launcher(
            installedPackages = setOf(
                TransitCodePaymentTargets.ALIPAY_HK_PACKAGE_NAME,
                TransitCodePaymentTargets.ALIPAY_PACKAGE_NAME
            ),
            starter = starter
        ).launchTransitCode()

        assertStarted(
            outcome = outcome,
            startedUri = TransitCodePaymentTargets.ALIPAY_SCHEME_URI
        )
        assertEquals(
            listOf(
                TransitCodePaymentTargets.ALIPAY_HK_SCHEME_URI,
                TransitCodePaymentTargets.ALIPAY_HK_RENDER_URL,
                TransitCodePaymentTargets.ALIPAY_SCHEME_URI
            ),
            starter.startedUris
        )
    }

    @Test
    fun launchStopsAfterFirstAcceptedCandidate() {
        val starter = RecordingUriStarter()

        val outcome = launcher(
            installedPackages = setOf(
                TransitCodePaymentTargets.ALIPAY_HK_PACKAGE_NAME,
                TransitCodePaymentTargets.ALIPAY_PACKAGE_NAME
            ),
            starter = starter
        ).launchTransitCode()

        assertStarted(
            outcome = outcome,
            startedUri = TransitCodePaymentTargets.ALIPAY_HK_SCHEME_URI
        )
        assertEquals(listOf(TransitCodePaymentTargets.ALIPAY_HK_SCHEME_URI), starter.startedUris)
    }

    @Test
    fun launchReportsFailureToastWhenEveryCandidateFails() {
        val starter = RecordingUriStarter(
            failures = mapOf(
                TransitCodePaymentTargets.ALIPAY_HK_SCHEME_URI to ActivityNotFoundException("missing"),
                TransitCodePaymentTargets.ALIPAY_HK_RENDER_URL to SecurityException("blocked")
            )
        )

        val outcome = launcher(
            installedPackages = setOf(TransitCodePaymentTargets.ALIPAY_HK_PACKAGE_NAME),
            starter = starter
        ).launchTransitCode()

        assertFalse(outcome.started)
        assertTrue(outcome.shouldShowFailureToast)
        assertNull(outcome.startedTarget)
        assertEquals(
            listOf(
                TransitCodePaymentTargets.ALIPAY_HK_SCHEME_URI,
                TransitCodePaymentTargets.ALIPAY_HK_RENDER_URL
            ),
            starter.startedUris
        )
    }

    @Test
    fun packageDetectionExceptionFallsBackToNoWalletInstalledPlan() {
        val starter = RecordingUriStarter()

        val outcome = TransitCodePaymentLauncher(
            packageDetector = ThrowingPackageDetector,
            uriStarter = starter,
            logger = NoOpPaymentLogger
        ).launchTransitCode()

        assertStarted(
            outcome = outcome,
            startedUri = TransitCodePaymentTargets.ALIPAY_HK_RENDER_URL
        )
        assertEquals(listOf(TransitCodePaymentTargets.ALIPAY_HK_RENDER_URL), starter.startedUris)
    }

    @Test
    fun unexpectedStarterExceptionFallsBackToNextCandidate() {
        val starter = RecordingUriStarter(
            failures = mapOf(
                TransitCodePaymentTargets.ALIPAY_SCHEME_URI to IllegalStateException("unexpected")
            )
        )

        val outcome = launcher(
            installedPackages = setOf(TransitCodePaymentTargets.ALIPAY_PACKAGE_NAME),
            starter = starter
        ).launchTransitCode()

        assertStarted(
            outcome = outcome,
            startedUri = TransitCodePaymentTargets.ALIPAY_RENDER_URL
        )
        assertEquals(
            listOf(
                TransitCodePaymentTargets.ALIPAY_SCHEME_URI,
                TransitCodePaymentTargets.ALIPAY_RENDER_URL
            ),
            starter.startedUris
        )
    }

    private fun launcher(
        installedPackages: Set<String>,
        starter: RecordingUriStarter
    ): TransitCodePaymentLauncher {
        return TransitCodePaymentLauncher(
            packageDetector = FakePackageDetector(installedPackages),
            uriStarter = starter,
            logger = NoOpPaymentLogger
        )
    }

    private fun assertStarted(
        outcome: TransitCodePaymentLaunchOutcome,
        startedUri: String
    ) {
        assertTrue(outcome.started)
        assertFalse(outcome.shouldShowFailureToast)
        assertEquals(startedUri, outcome.startedTarget?.uri)
    }

    private class FakePackageDetector(
        private val installedPackages: Set<String>
    ) : TransitCodePaymentPackageDetector {
        override fun isPackageInstalled(packageName: String): Boolean {
            return installedPackages.contains(packageName)
        }
    }

    private object ThrowingPackageDetector : TransitCodePaymentPackageDetector {
        override fun isPackageInstalled(packageName: String): Boolean {
            throw IllegalStateException("package query failed")
        }
    }

    private class RecordingUriStarter(
        private val failures: Map<String, Throwable> = emptyMap()
    ) : TransitCodePaymentUriStarter {
        val startedUris = mutableListOf<String>()

        override fun start(uri: String) {
            startedUris += uri
            failures[uri]?.let { throw it }
        }
    }

    private object NoOpPaymentLogger : TransitCodePaymentLaunchLogger {
        override fun log(message: String) = Unit
    }
}
