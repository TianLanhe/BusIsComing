package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSearchUiPolishContractTest {
    private val activityLayout = File("src/main/res/layout/activity_main.xml").readText()
    private val frequentLayout = File("src/main/res/layout/fragment_frequent_routes.xml").readText()
    private val searchLayout = File("src/main/res/layout/fragment_search.xml").readText()
    private val themes = File("src/main/res/values/themes.xml").readText()
    private val navigationMenu = File("src/main/res/menu/top_level_navigation.xml").readText()
    private val mainActivity =
        File("src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt").readText()
    private val searchFragment =
        File("src/main/java/com/golink/busiscoming/ui/main/SearchFragment.kt").readText()

    @Test
    fun `bottom navigation has persistent selected hierarchy without changing its icon slot`() {
        assertTrue(activityLayout.contains("app:itemActiveIndicatorStyle=\"@style/TopLevelNavigation.ActiveIndicator\""))
        assertTrue(activityLayout.contains("app:itemIconSize=\"28dp\""))
        assertTrue(activityLayout.contains("app:itemTextAppearanceActive=\"@style/TopLevelNavigation.Text.Active\""))
        assertTrue(activityLayout.contains("app:itemTextAppearanceInactive=\"@style/TopLevelNavigation.Text.Inactive\""))

        val activeText = styleBlock("TopLevelNavigation.Text.Active")
        val inactiveText = styleBlock("TopLevelNavigation.Text.Inactive")
        assertTrue(activeText.contains("<item name=\"android:textSize\">14sp</item>"))
        assertTrue(activeText.contains("<item name=\"android:textStyle\">bold</item>"))
        assertTrue(inactiveText.contains("<item name=\"android:textSize\">12sp</item>"))
        assertTrue(inactiveText.contains("<item name=\"android:textStyle\">normal</item>"))

        listOf("frequent_routes", "search", "settings").forEach { name ->
            assertTrue(navigationMenu.contains("@drawable/ic_nav_${name}_state"))
            val selector = File("src/main/res/drawable/ic_nav_${name}_state.xml").readText()
            assertTrue(selector.contains("android:state_checked=\"true\""))
            assertTrue(selector.contains("android:inset=\"2dp\""))
        }
    }

    @Test
    fun `search inputs own their tools candidates and attribution beside a fixed swap action`() {
        assertTrue(searchLayout.contains("android:id=\"@+id/searchRouteInputContainer\""))
        assertTrue(searchLayout.contains("android:id=\"@+id/searchPlaceColumn\""))
        assertTrue(searchLayout.contains("android:id=\"@+id/searchSwapSlot\""))
        assertTrue(searchLayout.contains("android:id=\"@+id/searchOriginToolSlot\""))
        assertTrue(searchLayout.contains("android:id=\"@+id/searchDestinationToolSlot\""))
        assertTrue(searchLayout.contains("android:id=\"@+id/searchCurrentLocationButton\""))
        assertTrue(searchLayout.contains("android:id=\"@+id/searchDestinationAttribution\""))

        val originIndex = searchLayout.indexOf("@+id/searchOriginLayout")
        val originCandidatesIndex = searchLayout.indexOf("@+id/searchOriginCandidateList")
        val destinationIndex = searchLayout.indexOf("@+id/searchDestinationLayout")
        val destinationCandidatesIndex = searchLayout.indexOf("@+id/searchDestinationCandidateList")
        assertTrue(originIndex < originCandidatesIndex)
        assertTrue(originCandidatesIndex < destinationIndex)
        assertTrue(destinationIndex < destinationCandidatesIndex)
        assertFalse(searchLayout.contains("app:helperText=\"@string/place_search_helper\""))
    }

    @Test
    fun `frequent and search results share one checkable sort style`() {
        val sortStyle = styleBlock("RouteResultSortButton")
        assertTrue(sortStyle.contains("<item name=\"android:minHeight\">48dp</item>"))
        assertTrue(sortStyle.contains("<item name=\"android:paddingLeft\">14dp</item>"))
        assertTrue(sortStyle.contains("<item name=\"android:paddingRight\">14dp</item>"))
        assertTrue(sortStyle.contains("<item name=\"android:textSize\">13sp</item>"))
        assertTrue(frequentLayout.contains("style=\"@style/RouteResultSortButton\""))
        assertTrue(searchLayout.contains("style=\"@style/RouteResultSortButton\""))
        assertTrue(mainActivity.contains("button.isChecked = isSelected"))
        assertFalse(mainActivity.contains("button.backgroundTintList ="))
        assertTrue(searchFragment.contains("button.isChecked = active"))

        listOf("sort_button_background", "sort_button_text", "sort_button_stroke").forEach { name ->
            val selector = File("src/main/res/color/$name.xml").readText()
            assertTrue(selector.contains("android:state_checked=\"true\""))
        }
    }

    @Test
    fun `search result summary responds to width and font scale with a tonal save action`() {
        assertTrue(searchLayout.contains("android:id=\"@+id/searchResultActions\""))
        assertTrue(searchFragment.contains("screenWidthDp >= 600"))
        assertTrue(searchFragment.contains("fontScale < 1.3f"))
        val saveButton = searchLayout.substringAfter("@+id/searchSaveButton").substringBefore("/>")
        assertTrue(saveButton.contains("style=\"@style/StableShortText.Button.Tonal\""))
        assertTrue(saveButton.contains("android:layout_height=\"wrap_content\""))
        assertTrue(saveButton.contains("android:minHeight=\"48dp\""))
    }

    @Test
    fun `first run keeps only the regular journey action and labels the route preview precisely`() {
        assertFalse(frequentLayout.contains("@+id/emptySearchButton"))
        assertFalse(mainActivity.contains("emptySearchButton"))
        assertTrue(File("src/main/res/values/strings.xml").readText().contains(
            "<string name=\"first_run_sample_label\">路線結果預覽</string>"
        ))
        assertTrue(File("src/main/res/values-b+zh+Hans/strings.xml").readText().contains(
            "<string name=\"first_run_sample_label\">路线结果预览</string>"
        ))
        assertTrue(File("src/main/res/values-en/strings.xml").readText().contains(
            "<string name=\"first_run_sample_label\">Route results preview</string>"
        ))
        assertTrue(File("src/main/res/values/strings_runtime.xml").readText().contains(
            "name=\"search_routes\""
        ))
    }

    private fun styleBlock(styleName: String): String {
        val start = themes.indexOf("<style name=\"$styleName\"")
        assertTrue("Missing style $styleName", start >= 0)
        val end = themes.indexOf("</style>", start)
        assertTrue("Missing style end for $styleName", end >= 0)
        return themes.substring(start, end)
    }
}
