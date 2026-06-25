package com.example.busiscoming

import android.Manifest
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.busiscoming.ui.edit.RouteEditActivity
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleReverseGeocodingCurrentPlaceInstrumentedTest {
    private var locationProviderPrepared: Boolean = false

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Test
    fun newRouteCurrentPlaceUsesRealGoogleAddressAndShowsAttribution() {
        assertTrue(
            "缺少 GOOGLE_GEOCODING_API_KEY，無法執行真實 Google API 驗收",
            BuildConfig.GOOGLE_GEOCODING_API_KEY.isNotBlank()
        )
        injectCentralLocationOrFail()

        ActivityScenario.launch(RouteEditActivity::class.java).use {
            injectCentralLocationOrFail()
            val address = waitForOriginAddress(it)

            assertNotEquals("目前位置附近", address)
            assertNotEquals("地址由 Google Maps 提供", address)
            assertFalse("起點地址不應是 plus code: $address", looksLikePlusCode(address))
            it.onActivity { activity ->
                val attribution = activity.findViewById<TextView>(R.id.originAttributionText)
                assertEquals(View.VISIBLE, attribution.visibility)
            }
        }
    }

    private fun injectCentralLocationOrFail() {
        if (!locationProviderPrepared) {
            runShell("appops set 2000 android:mock_location allow")
            runShell("cmd location set-location-enabled true")
            runShell("cmd location providers remove-test-provider gps")
            runShell(
                "cmd location providers add-test-provider gps " +
                    "--supportsAltitude --supportsSpeed --supportsBearing --powerRequirement 1"
            )
            runShell("cmd location providers set-test-provider-enabled gps true")
            locationProviderPrepared = true
        }
        runShell("cmd location providers set-test-provider-location gps --location 22.285978,114.158697 --accuracy 5")
    }

    private fun waitForOriginAddress(
        scenario: ActivityScenario<RouteEditActivity>,
        timeoutMillis: Long = 12_000L
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var nextLocationInjectionAt = 0L
        var latest = ""
        while (System.currentTimeMillis() < deadline) {
            val now = System.currentTimeMillis()
            if (now >= nextLocationInjectionAt) {
                injectCentralLocationOrFail()
                nextLocationInjectionAt = now + 1_000L
            }
            scenario.onActivity { activity ->
                latest = activity.findViewById<TextView>(R.id.originInput)
                    .text
                    ?.toString()
                    .orEmpty()
                    .trim()
            }
            if (latest.isNotBlank() &&
                latest != "目前位置附近" &&
                latest != "地址由 Google Maps 提供"
            ) {
                return latest
            }
            Thread.sleep(250)
        }
        fail(
            "真實 Google API 驗收未能在 ${timeoutMillis}ms 內填入起點地址；" +
                "請確認模擬器定位、網路、API key、package/cert 限制與 Geocoding API v4 權限"
        )
        return latest
    }

    private fun runShell(command: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).use { input ->
            input.readBytes().decodeToString()
        }
    }

    private fun looksLikePlusCode(value: String): Boolean {
        val candidate = value.trim().substringBefore(",").replace(" ", "")
        return candidate.contains("+") && PLUS_CODE_PATTERN.matches(candidate)
    }

    private companion object {
        val PLUS_CODE_PATTERN = Regex(
            pattern = """^[23456789CFGHJMPQRVWX]{2,8}\+[23456789CFGHJMPQRVWX]{2,}.*$""",
            option = RegexOption.IGNORE_CASE
        )
    }
}
