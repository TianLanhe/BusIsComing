package com.golink.busiscoming

import com.golink.busiscoming.ui.main.RouteDetailSheetDetent
import com.golink.busiscoming.ui.main.RouteDetailSheetPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteDetailSheetStateTest {
    @Test
    fun metricsKeepSummaryAdaptiveAndHalfAtLeastAsTall() {
        val normal = RouteDetailSheetPolicy.metrics(parentHeight = 800, summaryContentHeight = 160)
        assertEquals(216, normal.summaryHeight)
        assertEquals(0.55f, normal.halfExpandedRatio, 0.001f)

        val largeText = RouteDetailSheetPolicy.metrics(parentHeight = 800, summaryContentHeight = 520)
        assertEquals(520, largeText.summaryHeight)
        assertEquals(0.65f, largeText.halfExpandedRatio, 0.001f)
    }

    @Test
    fun gesturesFollowTheApprovedThreeDetentTransitions() {
        assertEquals(RouteDetailSheetDetent.FULL, RouteDetailSheetPolicy.onHandleClick(RouteDetailSheetDetent.SUMMARY))
        assertEquals(RouteDetailSheetDetent.FULL, RouteDetailSheetPolicy.onHandleClick(RouteDetailSheetDetent.HALF))
        assertEquals(RouteDetailSheetDetent.SUMMARY, RouteDetailSheetPolicy.onHandleClick(RouteDetailSheetDetent.FULL))
        assertEquals(RouteDetailSheetDetent.FULL, RouteDetailSheetPolicy.onSummaryContentSwipeUp())
        assertEquals(RouteDetailSheetDetent.HALF, RouteDetailSheetPolicy.onDownwardSettle(RouteDetailSheetDetent.FULL))
        assertEquals(RouteDetailSheetDetent.SUMMARY, RouteDetailSheetPolicy.onDownwardSettle(RouteDetailSheetDetent.HALF))
        assertEquals(RouteDetailSheetDetent.SUMMARY, RouteDetailSheetPolicy.onDownwardSettle(RouteDetailSheetDetent.SUMMARY))
    }
}
