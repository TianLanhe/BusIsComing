package com.example.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainRouteSelectionLayoutTest {
    private val layoutXml = File("src/main/res/layout/activity_main.xml").readText()
    private val stringsXml = File("src/main/res/values/strings.xml").readText()
    private val manifestXml = File("src/main/AndroidManifest.xml").readText()
    private val mainActivityKt =
        File("src/main/java/com/example/busiscoming/ui/main/MainActivity.kt").readText()

    @Test
    fun exposesAllEntryAndTemporaryQueryContextBar() {
        assertTrue(layoutXml.contains("android:id=\"@+id/routePickerButton\""))
        assertTrue(layoutXml.contains("android:text=\"全部\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/transitCodeButton\""))
        assertTrue(layoutXml.contains("android:text=\"乘車碼\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/temporaryQueryContextBar\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/temporaryQueryContextPathText\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/temporaryQueryEditButton\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/temporaryQuerySaveButton\""))
    }

    @Test
    fun topBarShowsTransitCodeAndSettingsWithoutBusQueryTitleOrTopManageButton() {
        val transitCodeButton = xmlBlockFor("@+id/transitCodeButton")
        val settingsButton = xmlBlockFor("@+id/settingsButton")

        assertFalse(layoutXml.contains("android:text=\"巴士查詢\""))
        assertFalse(layoutXml.contains("android:id=\"@+id/manageRoutesButton\""))
        assertTrue(transitCodeButton.contains("android:text=\"乘車碼\""))
        assertTrue(settingsButton.contains("android:contentDescription=\"@string/settings\""))
        assertTrue(settingsButton.contains("app:icon=\"@drawable/ic_settings_outline\""))
        assertTrue(transitCodeButton.contains("app:backgroundTint=\"@color/bus_chip_selected\""))
        assertTrue(settingsButton.contains("app:backgroundTint=\"@color/white\""))
        assertTrue(settingsButton.contains("app:strokeColor=\"@color/bus_divider\""))
        assertTrue(settingsButton.contains("app:strokeWidth=\"1dp\""))
        assertTrue(transitCodeButton.contains("app:cornerRadius=\"6dp\""))
        assertTrue(settingsButton.contains("app:cornerRadius=\"22dp\""))
        assertFalse(transitCodeButton.contains("Widget.MaterialComponents.Button.TextButton"))
    }

    @Test
    fun firstRunTopActionsAlsoExposeSettings() {
        val firstRunActions = xmlContainerForId("@+id/firstRunTopActions")

        assertTrue(firstRunActions.contains("android:gravity=\"center_vertical\""))
        assertTrue(firstRunActions.contains("@+id/firstRunTransitCodeButton"))
        assertTrue(firstRunActions.contains("@+id/firstRunSettingsButton"))
        assertTrue(xmlBlockFor("@+id/firstRunSettingsButton").contains("android:contentDescription=\"@string/settings\""))
        assertTrue(xmlBlockFor("@+id/firstRunSettingsButton").contains("app:icon=\"@drawable/ic_settings_outline\""))
    }

    @Test
    fun frequentRoutesHeaderKeepsAllAndAddsCompactManageIcon() {
        val header = xmlContainerForId("@+id/frequentRoutesHeader")
        val pickerButton = xmlBlockFor("@+id/routePickerButton")
        val manageButton = xmlBlockFor("@+id/routeManageIconButton")

        assertTrue(header.contains("@+id/routePickerButton"))
        assertTrue(header.contains("@+id/routeManageIconButton"))
        assertTrue(pickerButton.contains("android:text=\"全部\""))
        assertTrue(manageButton.contains("android:contentDescription=\"@string/manage_routes\""))
        assertTrue(manageButton.contains("app:icon=\"@drawable/ic_route_manage\""))
        assertTrue(manageButton.contains("android:layout_marginStart=\"6dp\""))
        assertFalse(header.contains("android:text=\"管理路線\""))
    }

    @Test
    fun firstRunStateUsesWarmCopySampleCardAndGradientBackground() {
        assertTrue(layoutXml.contains("android:background=\"@drawable/app_page_background\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/firstRunTopActions\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/firstRunTransitCodeButton\""))
        assertTrue(xmlBlockForId("@+id/firstRunTransitCodeButton").contains("android:layout_height=\"44dp\""))
        assertTrue(xmlBlockForId("@+id/firstRunTransitCodeButton").contains("android:textSize=\"14sp\""))
        assertTrue(layoutXml.contains("app:elevation=\"0dp\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/firstRunHeadlineText\""))
        assertTrue(layoutXml.contains("android:text=\"@string/first_run_home_headline\""))
        assertTrue(xmlBlockForId("@+id/firstRunHeadlineText").contains("android:gravity=\"start\""))
        assertTrue(xmlBlockForId("@+id/firstRunHeadlineText").contains("android:textSize=\"27sp\""))
        assertTrue(xmlBlockForId("@+id/firstRunHeadlineText").contains("android:maxLines=\"2\""))
        assertTrue(layoutXml.contains("android:paddingTop=\"78dp\""))
        assertTrue(stringsXml.contains("把常走的路線放在這裡，\\n出門前一按即查。"))
        assertTrue(layoutXml.contains("android:id=\"@+id/firstRunSampleLabelText\""))
        assertTrue(stringsXml.contains("示例預覽"))
        assertFalse(layoutXml.contains("不可點擊"))
        assertFalse(xmlBlockForId("@+id/firstRunSampleLabelText").contains("android:background="))
        assertTrue(layoutXml.contains("android:id=\"@+id/firstRunSampleRouteCard\""))
        assertTrue(layoutXml.contains("layout=\"@layout/item_bus_route\""))
        assertTrue(layoutXml.contains("android:text=\"@string/first_run_add_route\""))
        assertTrue(layoutXml.contains("android:text=\"@string/first_run_temporary_query\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/firstRunActionGroup\""))
        assertTrue(xmlBlockForId("@+id/firstRunActionGroup").contains("android:layout_width=\"wrap_content\""))
        assertTrue(xmlBlockForId("@+id/firstRunActionGroup").contains("android:layout_gravity=\"center_horizontal\""))
        assertTrue(xmlBlockForId("@+id/emptyAddRouteButton").contains("android:layout_width=\"wrap_content\""))
        assertTrue(xmlBlockForId("@+id/emptyTemporaryQueryButton").contains("android:layout_width=\"wrap_content\""))
        assertTrue(stringsXml.contains("新增常用路線"))
        assertTrue(stringsXml.contains("直接查詢一次"))
    }

    @Test
    fun firstRunPolicyHidesManageRouteAndKeepsTemporaryResultsVisible() {
        assertTrue(mainActivityKt.contains("private fun renderHomeShell()"))
        assertTrue(mainActivityKt.contains("normalTopActions.visibility = if (isFirstRun) View.GONE else View.VISIBLE"))
        assertTrue(mainActivityKt.contains("firstRunTopActions.visibility = if (isFirstRun) View.VISIBLE else View.GONE"))
        assertTrue(mainActivityKt.contains("routeManageIconButton.visibility = if (routeConfigs.isEmpty()) View.GONE else View.VISIBLE"))
        assertTrue(mainActivityKt.contains("resultSection.visibility = if (routeConfigs.isEmpty() && isFirstRun) View.GONE else View.VISIBLE"))
        assertTrue(mainActivityKt.contains("currentQueryContext == null"))
        assertTrue(mainActivityKt.contains("FirstRunRoutePreview.route()"))
    }

    @Test
    fun mainTransitCodeClickUsesFormalLauncherNotExperimentalSheet() {
        assertTrue(mainActivityKt.contains("TransitCodePaymentLauncher.forActivity(this)"))
        assertTrue(mainActivityKt.contains("launchTransitCode()"))
        assertTrue(mainActivityKt.contains("transit_code_launch_failed"))
        assertFalse(mainActivityKt.contains("transitCodeBottomSheet.show()"))
    }

    @Test
    fun manifestDeclaresPaymentWalletPackageVisibility() {
        assertTrue(manifestXml.contains("<package android:name=\"hk.alipay.wallet\" />"))
        assertTrue(manifestXml.contains("<package android:name=\"com.eg.android.AlipayGphone\" />"))
    }

    @Test
    fun mainActivityWiresSettingsRouteManagementAndTemporaryEdit() {
        assertTrue(mainActivityKt.contains("openSettings()"))
        assertTrue(mainActivityKt.contains("SettingsActivity::class.java"))
        assertTrue(mainActivityKt.contains("routeManageIconButton.setOnClickListener"))
        assertTrue(mainActivityKt.contains("temporaryQueryEditButton.setOnClickListener"))
        assertTrue(mainActivityKt.contains("editCurrentTemporaryQuery()"))
        assertTrue(mainActivityKt.contains("showTemporaryRouteSheet(context.origin, context.destination)"))
        assertTrue(mainActivityKt.contains("temporaryRouteBottomSheet.show(origin, destination)"))
    }

    @Test
    fun doesNotShowStandaloneSortTitle() {
        assertFalse(layoutXml.contains("android:text=\"排序\""))
    }

    @Test
    fun resultListUsesFixedRefreshOverlayContainer() {
        assertTrue(layoutXml.contains("android:id=\"@+id/resultListContainer\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/resultRefreshOverlay\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/resultRefreshProgress\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/resultRefreshSuccess\""))
        assertTrue(layoutXml.contains("android:clickable=\"false\""))
        assertTrue(layoutXml.contains("android:focusable=\"false\""))
    }

    private fun xmlBlockFor(id: String): String {
        val idIndex = layoutXml.indexOf("android:id=\"$id\"")
        assertTrue("Missing view id $id", idIndex >= 0)
        val start = layoutXml.lastIndexOf("<com.google.android.material.button.MaterialButton", idIndex)
        assertTrue("Missing MaterialButton start for $id", start >= 0)
        val end = layoutXml.indexOf("/>", idIndex)
        assertTrue("Missing MaterialButton end for $id", end >= 0)
        return layoutXml.substring(start, end)
    }

    private fun xmlBlockForId(id: String): String {
        val idIndex = layoutXml.indexOf("android:id=\"$id\"")
        assertTrue("Missing view id $id", idIndex >= 0)
        val start = layoutXml.lastIndexOf("<", idIndex)
        assertTrue("Missing view start for $id", start >= 0)
        val selfClose = layoutXml.indexOf("/>", idIndex)
        val nestedClose = layoutXml.indexOf(">", idIndex)
        val end = if (selfClose >= 0 && selfClose < nestedClose + 2000) selfClose else nestedClose
        assertTrue("Missing view end for $id", end >= 0)
        return layoutXml.substring(start, end)
    }

    private fun xmlContainerForId(id: String): String {
        val idIndex = layoutXml.indexOf("android:id=\"$id\"")
        assertTrue("Missing view id $id", idIndex >= 0)
        val start = layoutXml.lastIndexOf("<LinearLayout", idIndex)
        assertTrue("Missing LinearLayout start for $id", start >= 0)
        val end = layoutXml.indexOf("</LinearLayout>", idIndex)
        assertTrue("Missing LinearLayout end for $id", end >= 0)
        return layoutXml.substring(start, end)
    }
}
