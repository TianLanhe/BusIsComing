package com.golink.busiscoming

import com.golink.busiscoming.data.model.AppUpdateState
import com.golink.busiscoming.data.model.UpdateChannel
import com.golink.busiscoming.data.model.UpdateCheckTrigger
import com.golink.busiscoming.data.model.UpdateFailure
import com.golink.busiscoming.data.model.UpdateFailureKind
import com.golink.busiscoming.data.model.UpdateSnapshot
import com.golink.busiscoming.data.model.UpdateSnapshotState
import com.golink.busiscoming.ui.main.UpdateSettingsUiModelFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
                lastTrigger = UpdateCheckTrigger.MANUAL,
                lastFailure = UpdateFailure(
                    UpdateFailureKind.NETWORK,
                    retainedReliableSnapshot = true
                )
            ),
            R.string.update_status_available_failed
        )
    }

    @Test
    fun formatsAvailableVersionNameWithSingleLowercaseVPrefix() {
        val plain = UpdateSettingsUiModelFactory.create(
            AppUpdateState(availableSnapshot().copy(availableVersionName = "1.2")),
            now
        )
        val alreadyPrefixed = UpdateSettingsUiModelFactory.create(
            AppUpdateState(availableSnapshot().copy(availableVersionName = "V1.2")),
            now
        )

        assertEquals("v1.2", plain.versionArgument)
        assertEquals("v1.2", alreadyPrefixed.versionArgument)
    }

    @Test
    fun missingVersionNameNeverFallsBackToVersionCode() {
        val model = UpdateSettingsUiModelFactory.create(
            AppUpdateState(availableSnapshot().copy(availableVersionName = null)),
            now
        )

        assertNull(model.versionArgument)
        assertEquals(R.string.update_status_available_generic, model.summaryRes)
        assertTrue(model.showDot)
    }

    @Test
    fun legacyVersionNameEqualToVersionCodeIsRenderedAsGenericUpdate() {
        val model = UpdateSettingsUiModelFactory.create(
            AppUpdateState(availableSnapshot().copy(availableVersionName = "8")),
            now
        )

        assertNull(model.versionArgument)
        assertEquals(R.string.update_status_available_generic, model.summaryRes)
        assertTrue(model.showDot)
    }

    @Test
    fun missingVersionNameKeepsDeferredSkippedAndFailedMeaningWithoutNumber() {
        val base = AppUpdateState(availableSnapshot().copy(availableVersionName = null))
        val cases = listOf(
            base.copy(deferredVersionCode = 8L, deferredUntil = now + 1L) to
                R.string.update_status_available_deferred_generic,
            base.copy(skippedVersionCode = 8L) to
                R.string.update_status_available_skipped_generic,
            base.copy(
                lastTrigger = UpdateCheckTrigger.MANUAL,
                lastFailure = UpdateFailure(
                    UpdateFailureKind.NETWORK,
                    retainedReliableSnapshot = true
                )
            ) to R.string.update_status_available_failed_generic
        )

        cases.forEach { (state, expectedSummary) ->
            val model = UpdateSettingsUiModelFactory.create(state, now)
            assertEquals(expectedSummary, model.summaryRes)
            assertNull(model.versionArgument)
            assertTrue(model.showDot)
        }
    }

    @Test
    fun manualUnverifiableFailureOverridesStaleUpToDateSummary() {
        val state = AppUpdateState(
            snapshot = UpdateSnapshot.upToDate(6L, UpdateChannel.PLAY, now),
            lastTrigger = UpdateCheckTrigger.MANUAL,
            lastFailure = UpdateFailure(UpdateFailureKind.PLAY_APP_NOT_OWNED)
        )

        assertEquals(
            R.string.update_status_unverified,
            UpdateSettingsUiModelFactory.create(state, now).summaryRes
        )
    }

    @Test
    fun automaticFailureKeepsReliableUpToDateSummary() {
        val state = AppUpdateState(
            snapshot = UpdateSnapshot.upToDate(6L, UpdateChannel.PLAY, now),
            lastTrigger = UpdateCheckTrigger.AUTOMATIC,
            lastFailure = UpdateFailure(UpdateFailureKind.PLAY_APP_NOT_OWNED)
        )

        assertEquals(
            R.string.update_status_up_to_date,
            UpdateSettingsUiModelFactory.create(state, now).summaryRes
        )
    }

    @Test
    fun automaticFailureKeepsNeverCheckedAndAvailableSummaries() {
        val neverChecked = AppUpdateState(
            snapshot = UpdateSnapshot.neverChecked(6L),
            lastTrigger = UpdateCheckTrigger.AUTOMATIC,
            lastFailure = UpdateFailure(UpdateFailureKind.PLAY_DEBUG_BUILD_UNSUPPORTED)
        )
        assertEquals(
            R.string.update_status_never_checked,
            UpdateSettingsUiModelFactory.create(neverChecked, now).summaryRes
        )

        val available = AppUpdateState(
            snapshot = availableSnapshot(),
            lastTrigger = UpdateCheckTrigger.AUTOMATIC,
            lastFailure = UpdateFailure(
                UpdateFailureKind.PLAY_APP_NOT_OWNED,
                retainedReliableSnapshot = true
            )
        )
        assertEquals(
            R.string.update_status_available,
            UpdateSettingsUiModelFactory.create(available, now).summaryRes
        )
        assertTrue(UpdateSettingsUiModelFactory.create(available, now).showDot)
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
        assertEquals("v1.2", model.versionArgument)
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
