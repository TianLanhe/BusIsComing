package com.example.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainRouteSelectionLayoutTest {
    private val layoutXml = File("src/main/res/layout/activity_main.xml").readText()
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
        assertTrue(layoutXml.contains("android:id=\"@+id/temporaryQuerySaveButton\""))
    }

    @Test
    fun topBarShowsTransitCodeAndManageRoutesWithoutBusQueryTitle() {
        val transitCodeButton = xmlBlockFor("@+id/transitCodeButton")
        val manageRoutesButton = xmlBlockFor("@+id/manageRoutesButton")

        assertFalse(layoutXml.contains("android:text=\"巴士查詢\""))
        assertTrue(transitCodeButton.contains("android:text=\"乘車碼\""))
        assertTrue(manageRoutesButton.contains("android:text=\"管理路線\""))
        assertTrue(transitCodeButton.contains("app:backgroundTint=\"@color/bus_chip_selected\""))
        assertTrue(manageRoutesButton.contains("app:backgroundTint=\"@color/bus_chip_selected\""))
        assertTrue(transitCodeButton.contains("app:cornerRadius=\"6dp\""))
        assertTrue(manageRoutesButton.contains("app:cornerRadius=\"6dp\""))
        assertFalse(transitCodeButton.contains("Widget.MaterialComponents.Button.TextButton"))
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
}
