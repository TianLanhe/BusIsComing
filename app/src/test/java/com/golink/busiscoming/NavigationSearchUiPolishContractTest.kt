package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSearchUiPolishContractTest {
    private val activityLayout = File("src/main/res/layout/activity_main.xml").readText()
    private val frequentLayout = File("src/main/res/layout/fragment_frequent_routes.xml").readText()
    private val searchLayout = File("src/main/res/layout/fragment_search.xml").readText()
    private val placePairLayout = File("src/main/res/layout/view_place_pair_editor.xml").readText()
    private val resultControlsLayout =
        File("src/main/res/layout/view_route_result_controls.xml").readText()
    private val themes = File("src/main/res/values/themes.xml").readText()
    private val navigationMenu = File("src/main/res/menu/top_level_navigation.xml").readText()
    private val mainActivity =
        File("src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt").readText()
    private val searchFragment =
        File("src/main/java/com/golink/busiscoming/ui/main/SearchFragment.kt").readText()
    private val frequentFragment =
        File("src/main/java/com/golink/busiscoming/ui/main/FrequentRoutesFragment.kt").readText()
    private val resultListDrivenAppBar =
        File("src/main/java/com/golink/busiscoming/ui/common/ResultListDrivenAppBar.kt").readText()

    @Test
    fun `frequent and search include the same route query status card`() {
        val statusCard = File("src/main/res/layout/view_route_query_status_card.xml")
        assertTrue("Missing shared query status card", statusCard.isFile)
        val card = statusCard.readText()
        assertTrue(card.contains("@+id/resultStatusCard"))
        assertTrue(card.contains("@+id/resultStatusProgress"))
        assertTrue(card.contains("@+id/resultStatusTitle"))
        assertTrue(card.contains("@+id/resultStatusMessage"))
        assertTrue(frequentLayout.contains("@layout/view_route_query_status_card"))
        assertTrue(searchLayout.contains("@layout/view_route_query_status_card"))
        assertFalse(searchLayout.contains("@+id/searchResultLoading"))
        assertFalse(searchLayout.contains("@+id/searchResultStatus"))
        assertTrue(searchLayout.contains("@+id/searchResultRefreshOverlay"))
        assertTrue(searchLayout.contains("@+id/searchResultRefreshProgress"))
        assertTrue(searchLayout.contains("@+id/searchResultRefreshSuccess"))
        assertTrue(searchFragment.contains("renderSearchUi()"))
    }

    @Test
    fun `bottom navigation preserves indicator size with a dedicated label gap`() {
        val indicator = styleBlock("TopLevelNavigation.ActiveIndicator")
        assertTrue(indicator.contains("<item name=\"android:width\">64dp</item>"))
        assertTrue(indicator.contains("<item name=\"android:height\">32dp</item>"))
        assertTrue(activityLayout.contains("app:itemIconSize=\"24dp\""))
        assertTrue(activityLayout.contains("android:minHeight=\"64dp\""))
        assertTrue(activityLayout.contains("app:itemPaddingTop=\"6dp\""))
        assertTrue(activityLayout.contains("app:itemPaddingBottom=\"6dp\""))
        assertFalse(mainActivity.contains("applyTopLevelNavigationLabelSpacing"))
        assertTrue(activityLayout.contains("android:layout_height=\"wrap_content\""))
    }

    @Test
    fun `frequent and search use one transparent compact result controls view`() {
        val sharedFile = File("src/main/res/layout/view_route_result_controls.xml")
        assertTrue("Missing shared result controls layout", sharedFile.isFile)
        val shared = sharedFile.readText()
        assertTrue(shared.contains("android:background=\"@android:color/transparent\""))
        assertTrue(shared.contains("android:paddingTop=\"2dp\""))
        assertTrue(shared.contains("android:paddingBottom=\"2dp\""))
        assertTrue(shared.contains("android:layout_marginTop=\"4dp\""))
        assertTrue(shared.contains("android:minHeight=\"48dp\""))
        assertTrue(frequentLayout.contains("RouteResultControlsView"))
        assertTrue(searchLayout.contains("RouteResultControlsView"))
        assertFalse(frequentLayout.contains("android:background=\"@color/bus_surface\""))
    }

    @Test
    fun `bottom navigation has persistent selected hierarchy without changing its icon slot`() {
        assertTrue(activityLayout.contains("app:itemActiveIndicatorStyle=\"@style/TopLevelNavigation.ActiveIndicator\""))
        assertTrue(activityLayout.contains("app:itemIconSize=\"24dp\""))
        assertTrue(activityLayout.contains("app:itemTextAppearanceActive=\"@style/TopLevelNavigation.Text.Active\""))
        assertTrue(activityLayout.contains("app:itemTextAppearanceInactive=\"@style/TopLevelNavigation.Text.Inactive\""))

        val activeText = styleBlock("TopLevelNavigation.Text.Active")
        val inactiveText = styleBlock("TopLevelNavigation.Text.Inactive")
        assertTrue(activeText.contains("<item name=\"android:textSize\">13sp</item>"))
        assertTrue(activeText.contains("<item name=\"android:textStyle\">bold</item>"))
        assertTrue(inactiveText.contains("<item name=\"android:textSize\">12sp</item>"))
        assertTrue(inactiveText.contains("<item name=\"android:textStyle\">normal</item>"))

        listOf("frequent_routes", "search", "settings").forEach { name ->
            assertTrue(navigationMenu.contains("@drawable/ic_nav_${name}_state"))
            val selector = File("src/main/res/drawable/ic_nav_${name}_state.xml").readText()
            assertTrue(selector.contains("android:state_checked=\"true\""))
            assertFalse(selector.contains("android:inset"))
        }
    }

    @Test
    fun `search inputs render one border caption without supporting text rows`() {
        assertFalse(searchLayout.contains("android:text=\"@string/search_title\""))
        assertTrue(searchLayout.contains("android:id=\"@+id/searchPlacePairEditor\""))
        assertTrue(placePairLayout.contains("android:id=\"@+id/placePairInputColumn\""))
        assertTrue(placePairLayout.contains("android:id=\"@+id/placePairSwapSlot\""))
        assertTrue(placePairLayout.contains("android:id=\"@+id/placePairOriginToolSlot\""))
        assertTrue(placePairLayout.contains("android:id=\"@+id/placePairDestinationToolSlot\""))
        assertTrue(placePairLayout.contains("android:id=\"@+id/placePairCurrentLocationButton\""))
        assertFalse(placePairLayout.contains("placePairOriginAttribution"))
        assertFalse(placePairLayout.contains("placePairDestinationAttribution"))
        assertTrue(placePairLayout.contains("app:expandedHintEnabled=\"false\""))
        assertTrue(
            placePairLayout.contains(
                "app:hintTextAppearance=\"@style/TextAppearance.BusIsComing.SearchFieldCaption\""
            )
        )
        assertTrue(searchFragment.contains("SearchFieldCaptionRenderer"))

        val originIndex = placePairLayout.indexOf("@+id/placePairOriginLayout")
        val originCandidatesIndex = placePairLayout.indexOf("@+id/placePairOriginCandidateList")
        val destinationIndex = placePairLayout.indexOf("@+id/placePairDestinationLayout")
        val destinationCandidatesIndex = placePairLayout.indexOf("@+id/placePairDestinationCandidateList")
        assertTrue(originIndex < originCandidatesIndex)
        assertTrue(originCandidatesIndex < destinationIndex)
        assertTrue(destinationIndex < destinationCandidatesIndex)
        assertFalse(placePairLayout.contains("app:helperText=\"@string/place_search_helper\""))

        listOf("placePairOriginInput", "placePairDestinationInput").forEach { id ->
            val input = placePairLayout.substringAfter("@+id/$id").substringBefore("/>")
            assertTrue(input.contains("android:paddingStart=\"16dp\""))
            assertTrue(input.contains("android:paddingEnd=\"52dp\""))
            assertTrue(input.contains("android:textCursorDrawable=\"@drawable/search_text_cursor\""))
        }
        assertTrue(placePairLayout.contains("app:boxStrokeColor=\"@color/search_input_stroke\""))
        assertTrue(placePairLayout.contains("app:boxStrokeWidthFocused=\"2dp\""))
        val swapSlot = placePairLayout
            .substringAfter("android:id=\"@+id/placePairSwapSlot\"")
            .substringBefore(">")
        assertTrue(swapSlot.contains("android:layout_height=\"48dp\""))
        assertFalse(placePairLayout.contains("android:layout_height=\"120dp\""))
    }

    @Test
    fun `frequent and search results share one checkable sort style`() {
        val sortStyle = styleBlock("RouteResultSortButton")
        assertTrue(sortStyle.contains("<item name=\"android:minHeight\">48dp</item>"))
        assertTrue(sortStyle.contains("<item name=\"android:paddingLeft\">14dp</item>"))
        assertTrue(sortStyle.contains("<item name=\"android:paddingRight\">14dp</item>"))
        assertTrue(sortStyle.contains("<item name=\"android:textSize\">13sp</item>"))
        assertTrue(resultControlsLayout.contains("style=\"@style/RouteResultSortButton\""))
        assertTrue(frequentLayout.contains("RouteResultControlsView"))
        assertTrue(searchLayout.contains("RouteResultControlsView"))
        assertTrue(mainActivity.contains("button.isChecked = isSelected"))
        assertFalse(mainActivity.contains("button.backgroundTintList ="))
        assertTrue(searchFragment.contains("button.isChecked = active"))

        listOf("sort_button_background", "sort_button_text", "sort_button_stroke").forEach { name ->
            val selector = File("src/main/res/color/$name.xml").readText()
            assertTrue(selector.contains("android:state_checked=\"true\""))
        }
    }

    @Test
    fun `search uses a compact exclusive trip context with icon edit and outlined save`() {
        assertFalse(searchLayout.contains("android:id=\"@+id/searchResultSummaryContainer\""))
        assertFalse(searchLayout.contains("android:id=\"@+id/searchResultActions\""))
        assertFalse(searchFragment.contains("configureResultSummaryLayout"))
        assertTrue(searchLayout.contains("android:id=\"@+id/searchInputContainer\""))
        assertTrue(searchLayout.contains("android:id=\"@+id/searchTripContext\""))
        assertTrue(searchLayout.contains("android:id=\"@+id/searchTripRouteText\""))
        assertTrue(searchLayout.contains("android:id=\"@+id/searchEditButton\""))
        assertFalse(searchLayout.contains("android:id=\"@+id/searchCancelEditButton\""))
        assertTrue(searchLayout.contains("app:icon=\"@drawable/ic_edit\""))
        assertTrue(searchLayout.contains("android:ellipsize=\"end\""))
        assertTrue(searchLayout.contains("android:maxLines=\"1\""))
        val saveButton = searchLayout.substringAfter("@+id/searchSaveButton").substringBefore("/>")
        assertTrue(saveButton.contains("style=\"@style/StableShortText.Button.Outlined\""))
        assertTrue(saveButton.contains("android:layout_height=\"wrap_content\""))
        assertTrue(saveButton.contains("android:minHeight=\"48dp\""))
        assertTrue(saveButton.contains("android:text=\"@string/search_trip_save\""))
        assertTrue(saveButton.contains("android:visibility=\"gone\""))

        val inputContainerStart = searchLayout.indexOf("@+id/searchInputContainer")
        val saveIndex = searchLayout.indexOf("@+id/searchSaveButton")
        val inputContainerEnd = searchLayout.indexOf("</LinearLayout>", inputContainerStart)
        val subtitleIndex = searchLayout.indexOf("@string/search_subtitle")
        assertTrue(subtitleIndex > inputContainerStart)
        assertTrue(subtitleIndex < inputContainerEnd)
        assertTrue(saveIndex > inputContainerEnd)
        val sortIndex = searchLayout.indexOf("@+id/searchRouteResultControls")
        val listIndex = searchLayout.indexOf("@+id/searchResultList")
        assertTrue(sortIndex < listIndex)
        assertTrue(resultControlsLayout.indexOf("@+id/sortControls") <
            resultControlsLayout.indexOf("@+id/resultSummaryContainer"))
        assertTrue(searchFragment.contains("SearchTripContextVisibility"))
        assertTrue(searchFragment.contains("SearchTripEditorTransitionController"))
        assertTrue(searchFragment.contains("render(showEditor = showEditor, animate = true)"))
    }

    @Test
    fun `search retains old results while editing and clears them only outside that state`() {
        assertTrue(searchLayout.contains("<androidx.coordinatorlayout.widget.CoordinatorLayout"))
        assertTrue(searchLayout.contains("<com.google.android.material.appbar.AppBarLayout"))
        assertTrue(searchLayout.contains("app:layout_scrollFlags=\"scroll\""))
        assertTrue(searchLayout.contains("app:layout_behavior=\"@string/appbar_scrolling_view_behavior\""))
        assertTrue(resultListDrivenAppBar.contains("canDrag(appBarLayout: AppBarLayout): Boolean = false"))
        assertTrue(searchFragment.contains("ResultListDrivenAppBar.install"))
        assertTrue(frequentFragment.contains("ResultListDrivenAppBar.install"))
        assertTrue(frequentFragment.contains("isNestedScrollingEnabled = false"))
        val changedBlock = searchFragment
            .substringAfter("private fun onSearchSelectionChanged()")
            .substringBefore("private fun clearSuccessfulQuery()")
        assertTrue(changedBlock.contains("SearchDisplayMode.EDITING_RESULTS"))
        assertTrue(changedBlock.contains("return"))
        assertTrue(changedBlock.contains("routeQueryCoordinator.invalidate()"))
        assertTrue(changedBlock.contains("routeQueryState.clear()"))
        assertTrue(changedBlock.contains("cancelRefreshFeedback()"))
        assertTrue(changedBlock.contains("routeResultControls.visibility = View.GONE"))
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
