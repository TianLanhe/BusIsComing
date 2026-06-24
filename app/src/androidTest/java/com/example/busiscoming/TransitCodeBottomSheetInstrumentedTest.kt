package com.example.busiscoming

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.busiscoming.data.local.RouteConfigDbHelper
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.ui.main.MainActivity
import com.example.busiscoming.ui.main.TransitCodeBottomSheet
import com.example.busiscoming.ui.main.TransitCodePaymentLaunchAction
import com.example.busiscoming.ui.main.TransitCodePaymentLaunchOutcome
import com.example.busiscoming.ui.main.TransitCodeLauncher
import com.example.busiscoming.ui.main.ViewUriLauncher
import com.example.busiscoming.ui.main.WechatMiniProgramClient
import com.example.busiscoming.ui.main.WechatMiniProgramLaunchReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransitCodeBottomSheetInstrumentedTest {
    @Before
    fun clearRoutes() {
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
    }

    @Test
    fun showingExperimentalBottomSheetDirectlyDisplaysExperimentCopy() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var sheet: TransitCodeBottomSheet
            scenario.onActivity { activity ->
                sheet = showTransitCodeSheet(activity)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                assertTrue(sheet.isShowing())
                assertTrue(sheet.textSnapshot().contains("實驗性乘車碼入口"))
                assertTrue(sheet.textSnapshot().contains("逐條嘗試微信 SDK 或 AlipayHK 候選入口；不會自動嘗試下一條。"))
                assertTrue(sheet.textSnapshot().contains("最近診斷"))
                sheet.dispose()
            }
        }
    }

    @Test
    fun bottomSheetListsWechatSdkAndAlipayHkTargets() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var sheet: TransitCodeBottomSheet
            scenario.onActivity { activity ->
                sheet = showTransitCodeSheet(activity)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val labels = sheet.textSnapshot()
                listOf(
                    "微信 SDK",
                    "微信 SDK 正式版",
                    "微信 SDK 測試版",
                    "微信 SDK 預覽版",
                    "AlipayHK",
                    "AlipayHK Scheme",
                    "AlipayHK HTTPS",
                    "最近診斷"
                ).forEach { label ->
                    assertTrue("Missing label: $label", labels.contains(label))
                }
                listOf(
                    "微信 jumpWxa",
                    "微信明文 Scheme",
                    "微信首頁 path",
                    "支付寶",
                    "支付寶 appId",
                    "支付寶 H5 render"
                ).forEach { label ->
                    assertFalse("Unexpected label: $label", labels.contains(label))
                }
                sheet.dispose()
            }
        }
    }

    @Test
    fun clickingMainTransitCodeUsesFormalLauncherAndKeepsDisplayedRouteResults() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val paymentLauncher = RecordingPaymentLaunchAction()
            lateinit var before: ResultSnapshot
            scenario.onActivity { activity ->
                setPaymentLauncher(activity, paymentLauncher)
                prepareResults(activity, routes("保留", 4))
                before = snapshot(activity)
            }

            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.transitCodeButton).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                assertEquals(1, paymentLauncher.calls)
                assertFalse(rootText(activity).contains("實驗性乘車碼入口"))
                assertEquals(before, snapshot(activity))
            }
        }
    }

    private fun prepareResults(activity: MainActivity, routes: List<BusRouteOption>) {
        activity.findViewById<View>(R.id.emptyRouteState).visibility = View.GONE
        activity.findViewById<View>(R.id.queryControls).visibility = View.VISIBLE
        activity.findViewById<View>(R.id.resultSection).visibility = View.VISIBLE
        invoke(activity, "showInitialRoutes", arrayOf(List::class.java), routes)
    }

    private fun routes(prefix: String, count: Int): List<BusRouteOption> {
        return (1..count).map { index ->
            BusRouteOption(
                routeName = "$prefix$index",
                routeSegments = listOf("$index"),
                priceHkd = index.toDouble(),
                durationMinutes = index + 10,
                arrivalMinutes = index,
                transferCount = 0,
                walkingDistanceMeters = index * 10
            )
        }
    }

    private fun snapshot(activity: MainActivity): ResultSnapshot {
        return ResultSnapshot(
            emptyStateVisibility = activity.findViewById<View>(R.id.emptyRouteState).visibility,
            queryControlsVisibility = activity.findViewById<View>(R.id.queryControls).visibility,
            resultSectionVisibility = activity.findViewById<View>(R.id.resultSection).visibility,
            resultListVisibility = activity.findViewById<View>(R.id.resultListContainer).visibility,
            sortControlsVisibility = activity.findViewById<View>(R.id.sortControls).visibility,
            routeCount = activity.findViewById<RecyclerView>(R.id.busRouteList).adapter?.itemCount ?: -1,
            summary = activity.findViewById<TextView>(R.id.resultSummaryText).text.toString(),
            updatedAt = activity.findViewById<TextView>(R.id.resultUpdatedAtText).text.toString()
        )
    }

    private fun showTransitCodeSheet(activity: MainActivity): TransitCodeBottomSheet {
        return TransitCodeBottomSheet(
            context = activity,
            launcher = TransitCodeLauncher(
                viewUriLauncher = FakeViewUriLauncher,
                wechatMiniProgramClient = FakeWechatMiniProgramClient
            )
        ).also { it.show() }
    }

    private fun invoke(
        target: Any,
        name: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any
    ) {
        target.javaClass.getDeclaredMethod(name, *parameterTypes).apply {
            isAccessible = true
        }.invoke(target, *args)
    }

    private fun setPaymentLauncher(
        activity: MainActivity,
        launcher: RecordingPaymentLaunchAction
    ) {
        activity.javaClass.getDeclaredField("transitCodePaymentLauncher").apply {
            isAccessible = true
        }.set(activity, launcher)
    }

    private fun rootText(activity: MainActivity): String {
        return collectText(activity.window.decorView.rootView).joinToString("\n")
    }

    private fun collectText(view: View): List<String> {
        return when (view) {
            is TextView -> listOf(view.text.toString())
            is android.view.ViewGroup -> {
                (0 until view.childCount).flatMap { index -> collectText(view.getChildAt(index)) }
            }
            else -> emptyList()
        }
    }

    private data class ResultSnapshot(
        val emptyStateVisibility: Int,
        val queryControlsVisibility: Int,
        val resultSectionVisibility: Int,
        val resultListVisibility: Int,
        val sortControlsVisibility: Int,
        val routeCount: Int,
        val summary: String,
        val updatedAt: String
    )

    private class RecordingPaymentLaunchAction : TransitCodePaymentLaunchAction {
        var calls: Int = 0

        override fun launchTransitCode(): TransitCodePaymentLaunchOutcome {
            calls += 1
            return TransitCodePaymentLaunchOutcome(
                started = true,
                startedTarget = null,
                attempts = emptyList(),
                shouldShowFailureToast = false
            )
        }
    }

    private object FakeViewUriLauncher : ViewUriLauncher {
        override fun resolveActivity(uri: String): String? {
            return "fake.package/FakeActivity"
        }

        override fun start(uri: String) = Unit
    }

    private object FakeWechatMiniProgramClient : WechatMiniProgramClient {
        override fun launch(params: com.example.busiscoming.data.model.WechatMiniProgramParams): WechatMiniProgramLaunchReport {
            return WechatMiniProgramLaunchReport(
                appPackageName = "com.example.busiscoming",
                appSignatureSha256 = "AA:BB",
                wechatPackageVisible = true,
                registerAppResult = true,
                isWXAppInstalled = true,
                wxAppSupportApi = 123,
                isWXAppSupportApi = true,
                sendReqResult = true
            )
        }
    }
}
