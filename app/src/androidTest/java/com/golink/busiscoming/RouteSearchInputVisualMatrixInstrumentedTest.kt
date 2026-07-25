package com.golink.busiscoming

import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatTextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.location.CurrentPlaceSelectionResult
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.SearchFragment
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import java.io.FileInputStream
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteSearchInputVisualMatrixInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @After
    fun restoreEnvironment() {
        executeShell("settings put system font_scale 1.0")
        executeShell("wm size reset")
        AppThemePreferenceStore(context).setMode(AppThemeMode.SYSTEM)
        AppCompatDelegate.setDefaultNightMode(AppThemeMode.SYSTEM.nightMode)
        AppLanguageRepository(context).setChoice(AppLanguageChoice.TRADITIONAL_CHINESE)
        SearchFragment.resetTestDependencies()
        MainActivity.resetTestDependencies()
    }

    @Test
    fun routeSearchInputFitsRequestedLanguageThemeAndFontScale() {
        val arguments = InstrumentationRegistry.getArguments()
        val fontScale = requireNotNull(arguments.getString(ARG_FONT_SCALE))
        val languages = listOf(
            AppLanguageChoice.TRADITIONAL_CHINESE,
            AppLanguageChoice.SIMPLIFIED_CHINESE,
            AppLanguageChoice.ENGLISH
        )
        val themes = listOf(AppThemeMode.LIGHT, AppThemeMode.DARK)

        executeShell("wm size 945x2100")
        executeShell("settings put system font_scale $fontScale")
        SearchFragment.currentPlaceRequestOverride = { _, callback ->
            callback(CurrentPlaceSelectionResult.Failure)
        }

        languages.forEach { language ->
            themes.forEach { theme ->
                AppThemePreferenceStore(context).setMode(theme)
                AppCompatDelegate.setDefaultNightMode(theme.nightMode)
                AppLanguageRepository(context).setChoice(language)
                executeShell(
                    "cmd locale set-app-locales ${context.packageName} " +
                        "--locales ${language.localeTag()}"
                )

                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        assertTrue(
                            activity.findViewById<View>(R.id.navigation_search).performClick()
                        )
                    }
                    instrumentation.waitForIdleSync()
                    waitForLocationFailure(scenario)
                    assertCase(scenario, language, theme)
                }
            }
        }
    }

    private fun waitForLocationFailure(scenario: ActivityScenario<MainActivity>) {
        waitUntil {
            var locationFailureVisible = false
            scenario.onActivity { activity ->
                val originLayout =
                    activity.findViewById<TextInputLayout>(R.id.placePairOriginLayout)
                val label = activity.getString(R.string.search_field_origin_label)
                val full = activity.getString(
                    R.string.search_field_caption_format,
                    label,
                    activity.getString(R.string.search_field_location_failure)
                )
                val compact = activity.getString(
                    R.string.search_field_caption_format,
                    label,
                    activity.getString(R.string.search_field_location_failure_compact)
                )
                locationFailureVisible =
                    originLayout.hint.toString() == full ||
                        originLayout.hint.toString() == compact
            }
            locationFailureVisible
        }
    }

    private fun assertCase(
        scenario: ActivityScenario<MainActivity>,
        language: AppLanguageChoice,
        theme: AppThemeMode
    ) {
        scenario.onActivity { activity ->
            val originLayout =
                activity.findViewById<TextInputLayout>(R.id.placePairOriginLayout)
            val destinationLayout =
                activity.findViewById<TextInputLayout>(R.id.placePairDestinationLayout)
            val originInput =
                activity.findViewById<MaterialAutoCompleteTextView>(R.id.placePairOriginInput)
            val destinationInput =
                activity.findViewById<MaterialAutoCompleteTextView>(
                    R.id.placePairDestinationInput
                )
            val locationButton =
                activity.findViewById<View>(R.id.placePairCurrentLocationButton)
            val swapButton = activity.findViewById<View>(R.id.placePairSwapButton)
            val originLabel = activity.getString(R.string.search_field_origin_label)
            val destinationLabel =
                activity.getString(R.string.search_field_destination_label)
            val locationFull =
                activity.getString(R.string.search_field_location_failure)
            val locationCompact =
                activity.getString(R.string.search_field_location_failure_compact)
            val instructionFull =
                activity.getString(R.string.search_field_choose_from_list)
            val instructionCompact =
                activity.getString(R.string.search_field_choose_from_list_compact)

            assertCaption(
                inputLayout = originLayout,
                input = originInput,
                label = originLabel,
                fullStatus = locationFull,
                compactStatus = locationCompact
            )
            assertCaption(
                inputLayout = destinationLayout,
                input = destinationInput,
                label = destinationLabel,
                fullStatus = instructionFull,
                compactStatus = instructionCompact
            )
            assertEveryCompactStatusFits(originLayout, originInput, originLabel)
            assertNull(originLayout.helperText)
            assertNull(originLayout.error)
            assertNull(destinationLayout.helperText)
            assertNull(destinationLayout.error)

            val tolerance = dp(activity, 1)
            val originCenter = originInput.centerYOnScreen()
            val destinationCenter = destinationInput.centerYOnScreen()
            assertTrue(
                abs(originCenter - locationButton.centerYOnScreen()) <= tolerance
            )
            assertTrue(
                abs(
                    (originCenter + destinationCenter) / 2 -
                        swapButton.centerYOnScreen()
                ) <= tolerance
            )

            val actualNightMode = activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
            val expectedNightMode = if (theme == AppThemeMode.DARK) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            assertEquals(expectedNightMode, actualNightMode)
            assertEquals(
                language.localeTag(),
                activity.resources.configuration.locales[0].toLanguageTag()
            )
        }
    }

    private fun assertCaption(
        inputLayout: TextInputLayout,
        input: MaterialAutoCompleteTextView,
        label: String,
        fullStatus: String,
        compactStatus: String
    ) {
        val fullCaption =
            inputLayout.context.getString(R.string.search_field_caption_format, label, fullStatus)
        val compactCaption =
            inputLayout.context.getString(
                R.string.search_field_caption_format,
                label,
                compactStatus
            )
        val actual = inputLayout.hint.toString()
        assertTrue(
            "Unexpected caption: $actual",
            actual == fullCaption || actual == compactCaption
        )

        val measureView = AppCompatTextView(inputLayout.context).apply {
            setTextAppearance(R.style.TextAppearance_BusIsComing_SearchFieldCaption)
        }
        val availableWidth =
            inputLayout.width - input.paddingStart - dp(inputLayout.context, 8)
        assertTrue(
            "Caption exceeds field: $actual",
            measureView.paint.measureText(actual) <= availableWidth
        )
        assertTrue(
            inputLayout.contentDescription.toString().contains(fullStatus)
        )
    }

    private fun assertEveryCompactStatusFits(
        inputLayout: TextInputLayout,
        input: MaterialAutoCompleteTextView,
        label: String
    ) {
        val measureView = AppCompatTextView(inputLayout.context).apply {
            setTextAppearance(R.style.TextAppearance_BusIsComing_SearchFieldCaption)
        }
        val availableWidth =
            inputLayout.width - input.paddingStart - dp(inputLayout.context, 8)
        listOf(
            R.string.search_field_choose_from_list_compact,
            R.string.search_field_google_maps_address_compact,
            R.string.search_field_no_matches_compact,
            R.string.search_field_search_failed_compact,
            R.string.search_field_location_failure_compact,
            R.string.search_field_choose_place_compact,
            R.string.search_field_same_as_origin_compact
        ).forEach { statusResource ->
            val caption = inputLayout.context.getString(
                R.string.search_field_caption_format,
                label,
                inputLayout.context.getString(statusResource)
            )
            assertTrue(
                "Compact caption exceeds field: $caption",
                measureView.paint.measureText(caption) <= availableWidth
            )
        }
    }

    private fun AppLanguageChoice.localeTag(): String = when (this) {
        AppLanguageChoice.FOLLOW_SYSTEM -> "zh-Hant-HK"
        AppLanguageChoice.TRADITIONAL_CHINESE -> "zh-Hant-HK"
        AppLanguageChoice.SIMPLIFIED_CHINESE -> "zh-Hans-CN"
        AppLanguageChoice.ENGLISH -> "en"
    }

    private fun View.centerYOnScreen(): Int {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[1] + height / 2
    }

    private fun dp(context: android.content.Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun executeShell(command: String) {
        val descriptor: ParcelFileDescriptor =
            instrumentation.uiAutomation.executeShellCommand(command)
        FileInputStream(descriptor.fileDescriptor).use { stream ->
            while (stream.read() != -1) {
                // 必須讀完 shell 輸出，避免阻塞後續命令。
            }
        }
        descriptor.close()
        instrumentation.waitForIdleSync()
    }

    private fun waitUntil(timeoutMillis: Long = 10_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("Timed out waiting for route search state", condition())
    }

    private companion object {
        const val ARG_FONT_SCALE = "routeFontScale"
    }
}
