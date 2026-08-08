package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailLayoutContractTest {
    private val adapterSource = File(
        "src/main/java/com/golink/busiscoming/ui/main/RouteDetailAdapter.kt"
    ).readText()
    private val activitySource = File(
        "src/main/java/com/golink/busiscoming/ui/main/RouteDetailActivity.kt"
    ).readText()
    private val layoutSource = File("src/main/res/layout/activity_route_detail.xml").readText()

    @Test
    fun enlargedSummaryStillDoesNotUseVisibleFortyEightDpSegmentRows() {
        assertTrue(adapterSource.contains("SummarySegmentTouchDelegate"))
        assertTrue(adapterSource.contains("ViewGroup.LayoutParams.WRAP_CONTENT,\n                    dp(30)"))
        assertFalse(
            adapterSource.contains(
                "ViewGroup.LayoutParams.WRAP_CONTENT,\n                    dp(48)"
            )
        )
    }

    @Test
    fun fullScreenSafeInsetIsAppliedToTheSheetContentContainer() {
        assertTrue(layoutSource.contains("android:id=\"@+id/routeDetailSheetContent\""))
        assertTrue(activitySource.contains("sheetContent.setPadding"))
        assertFalse(activitySource.contains("sheet.setPadding(0, if (full) statusBarInset"))
    }

    @Test
    fun revisedHandleAndSummaryUseCompactVisibleSizesWithExpandedTouchTargets() {
        assertTrue(layoutSource.contains("android:id=\"@+id/routeDetailSheetHandle\""))
        assertTrue(layoutSource.contains("android:layout_height=\"28dp\""))
        assertTrue(activitySource.contains("installSheetHandleTouchDelegate"))
        assertTrue(adapterSource.contains("dp(30)"))
        assertTrue(adapterSource.contains("dp(24)"))
        assertTrue(adapterSource.contains("17f, true"))
        assertTrue(adapterSource.contains("12f, false"))
        assertTrue(adapterSource.contains("text(timing, 21f"))
        assertTrue(adapterSource.contains("text(meta, 13f"))
    }

    @Test
    fun csdiAttributionHasCompactVisibleSurfaceAndSeparateTouchHeight() {
        val block = layoutSource.substringAfter("android:id=\"@+id/routeDetailCsdiAttribution\"")
            .substringBefore("android:id=\"@+id/routeDetailSheet\"")
        assertTrue(block.contains("android:layout_width=\"116dp\""))
        assertTrue(block.contains("android:layout_height=\"48dp\""))
        assertTrue(block.contains("android:id=\"@+id/routeDetailCsdiAttributionSurface\""))
        assertTrue(block.contains("android:layout_height=\"29dp\""))
        assertTrue(block.contains("android:layout_width=\"15dp\""))
        assertTrue(block.contains("app:strokeWidth=\"0dp\""))
        assertTrue(block.contains("app:cardElevation=\"0dp\""))
        assertTrue(block.contains("MaxFontScaleTextView"))
        assertTrue(activitySource.contains("csdiAttributionSurface.getLocationOnScreen"))
        assertTrue(activitySource.contains("csdiAttribution.translationY"))
    }

    @Test
    fun slideHotPathDoesNotCommitLayoutOrRebuildOverlays() {
        val slideBlock = activitySource.substringAfter("private fun onMapTransitionSlide")
            .substringBefore("private fun nextUpwardDetentVisibleHeight")
        assertTrue(slideBlock.contains("translationY"))
        assertTrue(slideBlock.contains("ApplyCandidatePadding"))
        assertFalse(slideBlock.contains("layoutParams"))
        assertFalse(slideBlock.contains("commitStableMapLayout"))
        assertFalse(slideBlock.contains("relayout"))
    }
}
