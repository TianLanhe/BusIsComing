package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailActivityContractTest {
    @Test
    fun fullScreenDetailUsesFixedToolbarAndOneRecyclerViewWithoutMapPlaceholder() {
        val layout = File("src/main/res/layout/activity_route_detail.xml").readText()
        val activity = File("src/main/java/com/golink/busiscoming/ui/main/RouteDetailActivity.kt").readText()

        assertTrue(layout.contains("@+id/routeDetailToolbar"))
        assertTrue(layout.contains("app:title=\"@string/route_detail_title\""))
        assertEquals(1, Regex("<androidx.recyclerview.widget.RecyclerView").findAll(layout).count())
        assertFalse(layout.contains("MapView"))
        assertFalse(layout.contains("mapPlaceholder"))
        assertTrue(activity.contains("RouteDetailUiFormatter.items"))
        assertTrue(activity.contains("AppLanguageRuntime.snapshot()"))
    }

    @Test
    fun bothResultEntryPointsLaunchActivityAndBottomSheetIsRemoved() {
        val main = File("src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt").readText()
        val search = File("src/main/java/com/golink/busiscoming/ui/main/SearchFragment.kt").readText()

        assertTrue(main.contains("RouteDetailNavigator.open(this, route)"))
        assertTrue(search.contains("RouteDetailNavigator.open(requireContext(), route)"))
        assertFalse(File("src/main/java/com/golink/busiscoming/ui/main/RouteDetailBottomSheet.kt").exists())
        assertTrue(File("src/main/AndroidManifest.xml").readText().contains(".ui.main.RouteDetailActivity"))
    }
}
