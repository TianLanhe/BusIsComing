package com.golink.busiscoming

import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.ui.main.RoutePinSwipeAction
import com.golink.busiscoming.ui.main.RoutePinSwipePolicy
import com.golink.busiscoming.ui.main.RoutePinSwipeThresholdTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePinSwipePolicyTest {
    @Test
    fun `right raises attention and left directly cancels either pinned level`() {
        assertEquals(
            RoutePinSwipeAction.PIN_TEMPORARY,
            RoutePinSwipePolicy.action(PinLevel.UNPINNED, eligible = true, deltaX = 80f, width = 100f)
        )
        assertEquals(
            RoutePinSwipeAction.PIN_PERSISTENT,
            RoutePinSwipePolicy.action(PinLevel.TEMPORARY, true, 80f, 100f)
        )
        assertEquals(
            RoutePinSwipeAction.CANCEL,
            RoutePinSwipePolicy.action(PinLevel.TEMPORARY, true, -80f, 100f)
        )
        assertEquals(
            RoutePinSwipeAction.CANCEL,
            RoutePinSwipePolicy.action(PinLevel.PERSISTENT, true, -80f, 100f)
        )
    }

    @Test
    fun `ordinary left and persistent right rebound without mutation`() {
        assertEquals(
            RoutePinSwipeAction.REBOUND,
            RoutePinSwipePolicy.action(PinLevel.UNPINNED, true, -80f, 100f)
        )
        assertEquals(
            RoutePinSwipeAction.REBOUND,
            RoutePinSwipePolicy.action(PinLevel.PERSISTENT, true, 80f, 100f)
        )
    }

    @Test
    fun `ineligible and below fortyPercent threshold always rebound`() {
        assertEquals(
            RoutePinSwipeAction.UNAVAILABLE,
            RoutePinSwipePolicy.action(PinLevel.UNPINNED, false, 80f, 100f)
        )
        assertEquals(
            RoutePinSwipeAction.REBOUND,
            RoutePinSwipePolicy.action(PinLevel.UNPINNED, true, 39f, 100f)
        )
        assertEquals(0.4f, RoutePinSwipePolicy.SWIPE_THRESHOLD)
    }

    @Test
    fun `velocity never bypasses distance threshold`() {
        assertFalse(RoutePinSwipePolicy.hasFlingShortcut)
        assertEquals(
            RoutePinSwipeAction.REBOUND,
            RoutePinSwipePolicy.action(
                PinLevel.TEMPORARY,
                eligible = true,
                deltaX = 10f,
                width = 100f,
                velocityX = 50_000f
            )
        )
    }

    @Test
    fun `threshold tracker vibrates once per drag only for mutating direction`() {
        val tracker = RoutePinSwipeThresholdTracker()

        assertFalse(tracker.shouldHaptic(PinLevel.UNPINNED, true, 20f, 100f))
        assertTrue(tracker.shouldHaptic(PinLevel.UNPINNED, true, 41f, 100f))
        assertFalse(tracker.shouldHaptic(PinLevel.UNPINNED, true, 20f, 100f))
        assertFalse(tracker.shouldHaptic(PinLevel.UNPINNED, true, 60f, 100f))
        tracker.reset()
        assertFalse(tracker.shouldHaptic(PinLevel.PERSISTENT, true, 60f, 100f))
        assertTrue(tracker.shouldHaptic(PinLevel.PERSISTENT, true, -41f, 100f))
    }
}
