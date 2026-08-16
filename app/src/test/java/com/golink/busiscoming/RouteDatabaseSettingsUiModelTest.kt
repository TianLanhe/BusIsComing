package com.golink.busiscoming

import com.golink.busiscoming.data.repository.RouteDatabaseUpdateState
import com.golink.busiscoming.ui.main.RouteDatabaseSettingsUiModelFactory
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDatabaseSettingsUiModelTest {
    @Test
    fun neverCompletedAndCheckingStatesAreDistinct() {
        val idle = RouteDatabaseSettingsUiModelFactory.create(
            RouteDatabaseUpdateState.Idle(null),
            Locale.TRADITIONAL_CHINESE
        )
        val checking = RouteDatabaseSettingsUiModelFactory.create(
            RouteDatabaseUpdateState.Checking(null),
            Locale.TRADITIONAL_CHINESE
        )

        assertEquals(R.string.route_database_status_never_completed, idle.summaryRes)
        assertTrue(idle.rowEnabled)
        assertNull(idle.completedAtText)
        assertEquals(R.string.route_database_status_checking, checking.summaryRes)
        assertFalse(checking.rowEnabled)
    }

    @Test
    fun successfulStatesKeepHongKongCompletionTimeAndChangedOutcome() {
        val unchanged = RouteDatabaseSettingsUiModelFactory.create(
            RouteDatabaseUpdateState.Success(HK_NOON_MILLIS, changed = false),
            Locale.ENGLISH
        )
        val changed = RouteDatabaseSettingsUiModelFactory.create(
            RouteDatabaseUpdateState.Success(HK_NOON_MILLIS, changed = true),
            Locale.ENGLISH
        )

        assertEquals(R.string.route_database_status_latest, unchanged.summaryRes)
        assertEquals(R.string.route_database_status_updated, changed.summaryRes)
        assertEquals("Aug 17, 2026, 12:00\u202FPM", changed.completedAtText)
        assertTrue(changed.rowEnabled)
    }

    @Test
    fun failureKeepsPreviousSuccessfulTime() {
        val model = RouteDatabaseSettingsUiModelFactory.create(
            RouteDatabaseUpdateState.Failure(HK_NOON_MILLIS, "network"),
            Locale.ENGLISH
        )

        assertEquals(R.string.route_database_status_failed_using_previous, model.summaryRes)
        assertEquals("Aug 17, 2026, 12:00\u202FPM", model.completedAtText)
        assertTrue(model.rowEnabled)
    }

    companion object {
        private const val HK_NOON_MILLIS = 1_786_939_200_000L
    }
}
