package com.golink.busiscoming

import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatTextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.location.CurrentPlaceSelectionResult
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.repository.BusRouteRepository
import com.golink.busiscoming.data.repository.PlaceSearchRepository
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.RouteConfigSaveGateway
import com.golink.busiscoming.ui.main.SearchFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import java.io.FileInputStream
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
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
        val resolvedFontScale = fontScale.toFloat()

        executeShell("wm size ${if (resolvedFontScale <= 1f) 1440 else 945}x2100")
        executeShell("settings put system font_scale $fontScale")
        SearchFragment.currentPlaceRequestOverride = { _, callback ->
            callback(CurrentPlaceSelectionResult.Failure)
        }

        languages.forEach { language ->
            themes.forEach { theme ->
                val placeRepository = MatrixPlaceRepository(language)
                SearchFragment.placeSearchRepositoryFactory = { placeRepository }
                SearchFragment.busRouteRepositoryFactory = { MatrixRouteRepository() }
                SearchFragment.routeConfigSaveGatewayFactory = { MatrixSaveGateway() }
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
                    selectPlace(
                        R.id.placePairOriginInput,
                        "o",
                        placeRepository.origin.name
                    )
                    selectPlace(
                        R.id.placePairDestinationInput,
                        "d",
                        placeRepository.destination.name
                    )
                    onView(withId(R.id.searchQueryButton)).perform(click())
                    waitForDisplayed(R.id.searchTripContext)

                    val expectSingleRow =
                        resolvedFontScale <= 1f && language != AppLanguageChoice.ENGLISH
                    assertActionState(
                        scenario,
                        editVisible = true,
                        cancelVisible = false,
                        saveEnabled = true
                    )
                    assertTripContext(scenario, expectSingleRow)

                    performClick(scenario, R.id.searchEditButton)
                    waitForDisplayed(R.id.searchCancelEditButton)
                    assertActionState(
                        scenario,
                        editVisible = false,
                        cancelVisible = true,
                        saveEnabled = true
                    )
                    assertTripContext(scenario, expectSingleRow)
                    performClick(scenario, R.id.searchCancelEditButton)

                    performClick(scenario, R.id.searchSaveButton)
                    onView(withText(R.string.action_save)).inRoot(isDialog()).perform(click())
                    waitForDisplayed(R.id.searchSaveButton)
                    assertActionState(
                        scenario,
                        editVisible = true,
                        cancelVisible = false,
                        saveEnabled = false
                    )
                    assertTripContext(scenario, expectSingleRow)

                    if (
                        resolvedFontScale <= 1f &&
                        language == AppLanguageChoice.TRADITIONAL_CHINESE &&
                        theme == AppThemeMode.LIGHT
                    ) {
                        scenario.onActivity { activity ->
                            activity.findViewById<TextView>(R.id.searchTripRouteText).apply {
                                text = "非常長的起點名稱".repeat(12) + " → " +
                                    "非常長的終點名稱".repeat(12)
                                requestLayout()
                            }
                        }
                        waitForTripOrientation(scenario, LinearLayout.VERTICAL)
                        assertTripContext(scenario, expectSingleRow = false)
                        scenario.onActivity { activity ->
                            activity.findViewById<TextView>(R.id.searchTripRouteText).apply {
                                text = "短起點 → 短終點"
                                requestLayout()
                            }
                        }
                        waitForTripOrientation(scenario, LinearLayout.HORIZONTAL)
                        assertTripContext(scenario, expectSingleRow = true)

                        executeShell("wm size 945x2100")
                        waitForTripOrientation(scenario, LinearLayout.VERTICAL)
                        assertTripContext(scenario, expectSingleRow = false)
                        executeShell("wm size 1440x2100")
                        waitForTripOrientation(scenario, LinearLayout.HORIZONTAL)
                        assertTripContext(scenario, expectSingleRow = true)
                    }
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

    private fun selectPlace(inputId: Int, keyword: String, expected: String) {
        onView(withId(inputId)).perform(click(), replaceText(keyword), closeSoftKeyboard())
        waitUntil {
            try {
                onView(withText(expected)).check(matches(isDisplayed()))
                true
            } catch (_: NoMatchingViewException) {
                false
            } catch (_: AssertionError) {
                false
            }
        }
        onView(withText(expected)).perform(click())
    }

    private fun waitForDisplayed(viewId: Int) {
        waitUntil {
            try {
                onView(withId(viewId)).check(matches(isDisplayed()))
                true
            } catch (_: NoMatchingViewException) {
                false
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun performClick(scenario: ActivityScenario<MainActivity>, viewId: Int) {
        scenario.onActivity { activity ->
            val view = activity.findViewById<View>(viewId)
            assertEquals(View.VISIBLE, view.visibility)
            assertTrue(view.isEnabled)
            assertTrue(view.performClick())
        }
        instrumentation.waitForIdleSync()
    }

    private fun waitForTripOrientation(
        scenario: ActivityScenario<MainActivity>,
        orientation: Int
    ) {
        waitUntil {
            var actual = -1
            scenario.onActivity { activity ->
                actual = activity.findViewById<LinearLayout>(R.id.searchTripContextBody).orientation
            }
            actual == orientation
        }
    }

    private fun assertTripContext(
        scenario: ActivityScenario<MainActivity>,
        expectSingleRow: Boolean
    ) {
        scenario.onActivity { activity ->
            val card = activity.findViewById<View>(R.id.searchTripContext)
            val body = activity.findViewById<LinearLayout>(R.id.searchTripContextBody)
            val route = activity.findViewById<TextView>(R.id.searchTripRouteText)
            val actions = activity.findViewById<LinearLayout>(R.id.searchTripActions)
            assertEquals(
                if (expectSingleRow) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL,
                body.orientation
            )
            assertEquals(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    16f,
                    activity.resources.displayMetrics
                ),
                route.textSize,
                0.5f
            )
            assertNull(route.ellipsize)
            route.layout?.let { layout ->
                repeat(layout.lineCount) { line ->
                    assertEquals(0, layout.getEllipsisCount(line))
                }
            }
            assertInside(route, card)
            assertInside(actions, card)
            repeat(actions.childCount) { index ->
                val button = actions.getChildAt(index)
                if (button.visibility == View.VISIBLE) {
                    assertTrue(button is MaterialButton)
                    button as MaterialButton
                    assertTrue(button.measuredHeight >= dp(activity, 48))
                    assertNull(button.ellipsize)
                    button.layout?.let { layout ->
                        repeat(layout.lineCount) { line ->
                            assertEquals(0, layout.getEllipsisCount(line))
                        }
                    }
                    assertInside(button, actions)
                    assertFalse(button.text.isNullOrBlank())
                }
            }
        }
    }

    private fun assertActionState(
        scenario: ActivityScenario<MainActivity>,
        editVisible: Boolean,
        cancelVisible: Boolean,
        saveEnabled: Boolean
    ) {
        scenario.onActivity { activity ->
            assertEquals(
                if (editVisible) View.VISIBLE else View.GONE,
                activity.findViewById<View>(R.id.searchEditButton).visibility
            )
            assertEquals(
                if (cancelVisible) View.VISIBLE else View.GONE,
                activity.findViewById<View>(R.id.searchCancelEditButton).visibility
            )
            assertEquals(
                saveEnabled,
                activity.findViewById<View>(R.id.searchSaveButton).isEnabled
            )
        }
    }

    private fun assertInside(child: View, parent: View) {
        assertTrue(child.left >= 0)
        assertTrue(child.top >= 0)
        assertTrue(child.right <= parent.width)
        assertTrue(child.bottom <= parent.height)
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

    private class MatrixPlaceRepository(language: AppLanguageChoice) : PlaceSearchRepository {
        val origin = if (language == AppLanguageChoice.ENGLISH) {
            Place("Convention and Exhibition Centre Harbour Entrance", 22.28, 114.17)
        } else {
            Place("中環", 22.28, 114.17)
        }
        val destination = if (language == AppLanguageChoice.ENGLISH) {
            Place("International Commerce Centre Transportation Interchange", 22.30, 114.16)
        } else {
            Place("灣仔", 22.27, 114.18)
        }

        override fun searchPlaces(keyword: String): List<Place> =
            if (keyword == "o") listOf(origin) else listOf(destination)
    }

    private class MatrixRouteRepository : BusRouteRepository {
        override fun searchRoutes(origin: Place, destination: Place): List<BusRouteOption> =
            listOf(
                BusRouteOption(
                    routeName = "88",
                    routeSegments = listOf("88"),
                    priceHkd = 8.8,
                    durationMinutes = 25,
                    arrivalMinutes = 4,
                    transferCount = 0,
                    walkingDistanceMeters = 120,
                    resultId = "matrix-route"
                )
            )
    }

    private class MatrixSaveGateway : RouteConfigSaveGateway {
        override fun hasDuplicate(name: String, origin: Place, destination: Place): Boolean = false

        override fun insert(name: String, origin: Place, destination: Place): Long = 1L
    }

    private companion object {
        const val ARG_FONT_SCALE = "routeFontScale"
    }
}
