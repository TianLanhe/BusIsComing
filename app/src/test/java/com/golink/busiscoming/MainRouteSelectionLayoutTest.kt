package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainRouteSelectionLayoutTest {
    private val frequentLayout = File("src/main/res/layout/fragment_frequent_routes.xml").readText()
    private val searchLayout = File("src/main/res/layout/fragment_search.xml").readText()
    private val mainActivity = File("src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt").readText()
    private val searchFragment = File("src/main/java/com/golink/busiscoming/ui/main/SearchFragment.kt").readText()
    private val routeQueryCoordinator =
        File("src/main/java/com/golink/busiscoming/ui/main/RouteQueryCoordinator.kt").readText()

    @Test
    fun frequentRoutesKeepsSavedRouteQueryAndResultsSurface() {
        assertTrue(frequentLayout.contains("@+id/routePickerButton"))
        assertTrue(frequentLayout.contains("@+id/routeManageButton"))
        assertFalse(frequentLayout.contains("@+id/transitCodeButton"))
        assertFalse(frequentLayout.contains("@+id/firstRunTransitCodeButton"))
        assertFalse(frequentLayout.contains("@+id/settingsButton"))
        assertTrue(frequentLayout.contains("@+id/resultListContainer"))
        assertTrue(frequentLayout.contains("@+id/resultRefreshOverlay"))
        assertTrue(frequentLayout.contains("@+id/firstRunHeadlineText"))
        assertTrue(mainActivity.contains("RouteManageActivity::class.java"))
        assertFalse(mainActivity.contains("transitCodeButton.setOnClickListener"))
    }

    @Test
    fun firstRunKeepsOneJourneyActionAndUsesTheSearchDestinationForOneTimeQueries() {
        assertFalse(frequentLayout.contains("@+id/emptySearchButton"))
        assertFalse(mainActivity.contains("emptySearchButton"))
        assertTrue(searchLayout.contains("@+id/searchQueryButton"))
        assertTrue(mainActivity.contains("R.id.navigation_search"))
        assertFalse(mainActivity.contains("SettingsActivity::class.java"))
    }

    @Test
    fun firstRunContentScrollsAtLargeFontWithoutCappingTheHeadline() {
        val emptyState = frequentLayout
            .substringAfter("android:id=\"@+id/emptyRouteState\"")
            .substringBefore("android:id=\"@+id/queryControls\"")
        val headline = emptyState
            .substringAfter("android:id=\"@+id/firstRunHeadlineText\"")
            .substringBefore("/>")

        assertTrue(emptyState.contains("<androidx.core.widget.NestedScrollView"))
        assertTrue(emptyState.contains("android:fillViewport=\"true\""))
        assertFalse(headline.contains("android:maxLines"))
    }

    @Test
    fun frequentHeaderIsOneRowAndOnlyResultControlsStayPinned() {
        assertTrue(frequentLayout.contains("<androidx.coordinatorlayout.widget.CoordinatorLayout"))
        assertTrue(frequentLayout.contains("<com.google.android.material.appbar.AppBarLayout"))
        assertTrue(frequentLayout.contains("android:id=\"@+id/collapsingQueryControls\""))
        assertTrue(frequentLayout.contains("app:layout_scrollFlags=\"scroll\""))
        assertTrue(frequentLayout.contains("android:id=\"@+id/stickyResultControls\""))
        assertTrue(frequentLayout.contains("app:layout_behavior=\"@string/appbar_scrolling_view_behavior\""))
        assertTrue(frequentLayout.contains("android:paddingStart=\"16dp\""))
        assertTrue(frequentLayout.contains("android:paddingEnd=\"16dp\""))

        val header = frequentLayout
            .substringAfter("android:id=\"@+id/frequentRoutesHeader\"")
            .substringBefore("</LinearLayout>")
        assertTrue(header.contains("android:orientation=\"horizontal\""))
        val title = header.substringAfter("@+id/frequentRoutesTitle").substringBefore("/>")
        assertTrue(title.contains("android:layout_width=\"0dp\""))
        assertTrue(title.contains("android:layout_weight=\"1\""))
    }

    @Test
    fun hiddenResultControlsDoNotReserveAStickySpacer() {
        val sticky = frequentLayout
            .substringAfter("android:id=\"@+id/stickyResultControls\"")
            .substringBefore("</LinearLayout>")

        assertTrue(sticky.contains("android:visibility=\"gone\""))
        assertTrue(mainActivity.contains("updateStickyResultControlsVisibility()"))
    }

    @Test
    fun searchProvidesInlinePlaceSelectionAndSavedRouteAction() {
        assertTrue(searchLayout.contains("@+id/searchOriginInput"))
        assertTrue(searchLayout.contains("@+id/searchDestinationInput"))
        assertTrue(searchLayout.contains("@+id/searchOriginCandidateList"))
        assertTrue(searchLayout.contains("@+id/searchDestinationCandidateList"))
        assertTrue(searchLayout.contains("@+id/searchSwapButton"))
        assertTrue(searchLayout.contains("@+id/searchSaveButton"))
        assertTrue(searchFragment.contains("PlaceInputController"))
        assertTrue(searchFragment.contains("TemporaryRouteSaveDialog.show"))
        assertTrue(searchFragment.contains("RouteQueryCoordinator"))
        assertTrue(routeQueryCoordinator.contains("searchRoutesProgressively"))
    }

    @Test
    fun leavingSearchInvalidatesLocationWithoutDiscardingSuccessfulSaveEligibility() {
        val onDestinationHidden = searchFragment
            .substringAfter("fun onDestinationHidden()")
            .substringBefore("override fun onDestroyView()")

        assertTrue(onDestinationHidden.contains("invalidateCurrentPlaceRequest()"))
        assertFalse(onDestinationHidden.contains("clearSuccessfulQuery()"))
    }

    @Test
    fun searchPlaceFieldsKeepAStableTouchAndTextHeight() {
        val originInput = searchLayout.substringAfter("@+id/searchOriginInput").substringBefore("/>")
        val destinationInput = searchLayout.substringAfter("@+id/searchDestinationInput").substringBefore("/>")

        assertTrue(originInput.contains("android:minHeight=\"56dp\""))
        assertTrue(destinationInput.contains("android:minHeight=\"56dp\""))
    }
}
