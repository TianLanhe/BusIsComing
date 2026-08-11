package com.golink.busiscoming

import com.golink.busiscoming.data.location.CurrentLocationSnapshot
import com.golink.busiscoming.ui.main.RouteDetailLocationHeadingPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailLocationHeadingPolicyTest {
    @Test
    fun tracksOnlyWhileResumedWithUsableMapPermissionAndSystemLocation() {
        assertTrue(
            RouteDetailLocationHeadingPolicy.shouldTrack(
                resumed = true,
                hasPermission = true,
                systemLocationEnabled = true,
                mapUsable = true
            )
        )
        assertFalse(RouteDetailLocationHeadingPolicy.shouldTrack(false, true, true, true))
        assertFalse(RouteDetailLocationHeadingPolicy.shouldTrack(true, false, true, true))
        assertFalse(RouteDetailLocationHeadingPolicy.shouldTrack(true, true, false, true))
        assertFalse(RouteDetailLocationHeadingPolicy.shouldTrack(true, true, true, false))
    }

    @Test
    fun usesTrackedLocationForClickOnlyWithinFreshnessWindow() {
        val snapshot = CurrentLocationSnapshot(22.3, 114.2, 12f, 10_000L)

        assertTrue(RouteDetailLocationHeadingPolicy.isFresh(snapshot, nowElapsedMillis = 40_000L))
        assertFalse(RouteDetailLocationHeadingPolicy.isFresh(snapshot, nowElapsedMillis = 40_001L))
        assertFalse(RouteDetailLocationHeadingPolicy.isFresh(snapshot, nowElapsedMillis = 9_999L))
    }
}
