package com.golink.busiscoming

import android.content.Context
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.EtaArrival
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.ui.main.EtaArrivalsBottomSheet
import com.golink.busiscoming.ui.main.MainActivity
import org.hamcrest.Matchers.instanceOf
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EtaArrivalsBottomSheetInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @After
    fun restoreEnvironment() {
        executeShell("settings put system font_scale 1.0")
        executeShell("wm size reset")
        executeShell("wm density reset")
        AppThemePreferenceStore(context).setMode(AppThemeMode.SYSTEM)
        AppCompatDelegate.setDefaultNightMode(AppThemeMode.SYSTEM.nightMode)
        AppLanguageRepository(context).setChoice(AppLanguageChoice.TRADITIONAL_CHINESE)
        executeShell("cmd locale set-app-locales ${context.packageName} --locales zh-Hant")
    }

    @Test
    fun fixedHeaderScrollableFullListAndOperatorBadgesFitRequestedMatrix() {
        val cases = listOf(
            Triple(AppThemeMode.LIGHT, 1.0f, listOf(BusOperator.CTB, BusOperator.KMB)),
            Triple(AppThemeMode.DARK, 1.3f, listOf(BusOperator.CTB, BusOperator.LWB)),
            Triple(
                AppThemeMode.LIGHT,
                2.0f,
                listOf(
                    BusOperator.CTB,
                    BusOperator.KMB,
                    BusOperator.LWB,
                    BusOperator.CTB,
                    BusOperator.KMB,
                    BusOperator.LWB
                )
            )
        )
        executeShell("wm size 1080x2400")
        executeShell("wm density 480")

        cases.forEachIndexed { caseIndex, (theme, fontScale, operators) ->
            AppThemePreferenceStore(context).setMode(theme)
            AppCompatDelegate.setDefaultNightMode(theme.nightMode)
            executeShell("settings put system font_scale $fontScale")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var sheet: EtaArrivalsBottomSheet
                scenario.onActivity { activity ->
                    sheet = EtaArrivalsBottomSheet(activity)
                    sheet.show(route(caseIndex, operators))
                }
                instrumentation.waitForIdleSync()

                onView(withText(context.getString(R.string.eta_sheet_title, "118")))
                    .inRoot(isDialog())
                    .check(matches(isCompletelyDisplayed()))
                if (caseIndex < 2) {
                    operators.distinct().forEach { operator ->
                        onView(withText(operator.labelRes()))
                            .inRoot(isDialog())
                            .check(matches(isDisplayed()))
                    }
                }
                if (operators.size > 3) {
                    onView(instanceOf(ScrollView::class.java))
                        .inRoot(isDialog())
                        .perform(swipeUp(), swipeUp())
                    onView(withText(LONG_REMARK)).inRoot(isDialog()).check(matches(isDisplayed()))
                    onView(withText(context.getString(R.string.eta_sheet_title, "118")))
                        .inRoot(isDialog())
                        .check(matches(isCompletelyDisplayed()))
                    onView(instanceOf(ScrollView::class.java)).inRoot(isDialog()).check { view, error ->
                        if (error != null) throw error
                        assertTrue(view is ScrollView)
                    }
                }
                scenario.onActivity { sheet.dispose() }
            }
        }
    }

    private fun route(index: Int, operators: List<BusOperator>): BusRouteOption {
        return BusRouteOption(
            routeName = "118",
            routeSegments = listOf("118"),
            priceHkd = 12.3,
            durationMinutes = 40,
            arrivalMinutes = 3,
            transferCount = 0,
            walkingDistanceMeters = 120,
            resultId = "matrix-$index",
            stopPreview = RouteCardStopPreview(
                "樂軒臺長方向名稱測試",
                "深水埗（東京街）長方向名稱測試"
            ),
            waitTimeState = WaitTimeState.Available(
                operators.mapIndexed { arrivalIndex, operator ->
                    EtaArrival(
                        sequence = arrivalIndex + 1,
                        minutes = arrivalIndex + 2,
                        etaMillis = 1_800_000L + arrivalIndex * 60_000L,
                        arrivalTimeText = "12:${(arrivalIndex + 2).toString().padStart(2, '0')}",
                        destination = "深水埗（東京街）長方向名稱測試",
                        remark = if (arrivalIndex == operators.lastIndex) LONG_REMARK else null,
                        dataTimestampMillis = 1_700_000L,
                        operator = operator,
                        sourceSequence = arrivalIndex + 1
                    )
                }
            )
        )
    }

    private fun BusOperator.labelRes(): Int = when (this) {
        BusOperator.CTB -> R.string.operator_ctb
        BusOperator.KMB -> R.string.operator_kmb
        BusOperator.LWB -> R.string.operator_lwb
    }

    private fun executeShell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).close()
        instrumentation.waitForIdleSync()
    }

    companion object {
        private const val LONG_REMARK = "受道路封闭影响，本班次将按现场交通情况调整服务。"
    }
}
