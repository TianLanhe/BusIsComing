package com.golink.busiscoming

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.repository.RouteDetailRepository
import com.golink.busiscoming.ui.main.RouteDetailActivity
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import com.golink.busiscoming.ui.main.RouteDetailRuntime
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteDetailVisualMatrixInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @After
    fun restoreEnvironment() {
        RouteDetailRuntime.reset()
        executeShell("settings put system font_scale 1.0")
        executeShell("wm size reset")
        executeShell("wm density reset")
        AppThemePreferenceStore(context).setMode(AppThemeMode.SYSTEM)
        AppCompatDelegate.setDefaultNightMode(AppThemeMode.SYSTEM.nightMode)
        AppLanguageRepository(context).setChoice(AppLanguageChoice.TRADITIONAL_CHINESE)
        executeShell("cmd locale set-app-locales ${context.packageName} --locales zh-Hant")
    }

    @Test
    fun routeDetailFitsThreeLanguagesAndBothThemesAtRequestedFontScale() {
        val fontScale = requireNotNull(
            InstrumentationRegistry.getArguments().getString(ARG_FONT_SCALE)
        ).toFloat()
        val detail = longContentDetail()
        RouteDetailRuntime.repositoryFactory = {
            object : RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail = detail
            }
        }
        RouteDetailRuntime.etaResolver = { DemoScreenshotFixtures.primaryRoute().waitTimeState }
        executeShell("wm size 1080x2400")
        executeShell("wm density 480")
        executeShell("settings put system font_scale $fontScale")

        val languages = listOf(
            AppLanguageChoice.TRADITIONAL_CHINESE,
            AppLanguageChoice.SIMPLIFIED_CHINESE,
            AppLanguageChoice.ENGLISH
        )
        val themes = listOf(AppThemeMode.LIGHT, AppThemeMode.DARK)
        languages.forEach { language ->
            themes.forEach { theme ->
                configure(language, theme)
                ActivityScenario.launch<RouteDetailActivity>(intent()).use { scenario ->
                    waitForDetail(scenario)
                    scenario.onActivity { activity ->
                        assertEquals(fontScale, activity.resources.configuration.fontScale, 0.01f)
                        assertEquals(360, activity.resources.displayMetrics.widthPixels * 160 / activity.resources.displayMetrics.densityDpi)
                        val expectedNightMode = if (theme == AppThemeMode.DARK) {
                            Configuration.UI_MODE_NIGHT_YES
                        } else {
                            Configuration.UI_MODE_NIGHT_NO
                        }
                        assertEquals(
                            expectedNightMode,
                            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        )
                        assertTrue(collectText(activity.window.decorView).contains(expectedTitle(language)))
                        activity.findViewById<RecyclerView>(R.id.routeDetailList).scrollToPosition(4)
                    }
                    waitForUi()
                    scenario.onActivity { activity ->
                        val visibleText = collectText(activity.window.decorView)
                        assertTrue(visibleText.any { it.contains(LONG_DIRECTION) })
                        assertNoEllipsis(activity.window.decorView)
                        toggleLeg(activity, 0)
                        toggleLeg(activity, 1)
                    }
                    waitForUi()
                    scenario.onActivity { activity ->
                        val list = activity.findViewById<RecyclerView>(R.id.routeDetailList)
                        list.scrollToPosition(requireNotNull(list.adapter).itemCount - 1)
                    }
                    waitForUi()
                    scenario.onActivity { activity ->
                        assertTrue(collectText(activity.window.decorView).any { it.contains(LONG_DESTINATION) })
                        assertNoEllipsis(activity.window.decorView)
                    }
                }
            }
        }
    }

    private fun configure(language: AppLanguageChoice, theme: AppThemeMode) {
        AppThemePreferenceStore(context).setMode(theme)
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        AppLanguageRepository(context).setChoice(language)
        executeShell(
            "cmd locale set-app-locales ${context.packageName} --locales ${language.localeTag()}"
        )
    }

    private fun waitForDetail(scenario: ActivityScenario<RouteDetailActivity>) {
        waitUntil {
            val count = AtomicInteger()
            scenario.onActivity { activity ->
                count.set(activity.findViewById<RecyclerView>(R.id.routeDetailList).adapter?.itemCount ?: 0)
            }
            count.get() > 2
        }
    }

    private fun intent(): Intent = Intent(context, RouteDetailActivity::class.java).putExtras(
        RouteDetailLaunchArgs.fromRoute(DemoScreenshotFixtures.primaryRoute()).toBundle()
    )

    private fun longContentDetail(): RouteDetail {
        val original = DemoScreenshotFixtures.routeDetail()
        val firstLeg = original.legs.first()
        return original.copy(
            destinationName = LONG_DESTINATION,
            legs = original.legs.toMutableList().apply {
                this[0] = firstLeg.copy(
                    directionText = LONG_DIRECTION,
                    boardingStop = firstLeg.boardingStop.copy(
                        rawName = LONG_BOARDING_STOP,
                        displayName = LONG_BOARDING_STOP
                    )
                )
            }
        )
    }

    private fun toggleLeg(activity: RouteDetailActivity, index: Int) {
        activity.javaClass.getDeclaredMethod("toggleLeg", Int::class.javaPrimitiveType).apply {
            isAccessible = true
        }.invoke(activity, index)
    }

    private fun assertNoEllipsis(view: View) {
        if (view is TextView && view.layout != null) {
            for (line in 0 until view.layout.lineCount) {
                assertEquals("Unexpected ellipsis in '${view.text}'", 0, view.layout.getEllipsisCount(line))
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) assertNoEllipsis(view.getChildAt(index))
        }
    }

    private fun collectText(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { collectText(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun waitForUi() {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300L)
        instrumentation.waitForIdleSync()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(100L)
        }
        assertTrue("Timed out waiting for route detail", condition())
    }

    private fun executeShell(command: String) {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        descriptor.use { FileInputStream(it.fileDescriptor).use(FileInputStream::readBytes) }
    }

    private fun expectedTitle(language: AppLanguageChoice): String = when (language) {
        AppLanguageChoice.TRADITIONAL_CHINESE -> "路線詳情"
        AppLanguageChoice.SIMPLIFIED_CHINESE -> "路线详情"
        AppLanguageChoice.ENGLISH -> "Route details"
        AppLanguageChoice.FOLLOW_SYSTEM -> error("Not part of the explicit language matrix")
    }

    private fun AppLanguageChoice.localeTag(): String = when (this) {
        AppLanguageChoice.TRADITIONAL_CHINESE -> "zh-Hant"
        AppLanguageChoice.SIMPLIFIED_CHINESE -> "zh-Hans"
        AppLanguageChoice.ENGLISH -> "en"
        AppLanguageChoice.FOLLOW_SYSTEM -> ""
    }

    private companion object {
        const val ARG_FONT_SCALE = "fontScale"
        const val LONG_DIRECTION = "香港海岸公園及景澄站海濱長廊方向"
        const val LONG_BOARDING_STOP = "海庭苑公共運輸交匯處臨時上落客區"
        const val LONG_DESTINATION = "松嶺邨公共運輸交匯處臨時總站"
    }
}
