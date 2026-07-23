package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitCodeShortcutContractTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val mainActivity =
        File("src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt").readText()
    private val relayActivity =
        File("src/main/java/com/golink/busiscoming/ui/main/TransitCodeShortcutActivity.kt")
    private val shortcutManager =
        File("src/main/java/com/golink/busiscoming/ui/main/TransitCodeShortcutManager.kt")
    private val entryPoint =
        File("src/main/java/com/golink/busiscoming/ui/main/TransitCodeEntryPoint.kt")
    private val monitorService =
        File("src/main/java/com/golink/busiscoming/service/BusMonitorService.kt")
    private val staticShortcuts = File("src/main/res/xml/shortcuts.xml")

    @Test
    fun `manifest publishes a static transit code shortcut`() {
        assertTrue(manifest.contains("android.app.shortcuts"))
        assertTrue(manifest.contains("@xml/shortcuts"))
        assertTrue(staticShortcuts.isFile)
        val xml = staticShortcuts.readText()
        assertTrue(xml.contains("android:shortcutId=\"transit_code\""))
        assertTrue(xml.contains("@string/transit_code"))
        assertTrue(xml.contains("com.golink.busiscoming.action.OPEN_TRANSIT_CODE"))
        assertTrue(xml.contains("com.golink.busiscoming.ui.main.TransitCodeShortcutActivity"))
    }

    @Test
    fun `shortcut relay stays separate from the in-app transit code entry point`() {
        assertTrue(shortcutManager.isFile)
        assertTrue(relayActivity.isFile)
        val source = shortcutManager.readText()
        val entryPointSource = entryPoint.readText()
        val monitorSource = monitorService.readText()
        val relaySource = relayActivity.readText()
        assertTrue(source.contains("ShortcutManagerCompat.requestPinShortcut"))
        assertTrue(source.contains("TransitCodeEntryPoint.createShortcutIntent(context)"))
        assertTrue(source.contains("ShortcutManagerCompat.updateShortcuts"))
        assertTrue(entryPointSource.contains("Intent(context, MainActivity::class.java)"))
        assertTrue(
            entryPointSource.contains(
                "Intent(context, TransitCodeShortcutActivity::class.java)"
            )
        )
        assertTrue(monitorSource.contains("TransitCodeEntryPoint.createIntent(this)"))
        assertTrue(relaySource.contains("TransitCodePaymentLauncher.forActivity(this)"))
        assertTrue(relaySource.contains("finish()"))
        assertTrue(manifest.contains(".ui.main.TransitCodeShortcutActivity"))
        assertTrue(manifest.contains("@style/Theme.BusIsComing.ShortcutRelay"))
        assertTrue(manifest.contains("android:excludeFromRecents=\"true\""))
        assertTrue(manifest.contains("android:noHistory=\"true\""))
        assertTrue(mainActivity.contains("TransitCodeEntryPoint.isLaunchAction"))
        assertTrue(mainActivity.contains("consumeTransitCodeIntent(intent)"))
        assertFalse(mainActivity.contains("transitCodeButton.setOnClickListener"))
    }

    @Test
    fun `pinned shortcut request distinguishes actual and pending states`() {
        val source = shortcutManager.readText()
        assertTrue(source.contains("enum class TransitCodeShortcutState"))
        assertTrue(source.contains("ALREADY_PINNED"))
        assertTrue(source.contains("ShortcutManagerCompat.getShortcuts"))
        assertTrue(source.contains("ShortcutManagerCompat.FLAG_MATCH_PINNED"))
        assertTrue(source.contains("TransitCodeShortcutPinnedReceiver"))
        assertFalse(source.contains("requestPinShortcut(context, shortcut, null)"))
    }
}
