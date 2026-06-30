package com.example.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceInputInlineCandidatesContractTest {
    private val controllerKt =
        File("src/main/java/com/example/busiscoming/ui/common/PlaceInputController.kt").readText()
    private val editActivityKt =
        File("src/main/java/com/example/busiscoming/ui/edit/RouteEditActivity.kt").readText()
    private val editLayoutXml = File("src/main/res/layout/activity_route_edit.xml").readText()
    private val temporarySheetKt =
        File("src/main/java/com/example/busiscoming/ui/main/TemporaryRouteBottomSheet.kt").readText()

    @Test
    fun routeEditProvidesInlineCandidateLists() {
        assertTrue(editLayoutXml.contains("android:id=\"@+id/originCandidateList\""))
        assertTrue(editLayoutXml.contains("android:id=\"@+id/destinationCandidateList\""))
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
    fun editAndTemporaryFlowsCoordinateFocusBackAndSheetExpansion() {
        assertTrue(editActivityKt.contains("focusUnselectedPeer"))
        assertTrue(editActivityKt.contains("hideCandidateLists"))
        assertTrue(temporarySheetKt.contains("setCandidateMode"))
        assertTrue(temporarySheetKt.contains("BottomSheetBehavior.STATE_EXPANDED"))
    }

    @Test
    fun temporarySheetSupportsPrefilledEditWithoutOverwritingCurrentOrigin() {
        assertTrue(temporarySheetKt.contains("fun show(initialOrigin: Place? = null, initialDestination: Place? = null)"))
        assertTrue(temporarySheetKt.contains("applyInitialPlaces(initialOrigin, initialDestination)"))
        assertTrue(temporarySheetKt.contains("originController?.setSelectedPlace(initialOrigin)"))
        assertTrue(temporarySheetKt.contains("destinationController?.setSelectedPlace(initialDestination)"))
        assertTrue(temporarySheetKt.contains("if (initialOrigin == null)"))
        assertTrue(temporarySheetKt.contains("requestCurrentOriginIfNeeded(isAuto = true)"))
    }
}
