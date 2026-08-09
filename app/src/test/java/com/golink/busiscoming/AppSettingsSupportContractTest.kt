package com.golink.busiscoming

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import com.golink.busiscoming.ui.settings.AppSupportActions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsSupportContractTest {
    private val manifestXml = File("src/main/AndroidManifest.xml").readText()
    private val settingsFragmentLayoutXml =
        File("src/main/res/layout/fragment_settings.xml").readText()
    private val settingsFragmentKt =
        File("src/main/java/com/golink/busiscoming/ui/main/SettingsFragment.kt").readText()
    private val aboutLayoutXml = File("src/main/res/layout/activity_about.xml").readText()
    private val stringsXml = File("src/main/res/values/strings.xml").readText()
    private val valuesV26ThemesXml = File("src/main/res/values-v26/themes.xml").readText()
    private val aboutActivityKt =
        File("src/main/java/com/golink/busiscoming/ui/settings/AboutActivity.kt").readText()
    private val actionsKt =
        File("src/main/java/com/golink/busiscoming/ui/settings/AppSupportActions.kt").readText()
    private val localizedAboutStrings = listOf(
        File("src/main/res/values/strings.xml").readText() to
            "BusIsComing 為香港巴士通勤而設，助你比較 Citybus 路線與實時到站時間，更好掌握出發時機。\\n\\n你亦可儲存常用行程、查看地圖詳情及啟用通知欄監察。",
        File("src/main/res/values-b+zh+Hans/strings.xml").readText() to
            "BusIsComing 为香港公交通勤而设计，帮助你比较 Citybus 路线和实时到站时间，更好地掌握出发时机。\\n\\n你还可以保存常用行程、查看地图详情并启用通知栏监控。",
        File("src/main/res/values-en/strings.xml").readText() to
            "BusIsComing is built for Hong Kong bus commuters. Compare Citybus routes and live arrivals to choose a better time to leave.\\n\\nYou can also save regular journeys, view route details on the map, and monitor arrivals from your notifications."
    )
    private val localizedShareStrings = listOf(
        File("src/main/res/values/strings_runtime.xml").readText() to
            "用 BusIsComing 比較 Citybus 路線與實時到站時間，掌握更合適的出發時機。\\n\\nGoogle Play 下載：%1\$s\\n官方網站下載：%2\$s",
        File("src/main/res/values-b+zh+Hans/strings_runtime.xml").readText() to
            "使用 BusIsComing 比较 Citybus 路线和实时到站时间，掌握更合适的出发时机。\\n\\nGoogle Play 下载：%1\$s\\n官方网站下载：%2\$s",
        File("src/main/res/values-en/strings_runtime.xml").readText() to
            "Use BusIsComing to compare Citybus routes and live arrivals, so you can choose a better time to leave.\\n\\nDownload on Google Play: %1\$s\\nDownload from the official website: %2\$s"
    )

    @Test
    fun manifestKeepsSettingsTopLevelAndDeclaresOnlyItsSecondaryActivities() {
        assertFalse(manifestXml.contains(".ui.settings.SettingsActivity"))
        assertTrue(manifestXml.contains(".ui.settings.AboutActivity"))
        assertTrue(manifestXml.contains(".ui.settings.RouteTransferActivity"))
        assertTrue(manifestXml.contains("android:exported=\"false\""))
        assertFalse(manifestXml.contains("READ_EXTERNAL_STORAGE"))
        assertFalse(manifestXml.contains("WRITE_EXTERNAL_STORAGE"))
        assertFalse(manifestXml.contains("MANAGE_EXTERNAL_STORAGE"))
    }

    @Test
    fun settingsPageUsesGroupedLowFrequencyEntries() {
        assertTrue(settingsFragmentLayoutXml.contains("android:id=\"@+id/settingsRoot\""))
        assertTrue(settingsFragmentLayoutXml.contains("android:background=\"@drawable/app_page_background\""))
        assertTrue(settingsFragmentLayoutXml.contains("android:text=\"@string/settings\""))
        assertTrue(settingsFragmentLayoutXml.contains("android:text=\"@string/app_name\""))
        assertTrue(settingsFragmentLayoutXml.contains("android:id=\"@+id/settingsVersionText\""))
        assertTrue(settingsFragmentLayoutXml.contains("android:text=\"@string/settings_group_preferences\""))
        assertTrue(settingsFragmentLayoutXml.contains("android:text=\"@string/settings_group_route_data\""))
        assertTrue(settingsFragmentLayoutXml.contains("android:text=\"@string/settings_group_support\""))
        assertTrue(settingsFragmentLayoutXml.contains("android:text=\"@string/settings_group_about\""))
        assertEntry("settingsLanguageRow", "settings_language")
        assertEntry("settingsRouteTransferRow", "settings_route_transfer")
        assertEntry("settingsTransitCodeShortcutRow", "settings_transit_code_shortcut")
        assertTrue(settingsFragmentLayoutXml.contains("@string/settings_transit_code_shortcut_summary"))
        assertEntry("settingsShareRow", "settings_share_app")
        assertEntry("settingsFeedbackRow", "settings_feedback")
        assertEntry("settingsRatingRow", "settings_rate_app")
        assertEntry("settingsUpdateRow", "settings_check_update")
        assertTrue(settingsFragmentLayoutXml.contains("@+id/settingsUpdateSummary"))
        assertTrue(settingsFragmentLayoutXml.contains("@+id/settingsUpdateDot"))
        assertTrue(settingsFragmentLayoutXml.contains("@drawable/app_update_dot"))
        assertEntry("settingsAboutRow", "settings_about_us")
        assertEntry("settingsPrivacyRow", "settings_privacy_policy")
        assertFalse(settingsFragmentLayoutXml.contains("首頁"))
        val preferencesIndex = settingsFragmentLayoutXml.indexOf("@string/settings_group_preferences")
        val routeDataIndex = settingsFragmentLayoutXml.indexOf("@string/settings_group_route_data")
        val supportIndex = settingsFragmentLayoutXml.indexOf("@string/settings_group_support")
        assertTrue(preferencesIndex < routeDataIndex)
        assertTrue(routeDataIndex < supportIndex)
        assertEquals(1, Regex("@\\+id/settingsRouteTransferRow").findAll(settingsFragmentLayoutXml).count())
    }

    @Test
    fun topLevelSettingsStartsWithAppearanceThenLanguageAndShowsCurrentValues() {
        val preferencesIndex =
            settingsFragmentLayoutXml.indexOf("@string/settings_group_preferences")
        val appearanceIndex = settingsFragmentLayoutXml.indexOf("@+id/settingsAppearanceRow")
        val languageIndex = settingsFragmentLayoutXml.indexOf("@+id/settingsLanguageRow")
        val routeDataIndex =
            settingsFragmentLayoutXml.indexOf("@string/settings_group_route_data")
        val supportIndex = settingsFragmentLayoutXml.indexOf("@string/settings_group_support")
        val aboutIndex = settingsFragmentLayoutXml.indexOf("@string/settings_group_about")

        assertTrue(preferencesIndex >= 0)
        assertTrue(preferencesIndex < appearanceIndex)
        assertTrue(appearanceIndex < languageIndex)
        assertTrue(languageIndex < routeDataIndex)
        assertTrue(routeDataIndex < supportIndex)
        assertTrue(supportIndex < aboutIndex)
        assertTrue(settingsFragmentLayoutXml.contains("@+id/settingsAppearanceValue"))
        assertTrue(settingsFragmentLayoutXml.contains("@+id/settingsLanguageValue"))
        assertTrue(settingsFragmentLayoutXml.contains("android:minHeight=\"48dp\""))
        assertTrue(settingsFragmentKt.contains("AppThemeMode.SYSTEM"))
        assertTrue(settingsFragmentKt.contains("AppThemeMode.LIGHT"))
        assertTrue(settingsFragmentKt.contains("AppThemeMode.DARK"))
        assertTrue(settingsFragmentKt.contains("setSingleChoiceItems"))
    }

    @Test
    fun api26SettingsTitlesDoNotRetainHorizontalWeightInsideVerticalRows() {
        val style = valuesV26ThemesXml
            .substringAfter("<style name=\"SettingsRowText\">")
            .substringBefore("</style>")

        assertFalse(style.contains("android:layout_width\">0dp"))
        assertFalse(style.contains("android:layout_weight"))
        assertTrue(style.contains("android:layout_width\">match_parent"))
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
        assertTrue(actionsKt.contains("snapshot().websitePath"))
        assertTrue(actionsKt.contains("snapshot().privacyPath"))
        assertTrue(actionsKt.contains("hezhenyu966@gmail.com"))
        assertTrue(actionsKt.contains("R.string.share_copy"))
        assertTrue(actionsKt.contains("R.string.feedback_body"))
        assertTrue(stringsXml.contains("name=\"rating_play_disabled_title\""))
        assertTrue(stringsXml.contains("name=\"rating_play_missing_title\""))
        assertTrue(stringsXml.contains("name=\"rating_play_unusable_title\""))
        assertTrue(stringsXml.contains("name=\"rating_external_navigation_failed\""))
        assertFalse(stringsXml.contains("name=\"unsupported_check_update\""))
        assertTrue(stringsXml.contains("name=\"update_status_never_checked\""))
        assertTrue(stringsXml.contains("暫時無法分享應用"))
        assertTrue(stringsXml.contains("暫時無法開啟問題反饋"))
        assertTrue(stringsXml.contains("暫時無法開啟隱私政策"))
        assertTrue(stringsXml.contains("暫時無法開啟網站"))
    }

    @Test
    fun aboutAndShareCopyUseTheApprovedConciseThreeLanguageContract() {
        localizedAboutStrings.forEach { (xml, expected) ->
            assertEquals(expected, stringValue(xml, "about_description"))
        }
        localizedShareStrings.forEach { (xml, expected) ->
            val actual = stringValue(xml, "share_copy")
            assertEquals(expected, actual)
            assertTrue(actual.indexOf("%1\$s") < actual.indexOf("%2\$s"))
        }

        val shareTextBlock = actionsKt
            .substringAfter("fun shareText")
            .substringBefore("fun shareApp")
        assertTrue(shareTextBlock.contains("AppUpdateLinks.PLAY_HTTPS_URL"))
        assertTrue(shareTextBlock.contains("websiteDownloadUrl(context)"))
        assertTrue(actionsKt.contains("AppUpdateLinks.websiteDownloadPage"))
    }

    @Test
    fun topLevelSettingsAndSecondaryAboutPageWireTheirActions() {
        assertTrue(settingsFragmentKt.contains("settingsLanguageRow"))
        assertTrue(settingsFragmentKt.contains("settingsRouteTransferRow"))
        assertTrue(settingsFragmentKt.contains("settingsTransitCodeShortcutRow"))
        assertTrue(settingsFragmentKt.contains("TransitCodeShortcutManager.requestPinnedShortcut"))
        assertTrue(settingsFragmentKt.contains("RouteTransferActivity::class.java"))
        assertTrue(settingsFragmentKt.contains("AppLanguageChoice.FOLLOW_SYSTEM"))
        assertTrue(settingsFragmentKt.contains("AppLanguageChoice.TRADITIONAL_CHINESE"))
        assertTrue(settingsFragmentKt.contains("AppLanguageChoice.SIMPLIFIED_CHINESE"))
        assertTrue(settingsFragmentKt.contains("AppLanguageChoice.ENGLISH"))
        assertTrue(settingsFragmentKt.contains("GooglePlayRatingRuntime.availabilityResolver"))
        assertTrue(settingsFragmentKt.contains("GooglePlayRatingRuntime.navigatorFactory"))
        assertFalse(settingsFragmentKt.contains("unsupported_rate_app"))
        assertFalse(settingsFragmentKt.contains("unsupported_check_update"))
        assertTrue(settingsFragmentKt.contains("UpdateCheckTrigger.MANUAL"))
        assertTrue(settingsFragmentKt.contains("AppUpdateRuntime.coordinator"))
        assertTrue(settingsFragmentKt.contains("shareApp(requireContext())"))
        assertTrue(settingsFragmentKt.contains("sendFeedback(requireContext())"))
        assertTrue(settingsFragmentKt.contains("openPrivacyPolicy(requireContext())"))
        assertTrue(settingsFragmentKt.contains("AboutActivity::class.java"))
        assertTrue(aboutActivityKt.contains("openWebsite(this)"))
        assertTrue(settingsFragmentKt.contains("BuildConfig.VERSION_NAME"))
        assertTrue(aboutActivityKt.contains("BuildConfig.VERSION_NAME"))
        assertFalse(stringsXml.contains("unsupported_language_switch"))
    }

    @Test
    fun transitCodeShortcutUsesXiaomiPermissionRecoveryWithoutFalseConfirmationCopy() {
        assertTrue(settingsFragmentLayoutXml.contains("@+id/settingsTransitCodeShortcutValue"))
        assertTrue(settingsFragmentKt.contains("override fun onResume()"))
        assertTrue(settingsFragmentKt.contains("TransitCodeShortcutManager.currentState"))
        assertTrue(settingsFragmentKt.contains("TransitCodeShortcutRequestResult.NEEDS_PERMISSION"))
        assertTrue(settingsFragmentKt.contains("XiaomiShortcutPermissionNavigator"))
        assertTrue(settingsFragmentKt.contains("ActivityResultContracts.StartActivityForResult"))
        assertTrue(settingsFragmentKt.contains("shortcutPermissionSettingsLauncher"))
        assertFalse(settingsFragmentKt.contains("consumePermissionNavigationPending"))
        assertFalse(settingsFragmentKt.contains("markPermissionNavigationPending"))
        assertTrue(settingsFragmentKt.contains("transit_code_shortcut_already_added"))
        assertTrue(stringsXml.contains("name=\"transit_code_shortcut_added\""))
        assertTrue(stringsXml.contains("name=\"transit_code_shortcut_unsupported_guide\""))
        assertTrue(stringsXml.contains("name=\"transit_code_shortcut_permission_required\""))
        assertTrue(stringsXml.contains("桌面快捷方式"))
        assertFalse(settingsFragmentKt.contains("transit_code_shortcut_confirm_system"))
    }

    private fun assertEntry(rowId: String, stringName: String) {
        val rowRef = "@+id/$rowId"
        val textRef = "@string/$stringName"
        assertTrue("Missing row $rowId", settingsFragmentLayoutXml.contains(rowRef))
        assertTrue("Missing text $stringName", settingsFragmentLayoutXml.contains(textRef))
    }

    private fun stringValue(xml: String, name: String): String =
        xml.substringAfter("<string name=\"$name\">").substringBefore("</string>")
}

class AppSupportActionsTest {
    @Test
    fun supportCopyAndLinksAreResolvedFromCurrentLocale() {
        val source = File(
            "src/main/java/com/golink/busiscoming/ui/settings/AppSupportActions.kt"
        ).readText()
        assertTrue(source.contains("context.getString(R.string.share_copy"))
        assertTrue(source.contains("context.getString(\n            R.string.feedback_body"))
        assertTrue(source.contains("AppLanguageRepository(context).snapshot()"))
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
