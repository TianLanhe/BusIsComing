package com.golink.busiscoming

import com.golink.busiscoming.ui.main.SearchTripEditorTransitionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTripEditorTransitionPolicyTest {
    @Test
    fun `transition uses approved duration`() {
        assertEquals(240L, SearchTripEditorTransitionPolicy.DURATION_MS)
    }

    @Test
    fun `transition runs only when the request and view state are safe`() {
        assertTrue(
            SearchTripEditorTransitionPolicy.shouldAnimate(
                requested = true,
                isLaidOut = true,
                isAttached = true,
                systemAnimationsEnabled = true,
                lifecycleStarted = true
            )
        )
        assertFalse(
            SearchTripEditorTransitionPolicy.shouldAnimate(
                requested = true,
                isLaidOut = false,
                isAttached = true,
                systemAnimationsEnabled = true,
                lifecycleStarted = true
            )
        )
        assertFalse(
            SearchTripEditorTransitionPolicy.shouldAnimate(
                requested = true,
                isLaidOut = true,
                isAttached = true,
                systemAnimationsEnabled = false,
                lifecycleStarted = true
            )
        )
        assertFalse(
            SearchTripEditorTransitionPolicy.shouldAnimate(
                requested = true,
                isLaidOut = true,
                isAttached = true,
                systemAnimationsEnabled = true,
                lifecycleStarted = false
            )
        )
    }
}
