package com.golink.busiscoming.ui.main

import com.golink.busiscoming.R
import com.golink.busiscoming.data.model.AppUpdateState
import com.golink.busiscoming.data.model.UpdateCheckTrigger
import com.golink.busiscoming.data.model.UpdateFailureKind
import com.golink.busiscoming.data.model.UpdateSnapshotState

data class UpdateSettingsUiModel(
    val summaryRes: Int,
    val versionArgument: String? = null,
    val showDot: Boolean,
    val rowEnabled: Boolean
)

object UpdateSettingsUiModelFactory {
    fun create(state: AppUpdateState, now: Long): UpdateSettingsUiModel {
        val version = state.snapshot.availableVersionName
            ?: state.snapshot.availableVersionCode?.toString()
        val summaryRes = when {
            state.isChecking -> R.string.update_status_checking
            state.snapshot.state == UpdateSnapshotState.UPDATE_AVAILABLE &&
                state.lastTrigger == UpdateCheckTrigger.MANUAL &&
                state.lastFailure != null -> R.string.update_status_available_failed
            state.snapshot.state == UpdateSnapshotState.UPDATE_AVAILABLE &&
                state.skippedVersionCode == state.snapshot.availableVersionCode ->
                R.string.update_status_available_skipped
            state.snapshot.state == UpdateSnapshotState.UPDATE_AVAILABLE &&
                state.deferredVersionCode == state.snapshot.availableVersionCode &&
                state.deferredUntil?.let { now < it } == true ->
                R.string.update_status_available_deferred
            state.snapshot.state == UpdateSnapshotState.UPDATE_AVAILABLE ->
                R.string.update_status_available
            state.lastTrigger == UpdateCheckTrigger.MANUAL &&
                state.lastFailure?.kind in setOf(
                    UpdateFailureKind.PLAY_APP_NOT_OWNED,
                    UpdateFailureKind.PLAY_DEBUG_BUILD_UNSUPPORTED
                ) -> R.string.update_status_unverified
            state.snapshot.state == UpdateSnapshotState.UP_TO_DATE ->
                R.string.update_status_up_to_date
            state.snapshot.state == UpdateSnapshotState.NEVER_CHECKED &&
                (state.lastFailure == null || state.lastTrigger == UpdateCheckTrigger.AUTOMATIC) ->
                R.string.update_status_never_checked
            state.lastFailure?.kind == UpdateFailureKind.PLAY_UNAVAILABLE ->
                R.string.update_play_unavailable
            else -> R.string.update_status_failed
        }
        return UpdateSettingsUiModel(
            summaryRes = summaryRes,
            versionArgument = version.takeIf {
                state.snapshot.state == UpdateSnapshotState.UPDATE_AVAILABLE
            },
            showDot = state.snapshot.hasNewerVersion,
            rowEnabled = !state.isChecking
        )
    }
}
