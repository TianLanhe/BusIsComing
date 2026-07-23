package com.golink.busiscoming

import com.golink.busiscoming.data.model.AppUpdateState
import com.golink.busiscoming.data.model.UpdateChannel
import com.golink.busiscoming.data.model.UpdateFailure
import com.golink.busiscoming.data.model.UpdateFailureKind
import com.golink.busiscoming.data.model.UpdateSnapshot
import com.golink.busiscoming.data.model.UpdateSnapshotState
import com.golink.busiscoming.ui.main.UpdateSettingsUiModelFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSettingsUiModelTest {
    private val now = 1_000L

    @Test
    fun mapsNeverCheckedCheckingAndUpToDateStates() {
        val never = UpdateSettingsUiModelFactory.create(
            AppUpdateState(UpdateSnapshot.neverChecked(6L)),
            now
        )
        assertEquals(R.string.update_status_never_checked, never.summaryRes)
        assertFalse(never.showDot)
        assertTrue(never.rowEnabled)

        val checking = UpdateSettingsUiModelFactory.create(
            AppUpdateState(UpdateSnapshot.neverChecked(6L), isChecking = true),
            now
        )
        assertEquals(R.string.update_status_checking, checking.summaryRes)
        assertFalse(checking.rowEnabled)

        val latest = UpdateSettingsUiModelFactory.create(
            AppUpdateState(UpdateSnapshot.upToDate(6L, UpdateChannel.PLAY, now)),
            now
        )
        assertEquals(R.string.update_status_up_to_date, latest.summaryRes)
        assertFalse(latest.showDot)
    }

    @Test
    fun mapsAvailableDeferredSkippedAndRetainedFailureWithoutClearingDot() {
        val base = AppUpdateState(availableSnapshot())

        assertModel(base, R.string.update_status_available)
        assertModel(
            base.copy(deferredVersionCode = 8L, deferredUntil = now + 1L),
            R.string.update_status_available_deferred
        )
        assertModel(
            base.copy(skippedVersionCode = 8L),
            R.string.update_status_available_skipped
        )
        assertModel(
            base.copy(
                lastFailure = UpdateFailure(
                    UpdateFailureKind.NETWORK,
                    retainedReliableSnapshot = true
                )
            ),
            R.string.update_status_available_failed
        )
    }

    @Test
    fun mapsFailureWithoutSnapshotAndPlayUnavailable() {
        val failed = UpdateSettingsUiModelFactory.create(
            AppUpdateState(
                snapshot = UpdateSnapshot.neverChecked(6L),
                lastFailure = UpdateFailure(UpdateFailureKind.NETWORK)
            ),
            now
        )
        assertEquals(R.string.update_status_failed, failed.summaryRes)
        assertFalse(failed.showDot)

        val playUnavailable = UpdateSettingsUiModelFactory.create(
            AppUpdateState(
                snapshot = UpdateSnapshot.neverChecked(6L),
                lastFailure = UpdateFailure(UpdateFailureKind.PLAY_UNAVAILABLE)
            ),
            now
        )
        assertEquals(R.string.update_play_unavailable, playUnavailable.summaryRes)
    }

    private fun assertModel(state: AppUpdateState, expectedSummary: Int) {
        val model = UpdateSettingsUiModelFactory.create(state, now)
        assertEquals(expectedSummary, model.summaryRes)
        assertEquals("1.2", model.versionArgument)
        assertTrue(model.showDot)
        assertTrue(model.rowEnabled)
    }

    private fun availableSnapshot() = UpdateSnapshot(
        state = UpdateSnapshotState.UPDATE_AVAILABLE,
        channel = UpdateChannel.PLAY,
        installedVersionCode = 6L,
        availableVersionCode = 8L,
        availableVersionName = "1.2",
        availableSinceAt = 1L,
        firstSeenAt = 1L,
        checkedAt = 1L,
        flexibleAllowed = true
    )
}
