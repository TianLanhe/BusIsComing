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
        assertTrue(frequentLayout.contains("@+id/transitCodeButton"))
        assertTrue(frequentLayout.contains("@+id/resultListContainer"))
        assertTrue(frequentLayout.contains("@+id/resultRefreshOverlay"))
        assertTrue(frequentLayout.contains("@+id/firstRunHeadlineText"))
        assertTrue(mainActivity.contains("RouteManageActivity::class.java"))
        assertTrue(mainActivity.contains("TransitCodePaymentLauncher.forActivity(this)"))
    }

    @Test
    fun firstRunOneTimeQueryMovesToSearchDestination() {
        assertTrue(frequentLayout.contains("@+id/emptySearchButton"))
        assertTrue(mainActivity.contains("R.id.navigation_search"))
        assertFalse(mainActivity.contains("SettingsActivity::class.java"))
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
    fun searchPlaceFieldsKeepAStableTouchAndTextHeight() {
        val originInput = searchLayout.substringAfter("@+id/searchOriginInput").substringBefore("/>")
        val destinationInput = searchLayout.substringAfter("@+id/searchDestinationInput").substringBefore("/>")

        assertTrue(originInput.contains("android:minHeight=\"56dp\""))
        assertTrue(destinationInput.contains("android:minHeight=\"56dp\""))
    }
}
