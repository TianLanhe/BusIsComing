package com.golink.busiscoming

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.model.InitialInstallChannel
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.data.model.UpdateChannel
import com.golink.busiscoming.data.model.UpdateSnapshot
import com.golink.busiscoming.data.model.UpdateSnapshotState
import com.golink.busiscoming.data.update.AppUpdateRuntime
import com.golink.busiscoming.data.update.SharedPreferencesUpdateStateStore
import com.golink.busiscoming.data.update.UpdatePolicy
import com.golink.busiscoming.data.update.UpdateStoredState
import com.golink.busiscoming.ui.main.MainActivity
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateVisualMatrixInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun updateUiFitsRequestedLanguageThemeAndFontScale() {
        val arguments = InstrumentationRegistry.getArguments()
        val languageName = requireNotNull(arguments.getString(ARG_LANGUAGE))
        val themeName = requireNotNull(arguments.getString(ARG_THEME))
        val fontScale = requireNotNull(arguments.getString(ARG_FONT_SCALE))
        val language = AppLanguageChoice.valueOf(languageName)
        val theme = AppThemeMode.valueOf(themeName)

        try {
            executeShell("settings put system font_scale $fontScale")
            AppThemePreferenceStore(context).setMode(theme)
            AppCompatDelegate.setDefaultNightMode(theme.nightMode)
            AppLanguageRepository(context).setChoice(language)
            executeShell(
                "cmd locale set-app-locales ${context.packageName} " +
                    "--locales ${language.localeTag()}"
            )
            seedAvailableUpdate()

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTheme(scenario, theme)
                assertLanguage(scenario, language)
                assertPromptFits(fontScale.toFloat())
                saveScreenshot("dialog-${language.name}-${theme.name}-$fontScale.png")

                onView(withText(R.string.update_action_later)).perform(click())
                onView(withText(R.string.update_downloaded_message)).check(
                    matches(isCompletelyDisplayed())
                ).check(noEllipsis())
                onView(withText(R.string.update_downloaded_action)).check(
                    matches(isCompletelyDisplayed())
                ).check(noEllipsis())

                onView(withId(R.id.navigation_settings)).perform(click())
                scenario.onActivity { it.supportFragmentManager.executePendingTransactions() }
                onView(withId(R.id.settingsRoot)).check(matches(isDisplayed()))
                onView(withId(R.id.settingsPrivacyRow)).perform(scrollTo())
                onView(withId(R.id.settingsUpdateSummary)).check(
                    matches(isCompletelyDisplayed())
                ).check(noEllipsis())
                onView(withId(R.id.settingsUpdateDot)).check(matches(isDisplayed()))
                onView(withId(R.id.settingsUpdateDot)).check(
                    matches(withContentDescription(R.string.update_dot_description))
                )
                saveScreenshot("settings-${language.name}-${theme.name}-$fontScale.png")
            }
        } finally {
            executeShell("settings put system font_scale 1.0")
        }
    }

    private fun assertPromptFits(fontScale: Float) {
        listOf(
            R.id.updatePromptTitle,
            R.id.updatePromptVersion,
            R.id.updatePromptMessage,
            R.id.updatePromptLaterButton,
            R.id.updatePromptSkipButton,
            R.id.updatePromptUpdateButton
        ).forEach { viewId ->
            onView(withId(viewId))
                .check(matches(isCompletelyDisplayed()))
                .check(noEllipsis())
        }

        onView(withId(R.id.updatePromptActions)).check { view, noViewFoundException ->
            if (noViewFoundException != null) throw noViewFoundException
            val actions = view as LinearLayout
            val expectedOrientation = if (fontScale >= 2.0f) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
            assertEquals(expectedOrientation, actions.orientation)
            assertEquals(
                listOf(
                    R.id.updatePromptLaterButton,
                    R.id.updatePromptSkipButton,
                    R.id.updatePromptUpdateButton
                ),
                (0 until actions.childCount).map { actions.getChildAt(it).id }
            )

            val children = (0 until actions.childCount).map(actions::getChildAt)
            if (expectedOrientation == LinearLayout.HORIZONTAL) {
                children.forEach { child ->
                    val params = child.layoutParams as LinearLayout.LayoutParams
                    assertEquals(0, params.width)
                    assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.height)
                    assertEquals(1.0f, params.weight, 0.0f)
                }
                assertTrue(
                    children.zipWithNext().all { (left, right) -> left.left < right.left }
                )
                assertTrue(children.map { it.height }.distinct().size == 1)
            } else {
                children.forEach { child ->
                    val params = child.layoutParams as LinearLayout.LayoutParams
                    assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.width)
                    assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, params.height)
                    assertEquals(0.0f, params.weight, 0.0f)
                }
                assertTrue(
                    children.zipWithNext().all { (top, bottom) -> top.top < bottom.top }
                )
            }
        }
    }

    private fun assertTheme(
        scenario: ActivityScenario<MainActivity>,
        theme: AppThemeMode
    ) {
        scenario.onActivity { activity ->
            val actual = activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
            val expected = if (theme == AppThemeMode.DARK) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            assertEquals(expected, actual)
        }
    }

    private fun assertLanguage(
        scenario: ActivityScenario<MainActivity>,
        language: AppLanguageChoice
    ) {
        scenario.onActivity { activity ->
            assertEquals(
                language.localeTag(),
                activity.resources.configuration.locales[0].toLanguageTag()
            )
        }
    }

    private fun noEllipsis() = ViewAssertion { view: View?, noViewFoundException ->
        if (noViewFoundException != null) throw noViewFoundException
        val textView = view as TextView
        val layout = textView.layout
        assertNotNull("Text layout missing for ${textView.text}", layout)
        for (line in 0 until layout.lineCount) {
            assertEquals("Text was ellipsized: ${textView.text}", 0, layout.getEllipsisCount(line))
        }
    }

    private fun seedAvailableUpdate() {
        val now = System.currentTimeMillis()
        val state = UpdateStoredState(
            initialInstallChannel = InitialInstallChannel.PLAY,
            lastAutoAttemptAt = now,
            snapshot = UpdateSnapshot(
                state = UpdateSnapshotState.UPDATE_AVAILABLE,
                channel = UpdateChannel.PLAY,
                installedVersionCode = BuildConfig.VERSION_CODE.toLong(),
                availableVersionCode = BuildConfig.VERSION_CODE.toLong() + 1L,
                availableVersionName = "1.2",
                availableSinceAt = now - 4L * UpdatePolicy.DAY_MILLIS,
                firstSeenAt = now - 4L * UpdatePolicy.DAY_MILLIS,
                checkedAt = now,
                flexibleAllowed = true
            ),
            playUpdateDownloaded = true
        )
        SharedPreferencesUpdateStateStore(
            context,
            BuildConfig.VERSION_CODE.toLong()
        ).save(state)
        instrumentation.runOnMainSync {
            AppUpdateRuntime.coordinator.reloadPersistedState()
        }
        instrumentation.waitForIdleSync()
    }

    private fun saveScreenshot(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val outputDirectory = File(requireNotNull(context.getExternalFilesDir(null)), OUTPUT_DIR)
        assertTrue(outputDirectory.exists() || outputDirectory.mkdirs())
        FileOutputStream(File(outputDirectory, name)).use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }

    private fun executeShell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).bufferedReader().use { it.readText() }.also {
            instrumentation.waitForIdleSync()
        }

    private fun AppLanguageChoice.localeTag(): String = when (this) {
        AppLanguageChoice.TRADITIONAL_CHINESE -> "zh-Hant-HK"
        AppLanguageChoice.SIMPLIFIED_CHINESE -> "zh-Hans-CN"
        AppLanguageChoice.ENGLISH -> "en"
        AppLanguageChoice.FOLLOW_SYSTEM -> error("Visual matrix requires an explicit language")
    }

    companion object {
        private const val ARG_LANGUAGE = "updateLanguage"
        private const val ARG_THEME = "updateTheme"
        private const val ARG_FONT_SCALE = "updateFontScale"
        private const val OUTPUT_DIR = "update-visual-validation"
    }
}
