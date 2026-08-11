package com.golink.busiscoming

import com.golink.busiscoming.data.location.CurrentLocationSnapshot
import com.golink.busiscoming.data.location.ForegroundLocationHeadingState
import com.golink.busiscoming.ui.main.CurrentLocationMapOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentLocationMapOverlayTest {
    @Test
    fun keepsAccuracyAreaButDoesNotInventDirectionBeforeFirstHeading() {
        val overlay = CurrentLocationMapOverlay.from(
            ForegroundLocationHeadingState(
                location = CurrentLocationSnapshot(22.3193, 114.1694, 18f, 100L)
            )
        )

        requireNotNull(overlay)
        assertEquals(22.3193, overlay.coordinate.latitude, 0.0)
        assertEquals(114.1694, overlay.coordinate.longitude, 0.0)
        assertEquals(18f, overlay.accuracyMeters)
        assertNull(overlay.headingDegrees)
    }

    @Test
    fun combinesLocationWithPhoneHeadingWithoutUsingTravelBearing() {
        val overlay = CurrentLocationMapOverlay.from(
            ForegroundLocationHeadingState(
                location = CurrentLocationSnapshot(22.3193, 114.1694, null, 100L),
                headingDegrees = 275f
            )
        )

        requireNotNull(overlay)
        assertEquals(275f, overlay.headingDegrees)
        assertNull(overlay.accuracyMeters)
    }

    @Test
    fun doesNotDrawDirectionAtAnUnknownPosition() {
        val overlay = CurrentLocationMapOverlay.from(
            ForegroundLocationHeadingState(headingDegrees = 90f)
        )

        assertNull(overlay)
    }
}
