package com.golink.busiscoming

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.localization.AppLanguage
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.data.update.GooglePlayRatingActions
import com.golink.busiscoming.data.update.GooglePlayRatingRuntime
import com.golink.busiscoming.data.update.PlayStoreAvailability
import com.golink.busiscoming.ui.main.MainActivity
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GooglePlayRatingInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var originalLanguage: AppLanguageChoice
    private lateinit var originalTheme: AppThemeMode
    private var availability = PlayStoreAvailability.AVAILABLE
    private val actions = FakeRatingActions()

    @Before
    fun prepare() {
        assumeTrue(InstrumentationRegistry.getArguments().getString(ARG_RUN_RATING) == "true")
        originalLanguage = AppLanguageRepository(context).getChoice()
        originalTheme = AppThemePreferenceStore(context).getMode()
        GooglePlayRatingRuntime.availabilityResolver = { availability }
        GooglePlayRatingRuntime.navigatorFactory = { actions }
        executeShell("wm size 1080x2400")
        executeShell("wm density 480")
    }

    @After
    fun restore() {
        GooglePlayRatingRuntime.reset()
        AppThemePreferenceStore(context).setMode(originalTheme)
        AppCompatDelegate.setDefaultNightMode(originalTheme.nightMode)
        AppLanguageRepository(context).setChoice(originalLanguage)
        executeShell("settings put system font_scale 1.0")
        executeShell("wm size reset")
        executeShell("wm density reset")
    }

    @Test
    fun fourStatesCancelRecoveryFailureAndRecreationDoNotReplayNavigation() {
        configure(AppLanguageChoice.TRADITIONAL_CHINESE, AppThemeMode.LIGHT)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSettings()

            availability = PlayStoreAvailability.AVAILABLE
            clickRating()
            assertEquals(1, actions.productCalls)

            availability = PlayStoreAvailability.DISABLED
            clickRating()
            onView(withText(R.string.rating_play_disabled_title)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.update_action_cancel)).inRoot(isDialog()).perform(click())
            assertEquals(0, actions.playSettingsCalls)
            clickRating()
            onView(withText(R.string.rating_action_enable_play)).inRoot(isDialog()).perform(click())
            assertEquals(1, actions.playSettingsCalls)

            availability = PlayStoreAvailability.MISSING
            clickRating()
            onView(withText(R.string.rating_action_view_install_help)).inRoot(isDialog()).perform(click())
            assertEquals(listOf(AppLanguage.TRADITIONAL_CHINESE), actions.helpLanguages)

            availability = PlayStoreAvailability.UNUSABLE
            clickRating()
            onView(withText(R.string.rating_action_app_settings)).inRoot(isDialog()).perform(click())
            assertEquals(1, actions.appSettingsCalls)

            availability = PlayStoreAvailability.AVAILABLE
            actions.productResult = false
            clickRating()
            onView(withId(R.id.settingsRoot)).check(matches(isDisplayed()))
            assertEquals(2, actions.productCalls)
            val callsBeforeRecreate = actions.totalCalls()

            scenario.recreate()
            openSettings()
            assertEquals(callsBeforeRecreate, actions.totalCalls())
        }
    }

    @Test
    fun recoveryDialogsFitThreeLanguagesThemesAndLargeText() {
        executeShell("settings put system font_scale 2.0")
        val languages = listOf(
            AppLanguageChoice.TRADITIONAL_CHINESE,
            AppLanguageChoice.SIMPLIFIED_CHINESE,
            AppLanguageChoice.ENGLISH
        )
        val themes = listOf(AppThemeMode.LIGHT, AppThemeMode.DARK)
        val states = listOf(
            DialogCase(
                PlayStoreAvailability.DISABLED,
                R.string.rating_play_disabled_title,
                R.string.rating_play_disabled_message,
                R.string.rating_action_enable_play
            ),
            DialogCase(
                PlayStoreAvailability.MISSING,
                R.string.rating_play_missing_title,
                R.string.rating_play_missing_message,
                R.string.rating_action_view_install_help
            ),
            DialogCase(
                PlayStoreAvailability.UNUSABLE,
                R.string.rating_play_unusable_title,
                R.string.rating_play_unusable_message,
                R.string.rating_action_app_settings
            )
        )
        languages.forEach { language ->
            themes.forEach { theme ->
                configure(language, theme)
                states.forEach { dialogCase ->
                    availability = dialogCase.state
                    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                        openSettings()
                        clickRating()
                        assertDialogTextNotEllipsized(dialogCase.titleRes)
                        assertDialogTextNotEllipsized(dialogCase.messageRes)
                        assertDialogTextNotEllipsized(dialogCase.actionRes)
                        assertDialogTextNotEllipsized(R.string.update_action_cancel)
                        scenario.onActivity { activity ->
                            saveScreenshot(
                                activity,
                                "google-play-rating-${language.name.lowercase()}-${theme.name.lowercase()}-${dialogCase.state.name.lowercase()}.png"
                            )
                        }
                        onView(withText(R.string.update_action_cancel)).inRoot(isDialog()).perform(click())
                    }
                }
            }
        }
    }

    private fun configure(language: AppLanguageChoice, theme: AppThemeMode) {
        AppThemePreferenceStore(context).setMode(theme)
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        AppLanguageRepository(context).setChoice(language)
        executeShell("cmd locale set-app-locales ${context.packageName} --locales ${language.localeTag()}")
    }

    private fun openSettings() {
        onView(withId(R.id.navigation_settings)).perform(click())
        onView(withId(R.id.settingsRoot)).check(matches(isDisplayed()))
    }

    private fun clickRating() {
        onView(withId(R.id.settingsRatingRow)).perform(scrollTo(), click())
    }

    private fun assertDialogTextNotEllipsized(textRes: Int) {
        onView(withText(textRes)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            val textView = view as TextView
            assertTrue(textView.isShown)
            for (line in 0 until textView.layout.lineCount) {
                assertEquals("Unexpected ellipsis in '${textView.text}'", 0, textView.layout.getEllipsisCount(line))
            }
        }
    }

    private fun saveScreenshot(activity: MainActivity, name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val output = File(requireNotNull(activity.getExternalFilesDir(null)), name)
        FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun executeShell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
        }
    }

    private fun AppLanguageChoice.localeTag(): String = when (this) {
        AppLanguageChoice.FOLLOW_SYSTEM -> ""
        AppLanguageChoice.TRADITIONAL_CHINESE -> "zh-Hant"
        AppLanguageChoice.SIMPLIFIED_CHINESE -> "zh-Hans"
        AppLanguageChoice.ENGLISH -> "en"
    }

    private class FakeRatingActions : GooglePlayRatingActions {
        var productResult = true
        var productCalls = 0
        var playSettingsCalls = 0
        var appSettingsCalls = 0
        val helpLanguages = mutableListOf<AppLanguage>()

        override fun openProductPage(context: Context): Boolean {
            productCalls += 1
            return productResult
        }

        override fun openPlayAppSettings(context: Context): Boolean {
            playSettingsCalls += 1
            return true
        }

        override fun openAppSettings(context: Context): Boolean {
            appSettingsCalls += 1
            return true
        }

        override fun openOfficialHelp(context: Context, language: AppLanguage): Boolean {
            helpLanguages += language
            return true
        }

        fun totalCalls(): Int = productCalls + playSettingsCalls + appSettingsCalls + helpLanguages.size
    }

    private data class DialogCase(
        val state: PlayStoreAvailability,
        val titleRes: Int,
        val messageRes: Int,
        val actionRes: Int
    )

    private companion object {
        const val ARG_RUN_RATING = "runGooglePlayRating"
    }
}
