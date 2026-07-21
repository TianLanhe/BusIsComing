package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceInputInlineCandidatesContractTest {
    private val controllerKt =
        File("src/main/java/com/golink/busiscoming/ui/common/PlaceInputController.kt").readText()
    private val editActivityKt =
        File("src/main/java/com/golink/busiscoming/ui/edit/RouteEditActivity.kt").readText()
    private val editLayoutXml = File("src/main/res/layout/activity_route_edit.xml").readText()
    private val searchFragmentKt =
        File("src/main/java/com/golink/busiscoming/ui/main/SearchFragment.kt").readText()
    private val searchLayoutXml = File("src/main/res/layout/fragment_search.xml").readText()

    @Test
    fun `edit and search use one place pair editor geometry`() {
        val sharedFile = File("src/main/res/layout/view_place_pair_editor.xml")
        assertTrue("Missing shared place pair editor layout", sharedFile.isFile)
        val shared = sharedFile.readText()
        assertTrue(shared.contains("android:minHeight=\"56dp\""))
        assertTrue(shared.contains("android:layout_width=\"48dp\""))
        assertTrue(shared.contains("android:layout_height=\"48dp\""))
        assertTrue(shared.contains("android:layout_marginTop=\"8dp\""))
        assertTrue(shared.contains("android:id=\"@+id/placePairSwapButton\""))
        assertTrue(editLayoutXml.contains("PlacePairEditorView"))
        assertTrue(searchLayoutXml.contains("PlacePairEditorView"))
        assertFalse(editActivityKt.contains("syncSwapButtonVisibility"))
    }

    @Test
    fun routeEditProvidesInlineCandidateLists() {
        val shared = File("src/main/res/layout/view_place_pair_editor.xml").readText()
        assertTrue(shared.contains("android:id=\"@+id/placePairOriginCandidateList\""))
        assertTrue(shared.contains("android:id=\"@+id/placePairDestinationCandidateList\""))
        assertTrue(editLayoutXml.contains("android:id=\"@+id/routePlacePairEditor\""))
        assertTrue(editLayoutXml.contains("android:id=\"@+id/routeEditScroll\""))
        assertTrue(editLayoutXml.contains("<androidx.core.widget.NestedScrollView"))
    }

    @Test
    fun sharedControllerUsesRecyclerViewInsteadOfPopupWindow() {
        assertTrue(controllerKt.contains("private val candidateList: RecyclerView"))
        assertTrue(controllerKt.contains("fun hideCandidates(): Boolean"))
        assertTrue(controllerKt.contains("WindowInsetsCompat.Type.ime()"))
        assertTrue(controllerKt.contains("place_candidate_list_background"))
        assertTrue(controllerKt.contains("PlaceDistanceFormatter"))
        assertFalse(controllerKt.contains("showDropDown()"))
        assertFalse(controllerKt.contains("dismissDropDown()"))
    }

    @Test
    fun editAndSearchFlowsCoordinateInlineCandidates() {
        assertTrue(editActivityKt.contains("focusUnselectedPeer"))
        assertTrue(editActivityKt.contains("hideCandidateLists"))
        assertTrue(searchFragmentKt.contains("PlaceInputController"))
        assertTrue(searchFragmentKt.contains("destinationController?.hideCandidates()"))
    }

    @Test
    fun searchKeepsSelectedPlacesAcrossViewRecreation() {
        assertTrue(searchFragmentKt.contains("restoredOrigin"))
        assertTrue(searchFragmentKt.contains("restoredDestination"))
        assertTrue(searchFragmentKt.contains("onSaveInstanceState"))
        assertTrue(searchFragmentKt.contains("currentPlaceRequestState.beginAutoRequest"))
    }
}
