package com.golink.busiscoming

import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.ui.main.RoutePinSwipeAction
import com.golink.busiscoming.ui.main.RoutePinSwipeGeometry
import com.golink.busiscoming.ui.main.RoutePinSwipePolicy
import com.golink.busiscoming.ui.main.RoutePinSwipeReleaseTracker
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
            RoutePinSwipePolicy.action(
                PinLevel.UNPINNED,
                eligible = true,
                deltaX = 80f,
                triggerDistance = 72f
            )
        )
        assertEquals(
            RoutePinSwipeAction.PIN_PERSISTENT,
            RoutePinSwipePolicy.action(PinLevel.TEMPORARY, true, 80f, 72f)
        )
        assertEquals(
            RoutePinSwipeAction.CANCEL,
            RoutePinSwipePolicy.action(PinLevel.TEMPORARY, true, -80f, 72f)
        )
        assertEquals(
            RoutePinSwipeAction.CANCEL,
            RoutePinSwipePolicy.action(PinLevel.PERSISTENT, true, -80f, 72f)
        )
    }

    @Test
    fun `ordinary left and persistent right rebound without mutation`() {
        assertEquals(
            RoutePinSwipeAction.REBOUND,
            RoutePinSwipePolicy.action(PinLevel.UNPINNED, true, -80f, 72f)
        )
        assertEquals(
            RoutePinSwipeAction.REBOUND,
            RoutePinSwipePolicy.action(PinLevel.PERSISTENT, true, 80f, 72f)
        )
    }

    @Test
    fun `trigger distance follows measured label and the card has a finite end`() {
        val geometry = RoutePinSwipeGeometry.fromLabel(
            labelWidth = 56f,
            edgePadding = 16f,
            overshoot = 8f
        )

        assertEquals(72f, geometry.triggerDistance)
        assertEquals(80f, geometry.maxDistance)
        assertEquals(71f, geometry.clamp(71f))
        assertEquals(80f, geometry.clamp(200f))
        assertEquals(-80f, geometry.clamp(-200f))
    }

    @Test
    fun `ineligible action and distance below the measured label threshold do not mutate`() {
        assertEquals(
            RoutePinSwipeAction.UNAVAILABLE,
            RoutePinSwipePolicy.action(PinLevel.UNPINNED, false, 80f, 72f)
        )
        assertEquals(
            RoutePinSwipeAction.REBOUND,
            RoutePinSwipePolicy.action(PinLevel.UNPINNED, true, 71f, 72f)
        )
        assertEquals(
            RoutePinSwipeAction.PIN_TEMPORARY,
            RoutePinSwipePolicy.action(PinLevel.UNPINNED, true, 72f, 72f)
        )
    }

    @Test
    fun `velocity never bypasses distance threshold`() {
        assertFalse(RoutePinSwipePolicy.hasFlingShortcut)
        assertEquals(Float.MAX_VALUE, RoutePinSwipePolicy.dismissThreshold)
        assertEquals(
            RoutePinSwipeAction.REBOUND,
            RoutePinSwipePolicy.action(
                PinLevel.TEMPORARY,
                eligible = true,
                deltaX = 10f,
                triggerDistance = 72f,
                velocityX = 50_000f
            )
        )
    }

    @Test
    fun `threshold tracker vibrates once per drag only for mutating direction`() {
        val tracker = RoutePinSwipeThresholdTracker()

        assertFalse(tracker.shouldHaptic(PinLevel.UNPINNED, true, 20f, 72f))
        assertTrue(tracker.shouldHaptic(PinLevel.UNPINNED, true, 72f, 72f))
        assertFalse(tracker.shouldHaptic(PinLevel.UNPINNED, true, 20f, 72f))
        assertFalse(tracker.shouldHaptic(PinLevel.UNPINNED, true, 90f, 72f))
        tracker.reset()
        assertFalse(tracker.shouldHaptic(PinLevel.PERSISTENT, true, 90f, 72f))
        assertTrue(tracker.shouldHaptic(PinLevel.PERSISTENT, true, -72f, 72f))
    }

    @Test
    fun `release tracker keeps the active drag decision until rebound finishes`() {
        val tracker = RoutePinSwipeReleaseTracker()

        tracker.update("route:1", PinLevel.TEMPORARY, true, 80f, 72f)

        assertEquals(RoutePinSwipeAction.PIN_PERSISTENT, tracker.consume("route:1"))
        assertEquals(null, tracker.consume("route:1"))

        tracker.update("route:1", PinLevel.TEMPORARY, true, 80f, 72f)
        assertEquals(null, tracker.consume("route:2"))
        assertEquals(null, tracker.consume("route:1"))
    }
}
