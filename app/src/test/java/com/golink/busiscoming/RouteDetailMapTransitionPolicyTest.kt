package com.golink.busiscoming

import com.golink.busiscoming.ui.main.RouteDetailMapTransitionAction
import com.golink.busiscoming.ui.main.RouteDetailMapTransitionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailMapTransitionPolicyTest {
    @Test
    fun upwardDragAppliesCandidatePaddingOnlyOnceAndNeverRelayoutsPerFrame() {
        val policy = RouteDetailMapTransitionPolicy()
        val generation = policy.begin(stableVisibleHeight = 220)

        assertEquals(
            RouteDetailMapTransitionAction.TranslateAttribution(-40),
            policy.slide(generation, visibleHeight = 260, upwardCandidatePadding = 430)
        )
        assertEquals(
            RouteDetailMapTransitionAction.ApplyCandidatePadding(430, -80),
            policy.slide(generation, visibleHeight = 300, upwardCandidatePadding = 430)
        )
        assertEquals(
            RouteDetailMapTransitionAction.TranslateAttribution(-140),
            policy.slide(generation, visibleHeight = 360, upwardCandidatePadding = 430)
        )
        assertTrue(policy.slide(generation + 1, 380, 430) is RouteDetailMapTransitionAction.Ignore)
        assertEquals(
            RouteDetailMapTransitionAction.CommitStablePadding(440),
            policy.settle(generation, stableVisibleHeight = 440)
        )
    }

    @Test
    fun downwardDragKeepsLargerStablePaddingUntilOneExactCommit() {
        val policy = RouteDetailMapTransitionPolicy()
        val generation = policy.begin(stableVisibleHeight = 520)

        assertEquals(
            RouteDetailMapTransitionAction.TranslateAttribution(60),
            policy.slide(generation, visibleHeight = 460, upwardCandidatePadding = 520)
        )
        assertEquals(
            RouteDetailMapTransitionAction.TranslateAttribution(180),
            policy.slide(generation, visibleHeight = 340, upwardCandidatePadding = 520)
        )
        assertEquals(
            RouteDetailMapTransitionAction.CommitStablePadding(220),
            policy.settle(generation, stableVisibleHeight = 220)
        )
        assertTrue(policy.settle(generation, 220) is RouteDetailMapTransitionAction.Ignore)
    }

    @Test
    fun cancelledGenerationIgnoresLateSlideAndSettleCallbacks() {
        val policy = RouteDetailMapTransitionPolicy()
        val generation = policy.begin(stableVisibleHeight = 220)
        policy.cancel(generation)

        assertTrue(policy.slide(generation, 300, 440) is RouteDetailMapTransitionAction.Ignore)
        assertTrue(policy.settle(generation, 300) is RouteDetailMapTransitionAction.Ignore)
    }
}
