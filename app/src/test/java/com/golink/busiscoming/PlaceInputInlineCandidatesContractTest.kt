package com.golink.busiscoming

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
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
    private val searchInstrumentationTest = File(
        "src/androidTest/java/com/golink/busiscoming/SearchDestinationInstrumentedTest.kt"
    ).readText()
    private val controllerInstrumentationTest = File(
        "src/androidTest/java/com/golink/busiscoming/PlaceInputControllerInstrumentedTest.kt"
    ).readText()

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
    fun `search uses six candidate rows and locks outer scrolling while candidates are visible`() {
        assertTrue(
            searchFragmentKt.contains("private const val SEARCH_MAX_VISIBLE_CANDIDATE_ROWS = 6")
        )
        assertTrue(searchFragmentKt.contains("SearchCandidateScrollLock"))
        assertTrue(searchFragmentKt.contains("setCandidateScrollLock"))
        assertTrue(controllerKt.contains("exclusiveVerticalScroll"))
        assertTrue(controllerKt.contains("addOnItemTouchListener"))
        assertTrue(controllerKt.contains("RecyclerView.SimpleOnItemTouchListener"))
        assertTrue(controllerKt.contains("MotionEvent.ACTION_DOWN"))
        assertFalse(searchFragmentKt.contains("scrollFlags ="))
    }

    @Test
    fun `candidate instrumentation preserves a refreshable result viewport and uses real swipes`() {
        assertTrue(searchInstrumentationTest.contains("refresh.isEnabled"))
        assertTrue(searchInstrumentationTest.contains("findFirstVisibleItemPosition"))
        assertTrue(searchInstrumentationTest.contains("scrollToPositionWithOffset"))
        assertTrue(searchInstrumentationTest.contains("firstResultPosition > 0"))
        assertTrue(searchInstrumentationTest.contains("showOriginCandidatesForExistingResult"))
        assertTrue(searchInstrumentationTest.contains("assertOuterViewportUnchanged"))
        assertTrue(searchInstrumentationTest.contains("onBackPressedDispatcher.onBackPressed()"))

        val exclusiveTest = extractFunction(
            controllerInstrumentationTest,
            "exclusiveCandidateScrollKeepsItsOwnRecyclerViewScrollableWithoutNestedHandoff"
        )
        val candidateInitializer = extractBlock(
            exclusiveTest,
            "candidateList = RecyclerView(activity).apply {"
        )
        val exclusiveDescription = "instrumented exclusive candidate list"
        assertTrue(
            "The exclusive matcher description must be assigned to candidateList",
            Regex(
                """(?m)^\s*contentDescription\s*=\s*"${Regex.escape(exclusiveDescription)}"\s*$"""
            ).containsMatchIn(candidateInitializer)
        )
        assertTrue(
            "The exclusive matcher must target candidateList's unique description",
            exclusiveTest.contains(
                "onView(withContentDescription(\"$exclusiveDescription\"))" +
                    ".perform(activeDrag)"
            )
        )
        assertEquals(
            "The exclusive candidate description evidence must stay inside the exclusive test",
            2,
            Regex(Regex.escape(exclusiveDescription))
                .findAll(exclusiveTest)
                .count()
        )
        assertAppearsInOrder(
            exclusiveTest,
            "startOffset = candidateList.computeVerticalScrollOffset()",
            "owner.disallowRequests.clear()",
            "activeDrag = object : ViewAction",
            "MotionEvent.ACTION_DOWN",
            "recyclerView.dispatchTouchEvent(event)",
            "MotionEvent.ACTION_MOVE",
            "recyclerView.dispatchTouchEvent(event)",
            "onView(withContentDescription(\"$exclusiveDescription\"))" +
                ".perform(activeDrag)",
            "assertTrue(candidateList.computeVerticalScrollOffset() > startOffset)",
            "assertTrue(owner.disallowRequests.last())",
            "controller.dispose()",
            "assertEquals(View.GONE, candidateList.visibility)",
            "assertTrue(candidateList.isNestedScrollingEnabled)",
            "assertFalse(owner.disallowRequests.last())",
            "val requestCountAfterDispose = owner.disallowRequests.size",
            "candidateList.visibility = View.VISIBLE",
            "val postDisposeDown = MotionEvent.obtain(",
            "MotionEvent.ACTION_DOWN",
            "candidateList.dispatchTouchEvent(postDisposeDown)",
            "postDisposeDown.recycle()",
            "assertEquals(requestCountAfterDispose, owner.disallowRequests.size)"
        )
        val beforeDispose = exclusiveTest.substringBefore("controller.dispose()")
        assertFalse(
            "The active gesture must not complete before dispose",
            beforeDispose.contains("swipeUp()")
        )
        assertFalse(beforeDispose.contains("MotionEvent.ACTION_UP"))
        assertFalse(beforeDispose.contains("MotionEvent.ACTION_CANCEL"))
    }

    @Test
    fun searchKeepsSelectedPlacesAcrossViewRecreation() {
        assertTrue(searchFragmentKt.contains("restoredOrigin"))
        assertTrue(searchFragmentKt.contains("restoredDestination"))
        assertTrue(searchFragmentKt.contains("onSaveInstanceState"))
        assertTrue(searchFragmentKt.contains("currentPlaceRequestState.beginAutoRequest"))
    }

    private fun extractFunction(source: String, name: String): String {
        return extractBlock(source, "fun $name(")
    }

    private fun extractBlock(source: String, marker: String): String {
        val start = source.indexOf(marker)
        assertTrue("Missing source block: $marker", start >= 0)
        val bodyStart = source.indexOf('{', start)
        assertTrue("Missing body for source block: $marker", bodyStart >= 0)
        var depth = 0
        for (index in bodyStart until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        error("Unterminated source block: $marker")
    }

    private fun assertAppearsInOrder(source: String, vararg expected: String) {
        var cursor = 0
        expected.forEach { fragment ->
            val index = source.indexOf(fragment, cursor)
            assertTrue("Missing or out-of-order instrumentation evidence: $fragment", index >= 0)
            cursor = index + fragment.length
        }
    }
}
