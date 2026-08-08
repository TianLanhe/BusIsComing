package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAutoRefreshSettingsLayoutContractTest {
    private val layout = File("src/main/res/layout/fragment_settings.xml").readText()
    private val source = File(
        "src/main/java/com/golink/busiscoming/ui/main/SettingsFragment.kt"
    ).readText()

    @Test
    fun autoRefreshIsAStandardSettingsRowWithTrailingCurrentValue() {
        val row = layout.substringAfter("android:id=\"@+id/settingsAutoRefreshRow\"")
            .substringBefore("<TextView\n            style=\"@style/SettingsGroupLabel\"")
        assertTrue(row.contains("android:minHeight=\"48dp\""))
        assertTrue(row.contains("android:clickable=\"true\""))
        assertTrue(row.contains("android:focusable=\"true\""))
        assertTrue(row.contains("android:id=\"@+id/settingsAutoRefreshValue\""))
        assertTrue(row.contains("style=\"@style/SettingsRowValueText\""))
        assertFalse(layout.contains("settingsAutoRefreshOptions"))
    }

    @Test
    fun rowOpensMaterialSingleChoiceDialogAndSelectionImmediatelyPersists() {
        assertTrue(source.contains("MaterialAlertDialogBuilder(requireContext())"))
        assertTrue(source.contains("setSingleChoiceItems"))
        assertTrue(source.contains("autoRefreshStore.setInterval(selectedInterval)"))
        assertTrue(source.contains("AutoRefreshNoticeStore(requireContext()).complete()"))
        assertTrue(source.contains("dialog.dismiss()"))
        assertFalse(source.contains("renderAutoRefreshSelector"))
        assertFalse(source.contains("findSelectedAutoRefreshButton"))
    }
}
