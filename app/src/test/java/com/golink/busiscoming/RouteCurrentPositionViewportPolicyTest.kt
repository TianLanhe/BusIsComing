package com.golink.busiscoming

import com.golink.busiscoming.ui.main.RouteDetailViewportPolicy
import com.golink.busiscoming.ui.main.RouteSummaryGesturePolicy
import com.golink.busiscoming.ui.main.RouteSummaryViewportPolicy
import com.golink.busiscoming.ui.main.SummaryScrollState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCurrentPositionViewportPolicyTest {
    @Test
    fun summaryAutoScrollsFirstMatchAndChangedSegmentUntilUserScrolls() {
        val policy = RouteSummaryViewportPolicy()

        assertTrue(policy.shouldAutoScrollTo("leg-0-card"))
        assertFalse(policy.shouldAutoScrollTo("leg-0-card"))
        assertTrue(policy.shouldAutoScrollTo("walk-transfer-0"))
        policy.onUserScroll()
        assertFalse(policy.shouldAutoScrollTo("leg-1-card"))
    }

    @Test
    fun restoredSummaryOwnershipDoesNotStealViewport() {
        val policy = RouteSummaryViewportPolicy(
            SummaryScrollState(120, true, "leg-0-card")
        )

        assertFalse(policy.shouldAutoScrollTo("leg-1-card"))
        assertTrue(policy.state().ownedByUser)
    }

    @Test
    fun detailStopsFollowingAfterManualScrollAndResumesOnReenteringFull() {
        val policy = RouteDetailViewportPolicy()
        assertTrue(policy.shouldFollow())

        policy.onUserScroll()
        assertFalse(policy.shouldFollow())

        policy.onEnterFull()
        assertTrue(policy.shouldFollow())
    }

    @Test
    fun detailOwnershipDoesNotAffectSummaryPinOwnership() {
        val detail = RouteDetailViewportPolicy()
        val summary = RouteSummaryViewportPolicy()
        detail.onUserScroll()

        assertTrue(summary.shouldAutoScrollTo("leg-0-card"))
    }

    @Test
    fun summaryOwnershipOnlyTransfersForHorizontalDragPastTouchSlop() {
        assertFalse(RouteSummaryGesturePolicy.isHorizontalDrag(4f, 1f, 8f))
        assertFalse(RouteSummaryGesturePolicy.isHorizontalDrag(12f, 18f, 8f))
        assertTrue(RouteSummaryGesturePolicy.isHorizontalDrag(12f, 4f, 8f))
        assertTrue(RouteSummaryGesturePolicy.isHorizontalDrag(-12f, 4f, 8f))
    }
}
