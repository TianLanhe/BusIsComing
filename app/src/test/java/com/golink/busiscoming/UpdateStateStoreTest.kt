package com.golink.busiscoming

import com.golink.busiscoming.data.model.InitialInstallChannel
import com.golink.busiscoming.data.model.UpdateAttemptOutcome
import com.golink.busiscoming.data.model.UpdateChannel
import com.golink.busiscoming.data.model.UpdateSnapshot
import com.golink.busiscoming.data.model.UpdateSnapshotState
import com.golink.busiscoming.data.update.SharedPreferencesUpdateStateStore
import com.golink.busiscoming.data.update.UpdateKeyValueStore
import com.golink.busiscoming.data.update.UpdateStoredState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateStateStoreTest {
    @Test
    fun stateRoundTripsThroughPreferenceEncoding() {
        val backend = MemoryUpdateKeyValueStore()
        val store = SharedPreferencesUpdateStateStore(backend, installedVersionCode = 6L)
        val expected = UpdateStoredState(
            initialInstallChannel = InitialInstallChannel.NON_PLAY,
            lastAutoAttemptAt = 100L,
            lastAttemptAt = 200L,
            lastSuccessfulCheckAt = 190L,
            lastAttemptOutcome = UpdateAttemptOutcome.FAILED,
            snapshot = availableSnapshot(8L, 300L),
            deferredVersionCode = 8L,
            deferredUntil = 400L,
            skippedVersionCode = 7L,
            playUpdateDownloaded = true
        )

        store.save(expected)

        assertEquals(expected, store.load())
    }

    @Test
    fun corruptPreferenceValuesFallBackToSafeDefaults() {
        val backend = MemoryUpdateKeyValueStore(
            mutableMapOf(
                "initial_install_channel" to "SIDEWAYS",
                "snapshot_state" to "UPDATE_AVAILABLE",
                "snapshot_available_version_code" to "not-a-number",
                "last_auto_attempt_at" to "broken"
            )
        )
        val store = SharedPreferencesUpdateStateStore(backend, installedVersionCode = 6L)

        assertEquals(UpdateStoredState.initial(6L), store.load())
    }

    @Test
    fun installedVersionClearsSatisfiedSnapshotAndReminderState() {
        val store = SharedPreferencesUpdateStateStore(
            MemoryUpdateKeyValueStore(),
            installedVersionCode = 6L
        )
        store.save(
            UpdateStoredState(
                snapshot = availableSnapshot(8L, 300L),
                deferredVersionCode = 8L,
                deferredUntil = 400L,
                skippedVersionCode = 8L
            )
        )

        val synchronized = store.synchronizeInstalledVersion(8L, now = 500L)

        assertEquals(UpdateSnapshotState.UP_TO_DATE, synchronized.snapshot.state)
        assertEquals(8L, synchronized.snapshot.installedVersionCode)
        assertNull(synchronized.deferredVersionCode)
        assertNull(synchronized.deferredUntil)
        assertNull(synchronized.skippedVersionCode)
    }

    @Test
    fun higherAvailableVersionResetsOldDeferAndSkipButSameVersionPreservesFirstSeen() {
        val store = SharedPreferencesUpdateStateStore(
            MemoryUpdateKeyValueStore(),
            installedVersionCode = 6L
        )
        store.save(
            UpdateStoredState(
                snapshot = availableSnapshot(8L, 100L),
                deferredVersionCode = 8L,
                deferredUntil = 400L,
                skippedVersionCode = 8L
            )
        )

        val sameVersion = store.recordReliableSnapshot(availableSnapshot(8L, 300L))
        assertEquals(100L, sameVersion.snapshot.firstSeenAt)
        assertEquals(8L, sameVersion.skippedVersionCode)

        val higherVersion = store.recordReliableSnapshot(availableSnapshot(9L, 500L))
        assertEquals(500L, higherVersion.snapshot.firstSeenAt)
        assertNull(higherVersion.deferredVersionCode)
        assertNull(higherVersion.deferredUntil)
        assertNull(higherVersion.skippedVersionCode)
    }

    private fun availableSnapshot(versionCode: Long, firstSeenAt: Long) = UpdateSnapshot(
        state = UpdateSnapshotState.UPDATE_AVAILABLE,
        channel = UpdateChannel.PLAY,
        installedVersionCode = 6L,
        availableVersionCode = versionCode,
        availableVersionName = "1.$versionCode",
        availableSinceAt = firstSeenAt,
        firstSeenAt = firstSeenAt,
        checkedAt = firstSeenAt,
        flexibleAllowed = true
    )
}

private class MemoryUpdateKeyValueStore(
    private var values: MutableMap<String, String> = mutableMapOf()
) : UpdateKeyValueStore {
    override fun readAll(): Map<String, String> = values.toMap()

    override fun replaceAll(values: Map<String, String>) {
        this.values = values.toMutableMap()
    }
}
