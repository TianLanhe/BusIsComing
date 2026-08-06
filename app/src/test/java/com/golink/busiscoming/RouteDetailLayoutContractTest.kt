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
    fun compactSummaryDoesNotUseVisibleFortyEightDpSegmentRows() {
        assertTrue(adapterSource.contains("SummarySegmentTouchDelegate"))
        assertTrue(adapterSource.contains("ViewGroup.LayoutParams.WRAP_CONTENT,\n                    dp(22)"))
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
}
