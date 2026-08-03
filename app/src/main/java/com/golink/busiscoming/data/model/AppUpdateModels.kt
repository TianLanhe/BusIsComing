package com.golink.busiscoming.data.model

enum class UpdateCheckTrigger {
    AUTOMATIC,
    MANUAL
}

enum class InitialInstallChannel {
    PLAY,
    NON_PLAY,
    UNKNOWN_NON_PLAY
}

enum class UpdateChannel {
    PLAY,
    WEBSITE,
    PLAY_UNAVAILABLE
}

enum class UpdateSnapshotState {
    NEVER_CHECKED,
    UP_TO_DATE,
    UPDATE_AVAILABLE
}

enum class UpdateAttemptOutcome {
    SUCCESS,
    FAILED
}

enum class UpdateFailureKind {
    PLAY_UNAVAILABLE,
    PLAY_APP_NOT_OWNED,
    PLAY_DEBUG_BUILD_UNSUPPORTED,
    PLAY_TEMPORARY,
    NETWORK,
    INVALID_METADATA,
    EXTERNAL_ACTION,
    UNKNOWN
}

data class UpdateFailure(
    val kind: UpdateFailureKind,
    val retainedReliableSnapshot: Boolean = false
)

data class UpdateSnapshot(
    val state: UpdateSnapshotState,
    val channel: UpdateChannel? = null,
    val installedVersionCode: Long,
    val availableVersionCode: Long? = null,
    val availableVersionName: String? = null,
    val availableSinceAt: Long? = null,
    val firstSeenAt: Long? = null,
    val checkedAt: Long? = null,
    val flexibleAllowed: Boolean = false
) {
    val hasNewerVersion: Boolean
        get() = state == UpdateSnapshotState.UPDATE_AVAILABLE &&
            availableVersionCode != null &&
            availableVersionCode > installedVersionCode

    companion object {
        fun neverChecked(installedVersionCode: Long): UpdateSnapshot = UpdateSnapshot(
            state = UpdateSnapshotState.NEVER_CHECKED,
            installedVersionCode = installedVersionCode
        )

        fun upToDate(
            installedVersionCode: Long,
            channel: UpdateChannel,
            checkedAt: Long
        ): UpdateSnapshot = UpdateSnapshot(
            state = UpdateSnapshotState.UP_TO_DATE,
            channel = channel,
            installedVersionCode = installedVersionCode,
            checkedAt = checkedAt
        )
    }
}

sealed interface UpdateCheckResult {
    data class Reliable(val snapshot: UpdateSnapshot) : UpdateCheckResult

    data class Failed(
        val failure: UpdateFailure,
        val reliableSnapshot: UpdateSnapshot
    ) : UpdateCheckResult
}

data class AppUpdateState(
    val snapshot: UpdateSnapshot,
    val isChecking: Boolean = false,
    val lastTrigger: UpdateCheckTrigger? = null,
    val lastFailure: UpdateFailure? = null,
    val playUpdateDownloaded: Boolean = false,
    val deferredVersionCode: Long? = null,
    val deferredUntil: Long? = null,
    val skippedVersionCode: Long? = null,
    val resultGeneration: Long = 0L
)
