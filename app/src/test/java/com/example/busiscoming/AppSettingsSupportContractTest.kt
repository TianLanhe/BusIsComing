package com.example.busiscoming

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import com.example.busiscoming.ui.settings.AppSupportActions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsSupportContractTest {
    private val manifestXml = File("src/main/AndroidManifest.xml").readText()
    private val settingsLayoutXml = File("src/main/res/layout/activity_settings.xml").readText()
    private val aboutLayoutXml = File("src/main/res/layout/activity_about.xml").readText()
    private val stringsXml = File("src/main/res/values/strings.xml").readText()
    private val settingsActivityKt =
        File("src/main/java/com/example/busiscoming/ui/settings/SettingsActivity.kt").readText()
    private val aboutActivityKt =
        File("src/main/java/com/example/busiscoming/ui/settings/AboutActivity.kt").readText()
    private val actionsKt =
        File("src/main/java/com/example/busiscoming/ui/settings/AppSupportActions.kt").readText()

    @Test
    fun manifestDeclaresSettingsAndAboutActivities() {
        assertTrue(manifestXml.contains(".ui.settings.SettingsActivity"))
        assertTrue(manifestXml.contains(".ui.settings.AboutActivity"))
        assertTrue(manifestXml.contains("android:exported=\"false\""))
    }

    @Test
    fun settingsPageUsesGroupedLowFrequencyEntries() {
        assertTrue(settingsLayoutXml.contains("android:id=\"@+id/settingsRoot\""))
        assertTrue(settingsLayoutXml.contains("android:background=\"@drawable/app_page_background\""))
        assertTrue(settingsLayoutXml.contains("android:text=\"@string/settings\""))
        assertTrue(settingsLayoutXml.contains("android:text=\"@string/app_name\""))
        assertTrue(settingsLayoutXml.contains("android:id=\"@+id/settingsVersionText\""))
        assertTrue(settingsLayoutXml.contains("android:text=\"@string/settings_group_preferences\""))
        assertTrue(settingsLayoutXml.contains("android:text=\"@string/settings_group_support\""))
        assertTrue(settingsLayoutXml.contains("android:text=\"@string/settings_group_about\""))
        assertEntry("settingsLanguageRow", "settings_language")
        assertEntry("settingsShareRow", "settings_share_app")
        assertEntry("settingsFeedbackRow", "settings_feedback")
        assertEntry("settingsRatingRow", "settings_rate_app")
        assertEntry("settingsUpdateRow", "settings_check_update")
        assertEntry("settingsAboutRow", "settings_about_us")
        assertEntry("settingsPrivacyRow", "settings_privacy_policy")
        assertFalse(settingsLayoutXml.contains("首頁"))
    }

    @Test
    fun aboutPageShowsAppInfoAndWebsite() {
        assertTrue(aboutLayoutXml.contains("android:id=\"@+id/aboutRoot\""))
        assertTrue(aboutLayoutXml.contains("android:text=\"@string/settings_about_us\""))
        assertTrue(aboutLayoutXml.contains("android:text=\"@string/app_name\""))
        assertTrue(aboutLayoutXml.contains("android:id=\"@+id/aboutVersionText\""))
        assertTrue(aboutLayoutXml.contains("android:text=\"@string/about_description\""))
        assertTrue(aboutLayoutXml.contains("android:text=\"@string/website_url\""))
    }

    @Test
    fun appSupportActionsCentralizeUrlsCopyAndToasts() {
        assertTrue(actionsKt.contains("https://www.busiscoming.com"))
        assertTrue(actionsKt.contains("https://www.busiscoming.com/zh-hant/privacy/"))
        assertTrue(actionsKt.contains("hezhenyu966@gmail.com"))
        assertTrue(actionsKt.contains("搭巴士前想快一點知道哪條路線合適？"))
        assertTrue(stringsXml.contains("暫不支援語言切換"))
        assertTrue(stringsXml.contains("暫不支援應用評分"))
        assertTrue(stringsXml.contains("暫不支援檢查更新"))
        assertTrue(stringsXml.contains("暫時無法分享應用"))
        assertTrue(stringsXml.contains("暫時無法開啟問題反饋"))
        assertTrue(stringsXml.contains("暫時無法開啟隱私政策"))
        assertTrue(stringsXml.contains("暫時無法開啟網站"))
    }

    @Test
    fun activitiesWirePlaceholderAndExternalActions() {
        assertTrue(settingsActivityKt.contains("settingsLanguageRow"))
        assertTrue(settingsActivityKt.contains("unsupported_language_switch"))
        assertTrue(settingsActivityKt.contains("unsupported_rate_app"))
        assertTrue(settingsActivityKt.contains("unsupported_check_update"))
        assertTrue(settingsActivityKt.contains("shareApp(this)"))
        assertTrue(settingsActivityKt.contains("sendFeedback(this)"))
        assertTrue(settingsActivityKt.contains("openPrivacyPolicy(this)"))
        assertTrue(settingsActivityKt.contains("AboutActivity::class.java"))
        assertTrue(aboutActivityKt.contains("openWebsite(this)"))
        assertTrue(settingsActivityKt.contains("BuildConfig.VERSION_NAME"))
        assertTrue(aboutActivityKt.contains("BuildConfig.VERSION_NAME"))
    }

    private fun assertEntry(rowId: String, stringName: String) {
        val rowRef = "@+id/$rowId"
        val textRef = "@string/$stringName"
        assertTrue("Missing row $rowId", settingsLayoutXml.contains(rowRef))
        assertTrue("Missing text $stringName", settingsLayoutXml.contains(textRef))
    }
}

class AppSupportActionsTest {
    @Test
    fun shareTextUsesChosenCopyAndWebsite() {
        assertTrue(AppSupportActions.shareText.contains("搭巴士前想快一點知道哪條路線合適？"))
        assertTrue(AppSupportActions.shareText.contains("BusIsComing"))
        assertTrue(AppSupportActions.shareText.endsWith(AppSupportActions.websiteUrl))
    }

    @Test
    fun feedbackBodyIncludesDiagnosticsAndPrompt() {
        val body = AppSupportActions.feedbackBody(
            appVersion = "1.2.3",
            androidVersion = "36",
            deviceModel = "Pixel Test"
        )

        assertTrue(body.contains("App 版本：1.2.3"))
        assertTrue(body.contains("Android 版本：36"))
        assertTrue(body.contains("設備型號：Pixel Test"))
        assertTrue(body.contains("問題描述："))
    }

    @Test
    fun startOrToastReportsFallbackWhenNoActivityCanHandleIntent() {
        val fallbackMessages = mutableListOf<Int>()
        val started = AppSupportActions.startOrToast(
            context = ContextWrapper(null),
            intent = Intent(Intent.ACTION_VIEW),
            failureMessageRes = R.string.privacy_policy_failed,
            starter = { _, _ -> throw ActivityNotFoundException() },
            toaster = { _, messageRes -> fallbackMessages.add(messageRes) }
        )

        assertFalse(started)
        assertEquals(listOf(R.string.privacy_policy_failed), fallbackMessages)
    }

    @Test
    fun startOrToastDoesNotShowFallbackAfterSuccessfulStart() {
        val fallbackMessages = mutableListOf<Int>()
        val started = AppSupportActions.startOrToast(
            context = ContextWrapper(null),
            intent = Intent(Intent.ACTION_VIEW),
            failureMessageRes = R.string.privacy_policy_failed,
            starter = { _, _ -> },
            toaster = { _, messageRes -> fallbackMessages.add(messageRes) }
        )

        assertTrue(started)
        assertTrue(fallbackMessages.isEmpty())
    }
}
