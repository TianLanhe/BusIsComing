package com.golink.busiscoming.data.update

import android.content.Context
import com.golink.busiscoming.data.model.InitialInstallChannel
import com.golink.busiscoming.data.model.UpdateAttemptOutcome
import com.golink.busiscoming.data.model.UpdateChannel
import com.golink.busiscoming.data.model.UpdateSnapshot
import com.golink.busiscoming.data.model.UpdateSnapshotState

interface UpdateKeyValueStore {
    fun readAll(): Map<String, String>
    fun replaceAll(values: Map<String, String>)
}

data class UpdateStoredState(
    val initialInstallChannel: InitialInstallChannel? = null,
    val lastAutoAttemptAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val lastSuccessfulCheckAt: Long? = null,
    val lastAttemptOutcome: UpdateAttemptOutcome? = null,
    val snapshot: UpdateSnapshot = UpdateSnapshot.neverChecked(0L),
    val deferredVersionCode: Long? = null,
    val deferredUntil: Long? = null,
    val skippedVersionCode: Long? = null,
    val playUpdateDownloaded: Boolean = false
) {
    fun reminderState(): UpdateReminderState = UpdateReminderState(
        deferredVersionCode = deferredVersionCode,
        deferredUntil = deferredUntil,
        skippedVersionCode = skippedVersionCode
    )

    companion object {
        fun initial(installedVersionCode: Long): UpdateStoredState = UpdateStoredState(
            snapshot = UpdateSnapshot.neverChecked(installedVersionCode)
        )
    }
}

interface UpdateStateStore {
    fun load(): UpdateStoredState
    fun save(state: UpdateStoredState)
    fun synchronizeInstalledVersion(currentVersionCode: Long, now: Long): UpdateStoredState
    fun recordReliableSnapshot(snapshot: UpdateSnapshot): UpdateStoredState
}

class SharedPreferencesUpdateStateStore(
    private val backend: UpdateKeyValueStore,
    private val installedVersionCode: Long
) : UpdateStateStore {
    constructor(context: Context, installedVersionCode: Long) : this(
        SharedPreferencesUpdateKeyValueStore(context),
        installedVersionCode
    )

    override fun load(): UpdateStoredState {
        val values = backend.readAll()
        val snapshot = decodeSnapshot(values) ?: UpdateSnapshot.neverChecked(installedVersionCode)
        return UpdateStoredState(
            initialInstallChannel = values.enumValue<InitialInstallChannel>(
                KEY_INITIAL_INSTALL_CHANNEL
            ),
            lastAutoAttemptAt = values.longValue(KEY_LAST_AUTO_ATTEMPT_AT),
            lastAttemptAt = values.longValue(KEY_LAST_ATTEMPT_AT),
            lastSuccessfulCheckAt = values.longValue(KEY_LAST_SUCCESSFUL_CHECK_AT),
            lastAttemptOutcome = values.enumValue<UpdateAttemptOutcome>(KEY_LAST_ATTEMPT_OUTCOME),
            snapshot = snapshot,
            deferredVersionCode = values.longValue(KEY_DEFERRED_VERSION_CODE),
            deferredUntil = values.longValue(KEY_DEFERRED_UNTIL),
            skippedVersionCode = values.longValue(KEY_SKIPPED_VERSION_CODE),
            playUpdateDownloaded = values[KEY_PLAY_UPDATE_DOWNLOADED]?.toBooleanStrictOrNull() ?: false
        )
    }

    override fun save(state: UpdateStoredState) {
        val values = linkedMapOf<String, String>()
        values.putOptional(KEY_INITIAL_INSTALL_CHANNEL, state.initialInstallChannel?.name)
        values.putOptional(KEY_LAST_AUTO_ATTEMPT_AT, state.lastAutoAttemptAt?.toString())
        values.putOptional(KEY_LAST_ATTEMPT_AT, state.lastAttemptAt?.toString())
        values.putOptional(KEY_LAST_SUCCESSFUL_CHECK_AT, state.lastSuccessfulCheckAt?.toString())
        values.putOptional(KEY_LAST_ATTEMPT_OUTCOME, state.lastAttemptOutcome?.name)
        values[KEY_SNAPSHOT_STATE] = state.snapshot.state.name
        values[KEY_SNAPSHOT_INSTALLED_VERSION_CODE] = state.snapshot.installedVersionCode.toString()
        values.putOptional(KEY_SNAPSHOT_CHANNEL, state.snapshot.channel?.name)
        values.putOptional(
            KEY_SNAPSHOT_AVAILABLE_VERSION_CODE,
            state.snapshot.availableVersionCode?.toString()
        )
        values.putOptional(KEY_SNAPSHOT_AVAILABLE_VERSION_NAME, state.snapshot.availableVersionName)
        values.putOptional(KEY_SNAPSHOT_AVAILABLE_SINCE_AT, state.snapshot.availableSinceAt?.toString())
        values.putOptional(KEY_SNAPSHOT_FIRST_SEEN_AT, state.snapshot.firstSeenAt?.toString())
        values.putOptional(KEY_SNAPSHOT_CHECKED_AT, state.snapshot.checkedAt?.toString())
        values[KEY_SNAPSHOT_FLEXIBLE_ALLOWED] = state.snapshot.flexibleAllowed.toString()
        values.putOptional(KEY_DEFERRED_VERSION_CODE, state.deferredVersionCode?.toString())
        values.putOptional(KEY_DEFERRED_UNTIL, state.deferredUntil?.toString())
        values.putOptional(KEY_SKIPPED_VERSION_CODE, state.skippedVersionCode?.toString())
        values[KEY_PLAY_UPDATE_DOWNLOADED] = state.playUpdateDownloaded.toString()
        backend.replaceAll(values)
    }

    override fun synchronizeInstalledVersion(
        currentVersionCode: Long,
        now: Long
    ): UpdateStoredState {
        val current = load()
        val availableVersion = current.snapshot.availableVersionCode
        val synchronized = if (
            current.snapshot.state == UpdateSnapshotState.UPDATE_AVAILABLE &&
            availableVersion != null &&
            currentVersionCode >= availableVersion
        ) {
            current.copy(
                snapshot = UpdateSnapshot.upToDate(
                    installedVersionCode = currentVersionCode,
                    channel = current.snapshot.channel ?: UpdateChannel.PLAY,
                    checkedAt = now
                ),
                deferredVersionCode = null,
                deferredUntil = null,
                skippedVersionCode = null,
                playUpdateDownloaded = false
            )
        } else {
            current.copy(
                snapshot = current.snapshot.copy(installedVersionCode = currentVersionCode)
            )
        }
        save(synchronized)
        return synchronized
    }

    override fun recordReliableSnapshot(snapshot: UpdateSnapshot): UpdateStoredState {
        val current = load()
        val oldVersion = current.snapshot.availableVersionCode
        val newVersion = snapshot.availableVersionCode
        val sameAvailableVersion = snapshot.state == UpdateSnapshotState.UPDATE_AVAILABLE &&
            current.snapshot.state == UpdateSnapshotState.UPDATE_AVAILABLE &&
            oldVersion != null && oldVersion == newVersion
        val versionChanged = snapshot.state == UpdateSnapshotState.UPDATE_AVAILABLE &&
            newVersion != null && oldVersion != newVersion
        val mergedSnapshot = if (sameAvailableVersion) {
            snapshot.copy(firstSeenAt = current.snapshot.firstSeenAt ?: snapshot.firstSeenAt)
        } else {
            snapshot
        }
        val updated = current.copy(
            snapshot = mergedSnapshot,
            lastSuccessfulCheckAt = snapshot.checkedAt,
            lastAttemptOutcome = UpdateAttemptOutcome.SUCCESS,
            deferredVersionCode = if (versionChanged || !snapshot.hasNewerVersion) {
                null
            } else {
                current.deferredVersionCode
            },
            deferredUntil = if (versionChanged || !snapshot.hasNewerVersion) {
                null
            } else {
                current.deferredUntil
            },
            skippedVersionCode = if (versionChanged || !snapshot.hasNewerVersion) {
                null
            } else {
                current.skippedVersionCode
            }
        )
        save(updated)
        return updated
    }

    private fun decodeSnapshot(values: Map<String, String>): UpdateSnapshot? {
        val state = values.enumValue<UpdateSnapshotState>(KEY_SNAPSHOT_STATE)
            ?: return if (values.isEmpty()) UpdateSnapshot.neverChecked(installedVersionCode) else null
        val installed = values.longValue(KEY_SNAPSHOT_INSTALLED_VERSION_CODE)
            ?: if (state == UpdateSnapshotState.NEVER_CHECKED) installedVersionCode else return null
        val channel = values.enumValue<UpdateChannel>(KEY_SNAPSHOT_CHANNEL)
        val availableVersion = values.longValue(KEY_SNAPSHOT_AVAILABLE_VERSION_CODE)
        val availableName = values[KEY_SNAPSHOT_AVAILABLE_VERSION_NAME]
        val checkedAt = values.longValue(KEY_SNAPSHOT_CHECKED_AT)
        if (state == UpdateSnapshotState.UPDATE_AVAILABLE) {
            if (
                channel == null || availableVersion == null || availableVersion <= installed ||
                availableName.isNullOrBlank() || checkedAt == null
            ) {
                return null
            }
        }
        if (state == UpdateSnapshotState.UP_TO_DATE && (channel == null || checkedAt == null)) {
            return null
        }
        return UpdateSnapshot(
            state = state,
            channel = channel,
            installedVersionCode = installed,
            availableVersionCode = availableVersion,
            availableVersionName = availableName,
            availableSinceAt = values.longValue(KEY_SNAPSHOT_AVAILABLE_SINCE_AT),
            firstSeenAt = values.longValue(KEY_SNAPSHOT_FIRST_SEEN_AT),
            checkedAt = checkedAt,
            flexibleAllowed = values[KEY_SNAPSHOT_FLEXIBLE_ALLOWED]?.toBooleanStrictOrNull() ?: false
        )
    }

    private inline fun <reified T : Enum<T>> Map<String, String>.enumValue(key: String): T? =
        this[key]?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

    private fun Map<String, String>.longValue(key: String): Long? = this[key]?.toLongOrNull()

    private fun MutableMap<String, String>.putOptional(key: String, value: String?) {
        if (value != null) this[key] = value
    }

    companion object {
        private const val KEY_INITIAL_INSTALL_CHANNEL = "initial_install_channel"
        private const val KEY_LAST_AUTO_ATTEMPT_AT = "last_auto_attempt_at"
        private const val KEY_LAST_ATTEMPT_AT = "last_attempt_at"
        private const val KEY_LAST_SUCCESSFUL_CHECK_AT = "last_successful_check_at"
        private const val KEY_LAST_ATTEMPT_OUTCOME = "last_attempt_outcome"
        private const val KEY_SNAPSHOT_STATE = "snapshot_state"
        private const val KEY_SNAPSHOT_CHANNEL = "snapshot_channel"
        private const val KEY_SNAPSHOT_INSTALLED_VERSION_CODE = "snapshot_installed_version_code"
        private const val KEY_SNAPSHOT_AVAILABLE_VERSION_CODE = "snapshot_available_version_code"
        private const val KEY_SNAPSHOT_AVAILABLE_VERSION_NAME = "snapshot_available_version_name"
        private const val KEY_SNAPSHOT_AVAILABLE_SINCE_AT = "snapshot_available_since_at"
        private const val KEY_SNAPSHOT_FIRST_SEEN_AT = "snapshot_first_seen_at"
        private const val KEY_SNAPSHOT_CHECKED_AT = "snapshot_checked_at"
        private const val KEY_SNAPSHOT_FLEXIBLE_ALLOWED = "snapshot_flexible_allowed"
        private const val KEY_DEFERRED_VERSION_CODE = "deferred_version_code"
        private const val KEY_DEFERRED_UNTIL = "deferred_until"
        private const val KEY_SKIPPED_VERSION_CODE = "skipped_version_code"
        private const val KEY_PLAY_UPDATE_DOWNLOADED = "play_update_downloaded"
    }
}

private class SharedPreferencesUpdateKeyValueStore(context: Context) : UpdateKeyValueStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun readAll(): Map<String, String> = preferences.all.mapNotNull { (key, value) ->
        (value as? String)?.let { key to it }
    }.toMap()

    override fun replaceAll(values: Map<String, String>) {
        preferences.edit().clear().apply {
            values.forEach { (key, value) -> putString(key, value) }
        }.apply()
    }

    companion object {
        const val PREFERENCES_NAME = "app_update_state"
    }
}
