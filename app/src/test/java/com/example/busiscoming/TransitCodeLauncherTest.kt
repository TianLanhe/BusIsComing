package com.example.busiscoming

import android.content.ActivityNotFoundException
import com.example.busiscoming.data.model.TransitCodeLaunchTargets
import com.example.busiscoming.data.model.TransitCodeProvider
import com.example.busiscoming.data.model.WechatMiniProgramParams
import com.example.busiscoming.ui.main.TransitCodeDiagnosticLogger
import com.example.busiscoming.ui.main.TransitCodeDiagnosticResult
import com.example.busiscoming.ui.main.TransitCodeDiagnosticSink
import com.example.busiscoming.ui.main.TransitCodeLaunchResult
import com.example.busiscoming.ui.main.TransitCodeLaunchStatus
import com.example.busiscoming.ui.main.TransitCodeLauncher
import com.example.busiscoming.ui.main.ViewUriLauncher
import com.example.busiscoming.ui.main.WechatMiniProgramClient
import com.example.busiscoming.ui.main.WechatMiniProgramLaunchReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitCodeLauncherTest {
    private val alipayHkTarget = TransitCodeLaunchTargets.forProvider(TransitCodeProvider.ALIPAY_HK).first()
    private val wechatTarget = TransitCodeLaunchTargets.forProvider(TransitCodeProvider.WECHAT_SDK).first()

    @Test
    fun launchReturnsSuccessAfterUriStarterAcceptsUri() {
        val viewLauncher = FakeViewUriLauncher()
        val diagnostics = RecordingDiagnostics()
        val result = launcher(viewLauncher = viewLauncher, diagnostics = diagnostics).launch(alipayHkTarget)

        assertEquals(TransitCodeLaunchResult.SUCCESS, result)
        assertEquals(alipayHkTarget.uri, viewLauncher.startedUri)
        assertEquals(TransitCodeLaunchStatus.REQUEST_SENT, diagnostics.last?.status)
        assertTrue(diagnostics.last?.details.orEmpty().contains("startActivity=true"))
    }

    @Test
    fun launchMapsActivityNotFoundToUnavailable() {
        val diagnostics = RecordingDiagnostics()
        val result = launcher(
            viewLauncher = FakeViewUriLauncher(error = ActivityNotFoundException("missing")),
            diagnostics = diagnostics
        ).launch(alipayHkTarget)

        assertEquals(TransitCodeLaunchResult.UNAVAILABLE, result)
        assertEquals(TransitCodeLaunchStatus.UNAVAILABLE, diagnostics.last?.status)
    }

    @Test
    fun launchMapsSecurityExceptionToUnavailable() {
        val diagnostics = RecordingDiagnostics()
        val result = launcher(
            viewLauncher = FakeViewUriLauncher(error = SecurityException("blocked")),
            diagnostics = diagnostics
        ).launch(alipayHkTarget)

        assertEquals(TransitCodeLaunchResult.UNAVAILABLE, result)
        assertEquals(TransitCodeLaunchStatus.UNAVAILABLE, diagnostics.last?.status)
    }

    @Test
    fun launchMapsUnexpectedUriExceptionToUnexpectedError() {
        val exception = IllegalStateException("unexpected")
        val diagnostics = RecordingDiagnostics()
        val result = launcher(
            viewLauncher = FakeViewUriLauncher(error = exception),
            diagnostics = diagnostics
        ).launch(alipayHkTarget)

        assertEquals(TransitCodeLaunchResult.UNEXPECTED_ERROR, result)
        assertEquals(TransitCodeLaunchStatus.ERROR, diagnostics.last?.status)
        assertTrue(diagnostics.last?.details.orEmpty().contains("exceptionClass=java.lang.IllegalStateException"))
    }

    @Test
    fun launchReturnsSuccessAfterWechatSendReqSucceeds() {
        val wechatClient = FakeWechatClient(report = wechatReport(sendReqResult = true))
        val diagnostics = RecordingDiagnostics()
        val result = launcher(
            wechatClient = wechatClient,
            diagnostics = diagnostics
        ).launch(wechatTarget)

        assertEquals(TransitCodeLaunchResult.SUCCESS, result)
        assertEquals(wechatTarget.wechatMiniProgramParams, wechatClient.params)
        assertEquals(TransitCodeLaunchStatus.REQUEST_SENT, diagnostics.last?.status)
        assertTrue(diagnostics.last?.details.orEmpty().contains("miniprogramType=MINIPTOGRAM_TYPE_RELEASE/0"))
    }

    @Test
    fun launchMapsWechatRegisterFailureToUnavailable() {
        val diagnostics = RecordingDiagnostics()
        val result = launcher(
            wechatClient = FakeWechatClient(report = wechatReport(registerAppResult = false)),
            diagnostics = diagnostics
        ).launch(wechatTarget)

        assertEquals(TransitCodeLaunchResult.UNAVAILABLE, result)
        assertEquals(TransitCodeLaunchStatus.UNAVAILABLE, diagnostics.last?.status)
    }

    @Test
    fun launchMapsWechatUnsupportedSdkToUnavailable() {
        val diagnostics = RecordingDiagnostics()
        val result = launcher(
            wechatClient = FakeWechatClient(report = wechatReport(isWXAppSupportApi = false)),
            diagnostics = diagnostics
        ).launch(wechatTarget)

        assertEquals(TransitCodeLaunchResult.UNAVAILABLE, result)
        assertEquals(TransitCodeLaunchStatus.UNSUPPORTED, diagnostics.last?.status)
    }

    @Test
    fun launchMapsWechatSendReqFalseToUnavailable() {
        val diagnostics = RecordingDiagnostics()
        val result = launcher(
            wechatClient = FakeWechatClient(report = wechatReport(sendReqResult = false)),
            diagnostics = diagnostics
        ).launch(wechatTarget)

        assertEquals(TransitCodeLaunchResult.UNAVAILABLE, result)
        assertEquals(TransitCodeLaunchStatus.REQUEST_REJECTED, diagnostics.last?.status)
    }

    @Test
    fun launchMapsWechatExceptionToUnexpectedError() {
        val exception = IllegalStateException("unexpected")
        val wechatClient = FakeWechatClient(error = exception)
        val diagnostics = RecordingDiagnostics()
        val result = launcher(
            wechatClient = wechatClient,
            diagnostics = diagnostics
        ).launch(wechatTarget)

        assertEquals(TransitCodeLaunchResult.UNEXPECTED_ERROR, result)
        assertSame(exception, wechatClient.error)
        assertEquals(TransitCodeLaunchStatus.ERROR, diagnostics.last?.status)
    }

    private fun launcher(
        viewLauncher: ViewUriLauncher = FakeViewUriLauncher(),
        wechatClient: WechatMiniProgramClient = FakeWechatClient(),
        diagnostics: RecordingDiagnostics = RecordingDiagnostics()
    ): TransitCodeLauncher {
        return TransitCodeLauncher(
            viewUriLauncher = viewLauncher,
            wechatMiniProgramClient = wechatClient,
            diagnostics = diagnostics,
            logger = NoOpLogger,
            clock = { 123L }
        )
    }

    private fun wechatReport(
        registerAppResult: Boolean = true,
        isWXAppInstalled: Boolean = true,
        isWXAppSupportApi: Boolean = true,
        sendReqResult: Boolean = false
    ): WechatMiniProgramLaunchReport {
        return WechatMiniProgramLaunchReport(
            appPackageName = "com.example.busiscoming",
            appSignatureSha256 = "AA:BB",
            wechatPackageVisible = isWXAppInstalled,
            registerAppResult = registerAppResult,
            isWXAppInstalled = isWXAppInstalled,
            wxAppSupportApi = 123,
            isWXAppSupportApi = isWXAppSupportApi,
            sendReqResult = sendReqResult
        )
    }

    private class FakeViewUriLauncher(
        private val error: Throwable? = null
    ) : ViewUriLauncher {
        var startedUri: String? = null

        override fun resolveActivity(uri: String): String? {
            return "fake.package/FakeActivity"
        }

        override fun start(uri: String) {
            error?.let { throw it }
            startedUri = uri
        }
    }

    private class FakeWechatClient(
        private val report: WechatMiniProgramLaunchReport = WechatMiniProgramLaunchReport(
            appPackageName = "com.example.busiscoming",
            appSignatureSha256 = "AA:BB",
            wechatPackageVisible = true,
            registerAppResult = true,
            isWXAppInstalled = true,
            wxAppSupportApi = 123,
            isWXAppSupportApi = true,
            sendReqResult = true
        ),
        val error: Throwable? = null
    ) : WechatMiniProgramClient {
        var params: WechatMiniProgramParams? = null

        override fun launch(params: WechatMiniProgramParams): WechatMiniProgramLaunchReport {
            this.params = params
            error?.let { throw it }
            return report
        }
    }

    private class RecordingDiagnostics : TransitCodeDiagnosticSink {
        var last: TransitCodeDiagnosticResult? = null

        override fun publish(result: TransitCodeDiagnosticResult) {
            last = result
        }
    }

    private object NoOpLogger : TransitCodeDiagnosticLogger {
        override fun log(result: TransitCodeDiagnosticResult) = Unit
    }
}
