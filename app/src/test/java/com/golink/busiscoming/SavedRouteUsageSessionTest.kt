package com.golink.busiscoming

import com.golink.busiscoming.ui.main.SavedRouteUsageSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRouteUsageSessionTest {
    @Test
    fun recordsOnlyFirstQueryForContinuouslySelectedRoute() {
        val session = SavedRouteUsageSession()
        session.selectSavedRoute(1L)

        assertTrue(session.consumeUsageRecord(1L))
        assertFalse(session.consumeUsageRecord(1L))
    }

    @Test
    fun switchingSavedRoutesMakesPreviousRouteEligibleAgain() {
        val session = SavedRouteUsageSession()
        session.selectSavedRoute(1L)
        assertTrue(session.consumeUsageRecord(1L))

        session.selectSavedRoute(2L)
        assertTrue(session.consumeUsageRecord(2L))
        session.selectSavedRoute(1L)

        assertTrue(session.consumeUsageRecord(1L))
    }

    @Test
    fun selectingSameRouteAndTemporaryQueryDoNotResetEligibility() {
        val session = SavedRouteUsageSession()
        session.selectSavedRoute(1L)
        assertTrue(session.consumeUsageRecord(1L))

        session.selectSavedRoute(1L)
        session.onTemporaryQuery()

        assertFalse(session.consumeUsageRecord(1L))
    }

    @Test
    fun restoredRecordedStatePreventsDuplicateAfterConfigurationChange() {
        val session = SavedRouteUsageSession(
            selectedRouteId = 7L,
            recordedRouteId = 7L
        )

        assertFalse(session.consumeUsageRecord(7L))
    }

    @Test
    fun restoredStateForAnotherRouteIsIgnored() {
        val session = SavedRouteUsageSession(
            selectedRouteId = 8L,
            recordedRouteId = 7L
        )

        assertTrue(session.consumeUsageRecord(8L))
    }

    @Test
    fun newAppSessionCanRecordSameRouteAgain() {
        val previousSession = SavedRouteUsageSession()
        previousSession.selectSavedRoute(3L)
        assertTrue(previousSession.consumeUsageRecord(3L))

        val newSession = SavedRouteUsageSession()
        newSession.selectSavedRoute(3L)

        assertTrue(newSession.consumeUsageRecord(3L))
    }
}
