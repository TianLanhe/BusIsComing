package com.golink.busiscoming

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import com.golink.busiscoming.data.localization.AppLanguage
import com.golink.busiscoming.data.update.AppUpdateExternalActions
import com.golink.busiscoming.data.update.AppUpdateExternalActions.ExternalUpdateTarget
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateUiContractTest {
    private val applicationSource =
        File("src/main/java/com/golink/busiscoming/BusIsComingApplication.kt").readText()
    private val mainActivitySource =
        File("src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt").readText()
    private val updateDialogSource =
        File("src/main/res/layout/dialog_app_update.xml").readText()
    private val themeSource =
        File("src/main/res/values/themes.xml").readText()
    private val settingsSource =
        File("src/main/java/com/golink/busiscoming/ui/main/SettingsFragment.kt").readText() +
            File("src/main/java/com/golink/busiscoming/ui/main/UpdateSettingsUiModel.kt").readText()
    private val traditional = File("src/main/res/values/strings.xml").readText()
    private val simplified = File("src/main/res/values-b+zh+Hans/strings.xml").readText()
    private val english = File("src/main/res/values-en/strings.xml").readText()

    @Test
    fun applicationAndMainActivityOwnAutomaticAndFlexibleUpdateLifecycle() {
        assertTrue(applicationSource.contains("AppUpdateRuntime.initialize"))
        assertTrue(mainActivitySource.contains("UpdateCheckTrigger.AUTOMATIC"))
        assertTrue(mainActivitySource.contains("registerForActivityResult"))
        assertTrue(mainActivitySource.contains("StartIntentSenderForResult"))
        assertTrue(mainActivitySource.contains("startFlexibleUpdate"))
        assertTrue(mainActivitySource.contains("refreshPlayInstallStatus"))
        assertTrue(mainActivitySource.contains("reloadPersistedState"))
        assertTrue(mainActivitySource.contains("completePlayUpdate"))
        assertTrue(mainActivitySource.contains("setAnchorView(topLevelNav)"))
        assertTrue(mainActivitySource.contains("R.id.snackbar_text"))
        assertTrue(mainActivitySource.contains("R.id.snackbar_action"))
        assertTrue(mainActivitySource.contains("state.lastFailure == null"))
    }

    @Test
    fun updateDialogOwnsApprovedHierarchyActionOrderAndStyles() {
        val titleIndex = updateDialogSource.indexOf("@+id/updatePromptTitle")
        val versionIndex = updateDialogSource.indexOf("@+id/updatePromptVersion")
        val messageIndex = updateDialogSource.indexOf("@+id/updatePromptMessage")
        val laterIndex = updateDialogSource.indexOf("@+id/updatePromptLaterButton")
        val skipIndex = updateDialogSource.indexOf("@+id/updatePromptSkipButton")
        val updateIndex = updateDialogSource.indexOf("@+id/updatePromptUpdateButton")

        assertTrue(titleIndex >= 0)
        assertTrue(titleIndex < versionIndex)
        assertTrue(versionIndex < messageIndex)
        assertTrue(messageIndex < laterIndex)
        assertTrue(laterIndex < skipIndex)
        assertTrue(skipIndex < updateIndex)
        assertTrue(updateDialogSource.contains("@string/update_prompt_title"))
        assertTrue(updateDialogSource.contains("@string/update_prompt_message"))
        assertTrue(updateDialogSource.contains("android:orientation=\"horizontal\""))
        assertTrue(updateDialogSource.contains("android:layout_weight=\"1\""))
        assertTrue(updateDialogSource.contains("android:minHeight=\"48dp\""))
        assertTrue(updateDialogSource.contains("android:singleLine=\"false\""))
        assertTrue(
            updateDialogSource.contains(
                "@style/Widget.BusIsComing.UpdatePrompt.Button.Tonal"
            )
        )
        assertTrue(
            themeSource.contains(
                "<style name=\"ThemeOverlay.BusIsComing.UpdatePrompt\""
            )
        )
        assertTrue(themeSource.contains("<item name=\"cornerSize\">16dp</item>"))
        assertTrue(themeSource.contains("<item name=\"cornerRadius\">8dp</item>"))
    }

    @Test
    fun settingsRendersReliableStateAndAccessibleRedDot() {
        assertTrue(settingsSource.contains("settingsUpdateSummary"))
        assertTrue(settingsSource.contains("settingsUpdateDot"))
        assertTrue(settingsSource.contains("snapshot.hasNewerVersion"))
        assertTrue(settingsSource.contains("contentDescription"))
        assertTrue(settingsSource.contains("UpdateSnapshotState.NEVER_CHECKED"))
        assertTrue(settingsSource.contains("UpdateSnapshotState.UP_TO_DATE"))
        assertTrue(settingsSource.contains("UpdateSnapshotState.UPDATE_AVAILABLE"))
    }

    @Test
    fun allUpdateStringsExistInThreeIndependentLocales() {
        val names = listOf(
            "update_status_never_checked",
            "update_status_checking",
            "update_status_up_to_date",
            "update_status_available",
            "update_status_failed",
            "update_prompt_title",
            "update_prompt_version",
            "update_prompt_message",
            "update_action_update",
            "update_action_later",
            "update_action_skip",
            "update_downloaded_action",
            "update_dot_description"
        )
        names.forEach { name ->
            assertTrue("Traditional missing $name", traditional.contains("name=\"$name\""))
            assertTrue("Simplified missing $name", simplified.contains("name=\"$name\""))
            assertTrue("English missing $name", english.contains("name=\"$name\""))
        }

        assertEquals("版本 %1\$s", stringValue(traditional, "update_prompt_version"))
        assertEquals("版本 %1\$s", stringValue(simplified, "update_prompt_version"))
        assertEquals("Version %1\$s", stringValue(english, "update_prompt_version"))
        assertEquals(
            "新版本已可下載。你可以現在更新，或稍後再處理。",
            stringValue(traditional, "update_prompt_message")
        )
        assertEquals(
            "新版本已可下载。你可以现在更新，或稍后再处理。",
            stringValue(simplified, "update_prompt_message")
        )
        assertEquals(
            "A new version is ready to download. You can update now or come back to it later.",
            stringValue(english, "update_prompt_message")
        )
    }

    @Test
    fun playListingFallsBackFromMarketToHttpsWithoutUsingWebsite() {
        val attempts = mutableListOf<ExternalUpdateTarget>()
        val failures = mutableListOf<Int>()
        val success = AppUpdateExternalActions.openPlayListing(
            context = ContextWrapper(null),
            starter = { _, destination ->
                attempts += destination
                if (attempts.size == 1) throw ActivityNotFoundException()
            },
            toaster = { _, message -> failures += message }
        )

        assertTrue(success)
        assertEquals("market://details?id=com.golink.busiscoming", attempts[0].url)
        assertEquals("com.android.vending", attempts[0].packageName)
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.golink.busiscoming",
            attempts[1].url
        )
        assertTrue(attempts.none { it.url.contains("www.busiscoming.com") })
        assertTrue(failures.isEmpty())
    }

    @Test
    fun websiteActionUsesFixedLanguagePageAndNeverMetadataDownloadUrl() {
        var started: ExternalUpdateTarget? = null
        val success = AppUpdateExternalActions.openWebsiteDownloadPage(
            context = ContextWrapper(null),
            language = AppLanguage.ENGLISH,
            starter = { _, destination -> started = destination },
            toaster = { _, _ -> }
        )

        assertTrue(success)
        assertEquals("https://www.busiscoming.com/en/#download", started?.url)
        assertEquals(null, started?.packageName)
    }

    private fun stringValue(source: String, name: String): String {
        return Regex("""<string name="$name">([^<]*)</string>""")
            .find(source)
            ?.groupValues
            ?.get(1)
            ?: error("Missing string $name")
    }
}
