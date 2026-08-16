package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDatabaseSettingsContractTest {
    private val layout = File("src/main/res/layout/fragment_settings.xml").readText()
    private val source = File(
        "src/main/java/com/golink/busiscoming/ui/main/SettingsFragment.kt"
    ).readText()

    @Test
    fun routeDatabaseCheckIsAStandardAccessibleRouteDataRow() {
        val transfer = layout.indexOf("@+id/settingsRouteTransferRow")
        val database = layout.indexOf("@+id/settingsRouteDatabaseRow")
        val nextGroup = layout.indexOf("@string/settings_group_support")

        assertTrue(transfer < database)
        assertTrue(database < nextGroup)
        assertTrue(layout.contains("@+id/settingsRouteDatabaseSummary"))
        assertTrue(layout.contains("@string/settings_route_database_update"))
        assertTrue(source.contains("CrossOperatorEtaRuntime.updateCoordinator()"))
        assertTrue(source.contains("RouteDatabaseUpdateTrigger.MANUAL"))
        assertTrue(source.contains("routeDatabaseSubscription?.close()"))
    }

    @Test
    fun manualGlobalCheckDoesNotInvokeLazySliceOrDpFromSettings() {
        assertFalse(source.contains("CtbRouteSliceLoader"))
        assertFalse(source.contains("CrossOperatorRouteMatcher"))
        assertFalse(source.contains("loadRoute("))
    }
}
