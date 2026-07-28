package com.golink.busiscoming

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
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
    fun `route edit restores historical geometry while search keeps compact editor`() {
        val sharedFile = File("src/main/res/layout/view_place_pair_editor.xml")
        assertTrue("Missing search place pair editor layout", sharedFile.isFile)
        val shared = sharedFile.readText()
        assertTrue(shared.contains("android:minHeight=\"56dp\""))
        assertTrue(shared.contains("android:layout_width=\"48dp\""))
        assertTrue(shared.contains("android:layout_height=\"48dp\""))
        assertTrue(shared.contains("android:layout_marginTop=\"8dp\""))
        assertTrue(shared.contains("android:id=\"@+id/placePairSwapButton\""))
        assertTrue(searchLayoutXml.contains("PlacePairEditorView"))
        assertFalse(editLayoutXml.contains("PlacePairEditorView"))
        assertTrue(editLayoutXml.contains("android:id=\"@+id/originInputLayout\""))
        assertTrue(editLayoutXml.contains("android:id=\"@+id/destinationInputLayout\""))
        assertTrue(editLayoutXml.contains("android:minHeight=\"56dp\""))
        assertTrue(editLayoutXml.contains("android:paddingStart=\"16dp\""))
        assertTrue(editLayoutXml.contains("android:layout_marginTop=\"14dp\""))
        assertTrue(editLayoutXml.contains("android:layout_marginTop=\"6dp\""))
        assertTrue(editLayoutXml.contains("?attr/selectableItemBackgroundBorderless"))
        assertTrue(editActivityKt.contains("syncSwapButtonVisibility"))
        assertFalse(editActivityKt.contains("PlacePairEditorView"))
    }

    @Test
    fun routeEditProvidesInlineCandidateLists() {
        assertTrue(editLayoutXml.contains("android:id=\"@+id/originCandidateList\""))
        assertTrue(editLayoutXml.contains("android:id=\"@+id/destinationCandidateList\""))
        assertTrue(editLayoutXml.contains("android:id=\"@+id/originSearchLoading\""))
        assertTrue(editLayoutXml.contains("android:id=\"@+id/destinationSearchLoading\""))
        assertTrue(editLayoutXml.contains("android:id=\"@+id/originAttributionText\""))
        assertTrue(editLayoutXml.contains("android:id=\"@+id/routeEditScroll\""))
        assertTrue(editLayoutXml.contains("<androidx.core.widget.NestedScrollView"))
    }

    @Test
    fun `route edit keeps place supporting rows state driven while preserving journey name guidance`() {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/layout/activity_route_edit.xml"))
        val helperTextById = document.getElementsByTagName("com.google.android.material.textfield.TextInputLayout")
            .let { layouts ->
                (0 until layouts.length).associate { index ->
                    val layout = layouts.item(index).attributes
                    layout.getNamedItem("android:id").nodeValue to
                        (layout.getNamedItem("app:helperText")?.nodeValue ?: "")
                }
            }

        assertTrue(
            helperTextById["@+id/routeNameInputLayout"] == "@string/route_name_helper"
        )
        assertTrue(helperTextById["@+id/originInputLayout"].isNullOrEmpty())
        assertTrue(helperTextById["@+id/destinationInputLayout"].isNullOrEmpty())
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
    fun sharedControllerRestoresTheFieldDefaultHelperAfterClearingMessages() {
        assertTrue(
            controllerKt.contains(
                "private val defaultInstructionText = instructionText ?: inputLayout.helperText"
            )
        )
        assertTrue(controllerKt.contains("inputLayout.helperText = defaultInstructionText"))
        assertTrue(
            controllerKt.contains(
                "private val onMessageChanged: ((PlaceInputMessage) -> Unit)? = null"
            )
        )
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
