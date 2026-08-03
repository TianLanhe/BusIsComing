package com.golink.busiscoming

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.golink.busiscoming.data.model.AppUpdateState
import com.golink.busiscoming.data.model.InitialInstallChannel
import com.golink.busiscoming.data.model.UpdateChannel
import com.golink.busiscoming.data.model.UpdateCheckTrigger
import com.golink.busiscoming.data.model.UpdateFailureKind
import com.golink.busiscoming.data.model.UpdateSnapshot
import com.golink.busiscoming.data.model.UpdateSnapshotState
import com.golink.busiscoming.data.update.AppUpdateDiagnosticEvent
import com.golink.busiscoming.data.update.AppUpdateDiagnostics
import com.golink.busiscoming.data.update.AppUpdateCoordinator
import com.golink.busiscoming.data.update.InstallSourceReader
import com.golink.busiscoming.data.update.NoOpAppUpdateDiagnostics
import com.golink.busiscoming.data.update.PlayPackageProbe
import com.golink.busiscoming.data.update.PlayUpdateResult
import com.golink.busiscoming.data.update.PlayUpdateSource
import com.golink.busiscoming.data.update.SharedPreferencesUpdateStateStore
import com.golink.busiscoming.data.update.UpdateKeyValueStore
import com.golink.busiscoming.data.update.UpdatePolicy
import com.golink.busiscoming.data.update.UpdateStoredState
import com.golink.busiscoming.data.update.UpdateChannelDecision
import com.golink.busiscoming.data.update.WebsiteUpdateResult
import com.golink.busiscoming.data.update.WebsiteUpdateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCoordinatorTest {
    @Test
    fun failedAutomaticAttemptIsThrottledButManualCheckBypassesThrottle() {
        var now = 1_000_000_000L
        val play = FakePlayUpdateSource(
            immediateResult = PlayUpdateResult.Failed(UpdateFailureKind.PLAY_TEMPORARY)
        )
        val coordinator = coordinator(now = { now }, play = play)

        assertTrue(coordinator.check(UpdateCheckTrigger.AUTOMATIC))
        assertFalse(coordinator.check(UpdateCheckTrigger.AUTOMATIC))
        assertEquals(1, play.checkCount)
        assertTrue(coordinator.check(UpdateCheckTrigger.MANUAL))
        assertEquals(2, play.checkCount)
        assertEquals(UpdateFailureKind.PLAY_TEMPORARY, coordinator.currentState().lastFailure?.kind)
    }

    @Test
    fun debugBuildManualCheckFailsWithoutCallingAnyUpdateSource() {
        val play = FakePlayUpdateSource()
        val website = FakeWebsiteUpdateSource(
            WebsiteUpdateResult.Failed(UpdateFailureKind.NETWORK)
        )
        val environment = CountingUpdateEnvironment()
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            play = play,
            website = website,
            playCheckSupported = false,
            environment = environment
        )

        assertTrue(coordinator.check(UpdateCheckTrigger.MANUAL))

        assertEquals(
            UpdateFailureKind.PLAY_DEBUG_BUILD_UNSUPPORTED,
            coordinator.currentState().lastFailure?.kind
        )
        assertEquals(0, play.checkCount)
        assertEquals(0, website.checkCount)
        assertEquals(0, environment.playPackageChecks)
        assertEquals(0, environment.installSourceReads)
        assertEquals(
            UpdateSnapshotState.NEVER_CHECKED,
            coordinator.currentState().snapshot.state
        )
    }

    @Test
    fun debugBuildAutomaticFailureStillUsesTwentyFourHourThrottle() {
        var now = 1_000_000_000L
        val coordinator = coordinator(
            now = { now },
            playCheckSupported = false
        )

        assertTrue(coordinator.check(UpdateCheckTrigger.AUTOMATIC))
        assertFalse(coordinator.check(UpdateCheckTrigger.AUTOMATIC))

        now += UpdatePolicy.AUTO_CHECK_INTERVAL_MILLIS
        assertTrue(coordinator.check(UpdateCheckTrigger.AUTOMATIC))
    }

    @Test
    fun overlappingManualCheckAttachesToSingleFlightAndUpgradesResultTrigger() {
        val play = FakePlayUpdateSource()
        val coordinator = coordinator(now = { 1_000_000_000L }, play = play)

        assertTrue(coordinator.check(UpdateCheckTrigger.AUTOMATIC))
        assertTrue(coordinator.check(UpdateCheckTrigger.MANUAL))
        assertEquals(1, play.checkCount)
        play.complete(availablePlay(versionCode = 8L, stalenessDays = 3))

        val state = coordinator.currentState()
        assertFalse(state.isChecking)
        assertEquals(UpdateCheckTrigger.MANUAL, state.lastTrigger)
        assertEquals(8L, state.snapshot.availableVersionCode)
        assertTrue(coordinator.shouldPrompt())
    }

    @Test
    fun playAvailableUsesMatchingWebsiteVersionNameWithoutChangingPlayEligibility() {
        val website = FakeWebsiteUpdateSource(
            WebsiteUpdateResult.Available(
                availableSnapshot(
                    versionCode = 12L,
                    firstSeenAt = 1L,
                    channel = UpdateChannel.WEBSITE,
                    versionName = "1.2"
                )
            )
        )
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            play = FakePlayUpdateSource(
                immediateResult = availablePlay(versionCode = 12L, stalenessDays = 3)
            ),
            website = website
        )

        coordinator.check(UpdateCheckTrigger.MANUAL)

        val snapshot = coordinator.currentState().snapshot
        assertEquals(1, website.checkCount)
        assertEquals(UpdateChannel.PLAY, snapshot.channel)
        assertEquals(12L, snapshot.availableVersionCode)
        assertEquals("1.2", snapshot.availableVersionName)
        assertTrue(snapshot.flexibleAllowed)
    }

    @Test
    fun playAvailableNeverUsesVersionCodeOrMismatchedWebsiteNameAsVersionName() {
        val website = FakeWebsiteUpdateSource(
            WebsiteUpdateResult.Available(
                availableSnapshot(
                    versionCode = 11L,
                    firstSeenAt = 1L,
                    channel = UpdateChannel.WEBSITE,
                    versionName = "1.1"
                )
            )
        )
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            play = FakePlayUpdateSource(
                immediateResult = availablePlay(versionCode = 12L, stalenessDays = 3)
            ),
            website = website
        )

        coordinator.check(UpdateCheckTrigger.MANUAL)

        val snapshot = coordinator.currentState().snapshot
        assertEquals(1, website.checkCount)
        assertEquals(12L, snapshot.availableVersionCode)
        assertNull(snapshot.availableVersionName)
        assertTrue(snapshot.hasNewerVersion)
    }

    @Test
    fun playAvailableRemainsReliableWhenVersionNameLookupFails() {
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            play = FakePlayUpdateSource(
                immediateResult = availablePlay(versionCode = 12L, stalenessDays = 3)
            ),
            website = FakeWebsiteUpdateSource(
                WebsiteUpdateResult.Failed(UpdateFailureKind.NETWORK)
            )
        )

        coordinator.check(UpdateCheckTrigger.MANUAL)

        val state = coordinator.currentState()
        assertEquals(UpdateChannel.PLAY, state.snapshot.channel)
        assertEquals(12L, state.snapshot.availableVersionCode)
        assertNull(state.snapshot.availableVersionName)
        assertNull(state.lastFailure)
    }

    @Test
    fun failedCheckRetainsReliableUpdateSnapshotAndRedDotState() {
        val store = stateStore().apply {
            save(
                UpdateStoredState(
                    initialInstallChannel = InitialInstallChannel.PLAY,
                    snapshot = availableSnapshot(8L, 1L)
                )
            )
        }
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            play = FakePlayUpdateSource(
                immediateResult = PlayUpdateResult.Failed(UpdateFailureKind.PLAY_TEMPORARY)
            ),
            store = store
        )

        coordinator.check(UpdateCheckTrigger.MANUAL)

        val state = coordinator.currentState()
        assertEquals(8L, state.snapshot.availableVersionCode)
        assertTrue(state.snapshot.hasNewerVersion)
        assertTrue(state.lastFailure?.retainedReliableSnapshot == true)
    }

    @Test
    fun nonPlayInstallWithoutPlayUsesWebsite() {
        val website = FakeWebsiteUpdateSource(
            WebsiteUpdateResult.Available(availableSnapshot(9L, 1L, UpdateChannel.WEBSITE))
        )
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            playAvailable = false,
            initialChannel = InitialInstallChannel.NON_PLAY,
            website = website
        )

        coordinator.check(UpdateCheckTrigger.MANUAL)

        assertEquals(1, website.checkCount)
        assertEquals(UpdateChannel.WEBSITE, coordinator.currentState().snapshot.channel)
    }

    @Test
    fun playInstallWithoutPlayDoesNotFallBackToWebsite() {
        val website = FakeWebsiteUpdateSource(
            WebsiteUpdateResult.Available(availableSnapshot(9L, 1L, UpdateChannel.WEBSITE))
        )
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            playAvailable = false,
            initialChannel = InitialInstallChannel.PLAY,
            website = website
        )

        coordinator.check(UpdateCheckTrigger.MANUAL)

        assertEquals(0, website.checkCount)
        assertEquals(UpdateFailureKind.PLAY_UNAVAILABLE, coordinator.currentState().lastFailure?.kind)
    }

    @Test
    fun appNotOwnedUsesWebsiteVersionButKeepsPlayAsActionChannel() {
        val website = FakeWebsiteUpdateSource(
            WebsiteUpdateResult.Available(availableSnapshot(9L, 1L, UpdateChannel.WEBSITE))
        )
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            play = FakePlayUpdateSource(immediateResult = PlayUpdateResult.AppNotOwned),
            website = website
        )

        coordinator.check(UpdateCheckTrigger.MANUAL)

        assertEquals(1, website.checkCount)
        assertEquals(UpdateChannel.PLAY, coordinator.currentState().snapshot.channel)
        assertEquals(9L, coordinator.currentState().snapshot.availableVersionCode)
        assertFalse(coordinator.currentState().snapshot.flexibleAllowed)
    }

    @Test
    fun appNotOwnedWithWebsiteUpToDateIsUnverifiableNotUpToDate() {
        val website = FakeWebsiteUpdateSource(
            WebsiteUpdateResult.UpToDate(
                UpdateSnapshot.upToDate(
                    installedVersionCode = 6L,
                    channel = UpdateChannel.WEBSITE,
                    checkedAt = 1L
                )
            )
        )
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            play = FakePlayUpdateSource(immediateResult = PlayUpdateResult.AppNotOwned),
            website = website
        )

        coordinator.check(UpdateCheckTrigger.MANUAL)

        assertEquals(
            UpdateFailureKind.PLAY_APP_NOT_OWNED,
            coordinator.currentState().lastFailure?.kind
        )
        assertEquals(
            UpdateSnapshotState.NEVER_CHECKED,
            coordinator.currentState().snapshot.state
        )
    }

    @Test
    fun appNotOwnedKeepsRootFailureWhenWebsiteFails() {
        listOf(UpdateFailureKind.NETWORK, UpdateFailureKind.INVALID_METADATA).forEach { kind ->
            val coordinator = coordinator(
                now = { 1_000_000_000L },
                play = FakePlayUpdateSource(immediateResult = PlayUpdateResult.AppNotOwned),
                website = FakeWebsiteUpdateSource(WebsiteUpdateResult.Failed(kind))
            )

            coordinator.check(UpdateCheckTrigger.MANUAL)

            assertEquals(
                UpdateFailureKind.PLAY_APP_NOT_OWNED,
                coordinator.currentState().lastFailure?.kind
            )
        }
    }

    @Test
    fun appNotOwnedFailureRetainsReliableUpdateSnapshot() {
        val store = stateStore().apply {
            save(
                UpdateStoredState(
                    initialInstallChannel = InitialInstallChannel.PLAY,
                    snapshot = availableSnapshot(8L, 1L)
                )
            )
        }
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            play = FakePlayUpdateSource(immediateResult = PlayUpdateResult.AppNotOwned),
            website = FakeWebsiteUpdateSource(
                WebsiteUpdateResult.Failed(UpdateFailureKind.NETWORK)
            ),
            store = store
        )

        coordinator.check(UpdateCheckTrigger.MANUAL)

        assertEquals(8L, coordinator.currentState().snapshot.availableVersionCode)
        assertTrue(coordinator.currentState().snapshot.hasNewerVersion)
        assertTrue(coordinator.currentState().lastFailure?.retainedReliableSnapshot == true)
    }

    @Test
    fun coordinatorRecordsChannelDecisionAndCompletedFailure() {
        val events = mutableListOf<AppUpdateDiagnosticEvent>()
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            play = FakePlayUpdateSource(immediateResult = PlayUpdateResult.AppNotOwned),
            website = FakeWebsiteUpdateSource(
                WebsiteUpdateResult.Failed(UpdateFailureKind.NETWORK)
            ),
            diagnostics = AppUpdateDiagnostics(events::add)
        )

        coordinator.check(UpdateCheckTrigger.MANUAL)

        assertTrue(
            AppUpdateDiagnosticEvent.ChannelDecision(
                InitialInstallChannel.PLAY,
                UpdateChannelDecision.PLAY_WITH_WEBSITE_METADATA
            ) in events
        )
        assertTrue(
            AppUpdateDiagnosticEvent.CompletedFailure(
                UpdateFailureKind.PLAY_APP_NOT_OWNED
            ) in events
        )
    }

    @Test
    fun listenersReceiveCheckingAndReliableResultGenerations() {
        val play = FakePlayUpdateSource()
        val coordinator = coordinator(now = { 1_000_000_000L }, play = play)
        val states = mutableListOf<Boolean>()
        val subscription = coordinator.observe { states += it.isChecking }

        coordinator.check(UpdateCheckTrigger.MANUAL)
        play.complete(PlayUpdateResult.NotAvailable)
        subscription.close()

        assertEquals(listOf(false, true, false), states)
        assertEquals(UpdateSnapshotState.UP_TO_DATE, coordinator.currentState().snapshot.state)
    }

    @Test
    fun closedObserverDoesNotReceiveAlreadyQueuedCallbacks() {
        val queued = mutableListOf<Runnable>()
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            callbackExecutor = { queued += it }
        )
        val states = mutableListOf<AppUpdateState>()

        val subscription = coordinator.observe(states::add)
        subscription.close()
        queued.forEach(Runnable::run)

        assertTrue(states.isEmpty())
    }

    @Test
    fun deferringManualResultRestoresAutomaticSuppression() {
        val now = 1_000_000_000L
        val coordinator = coordinator(
            now = { now },
            play = FakePlayUpdateSource(
                immediateResult = availablePlay(versionCode = 8L, stalenessDays = 3)
            )
        )

        coordinator.check(UpdateCheckTrigger.MANUAL)
        assertTrue(coordinator.shouldPrompt())
        coordinator.deferCurrentVersion()

        assertFalse(coordinator.shouldPrompt())
    }

    @Test
    fun resumedHostReloadsPersistedSnapshotWithoutStartingAnotherCheck() {
        val store = stateStore(InitialInstallChannel.NON_PLAY)
        val play = FakePlayUpdateSource()
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            play = play,
            store = store
        )
        store.save(
            store.load().copy(
                snapshot = availableSnapshot(9L, 1L, UpdateChannel.WEBSITE),
                deferredVersionCode = 9L,
                deferredUntil = 2_000_000_000L
            )
        )

        coordinator.reloadPersistedState()

        assertEquals(9L, coordinator.currentState().snapshot.availableVersionCode)
        assertEquals(9L, coordinator.currentState().deferredVersionCode)
        assertEquals(0, play.checkCount)
    }

    private fun coordinator(
        now: () -> Long,
        play: FakePlayUpdateSource = FakePlayUpdateSource(),
        website: FakeWebsiteUpdateSource = FakeWebsiteUpdateSource(
            WebsiteUpdateResult.Failed(UpdateFailureKind.NETWORK)
        ),
        playAvailable: Boolean = true,
        initialChannel: InitialInstallChannel = InitialInstallChannel.PLAY,
        store: SharedPreferencesUpdateStateStore = stateStore(initialChannel),
        playCheckSupported: Boolean = true,
        environment: CountingUpdateEnvironment? = null,
        diagnostics: AppUpdateDiagnostics = NoOpAppUpdateDiagnostics,
        callbackExecutor: (Runnable) -> Unit = { it.run() }
    ) = AppUpdateCoordinator(
        installedVersionCode = 6L,
        stateStore = store,
        policy = UpdatePolicy(now),
        playSource = play,
        websiteSource = website,
        playPackageProbe = environment ?: object : PlayPackageProbe {
            override fun isPlayAvailable(): Boolean = playAvailable
        },
        installSourceReader = environment ?: object : InstallSourceReader {
            override fun installerPackageName(): String? = when (initialChannel) {
                InitialInstallChannel.PLAY -> "com.android.vending"
                InitialInstallChannel.NON_PLAY -> "com.android.packageinstaller"
                InitialInstallChannel.UNKNOWN_NON_PLAY -> null
            }
        },
        playCheckSupported = playCheckSupported,
        diagnostics = diagnostics,
        clock = now,
        callbackExecutor = callbackExecutor
    )

    private fun stateStore(
        initialChannel: InitialInstallChannel? = null
    ): SharedPreferencesUpdateStateStore = SharedPreferencesUpdateStateStore(
        TestUpdateKeyValueStore(),
        installedVersionCode = 6L
    ).apply {
        if (initialChannel != null) {
            save(UpdateStoredState.initial(6L).copy(initialInstallChannel = initialChannel))
        }
    }

    private fun availablePlay(versionCode: Long, stalenessDays: Int?) =
        PlayUpdateResult.Available(
            availableVersionCode = versionCode,
            stalenessDays = stalenessDays,
            flexibleAllowed = true,
            downloaded = false
        )

    private fun availableSnapshot(
        versionCode: Long,
        firstSeenAt: Long,
        channel: UpdateChannel = UpdateChannel.PLAY,
        versionName: String = "1.$versionCode"
    ) = UpdateSnapshot(
        state = UpdateSnapshotState.UPDATE_AVAILABLE,
        channel = channel,
        installedVersionCode = 6L,
        availableVersionCode = versionCode,
        availableVersionName = versionName,
        availableSinceAt = firstSeenAt,
        firstSeenAt = firstSeenAt,
        checkedAt = firstSeenAt,
        flexibleAllowed = channel == UpdateChannel.PLAY
    )
}

private class CountingUpdateEnvironment : PlayPackageProbe, InstallSourceReader {
    var playPackageChecks = 0
    var installSourceReads = 0

    override fun isPlayAvailable(): Boolean {
        playPackageChecks += 1
        return true
    }

    override fun installerPackageName(): String? {
        installSourceReads += 1
        return "com.android.vending"
    }
}

private class FakePlayUpdateSource(
    private val immediateResult: PlayUpdateResult? = null
) : PlayUpdateSource {
    var checkCount = 0
    var refreshCount = 0
    var completeCount = 0
    var listenerSetCount = 0
    private var pending: ((PlayUpdateResult) -> Unit)? = null

    override fun check(callback: (PlayUpdateResult) -> Unit) {
        checkCount += 1
        if (immediateResult != null) callback(immediateResult) else pending = callback
    }

    fun complete(result: PlayUpdateResult) {
        pending?.invoke(result)
        pending = null
    }

    override fun startFlexibleUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean = false

    override fun refreshInstallStatus() {
        refreshCount += 1
    }

    override fun completeUpdate(callback: (Boolean) -> Unit) {
        completeCount += 1
        callback(false)
    }

    override fun setDownloadedListener(listener: ((Boolean) -> Unit)?) {
        listenerSetCount += 1
    }
}

private class FakeWebsiteUpdateSource(
    private val result: WebsiteUpdateResult
) : WebsiteUpdateSource {
    var checkCount = 0

    override fun check(
        installedVersionCode: Long,
        checkedAt: Long,
        callback: (WebsiteUpdateResult) -> Unit
    ) {
        checkCount += 1
        callback(result)
    }
}

private class TestUpdateKeyValueStore : UpdateKeyValueStore {
    private var values = emptyMap<String, String>()
    override fun readAll(): Map<String, String> = values
    override fun replaceAll(values: Map<String, String>) {
        this.values = values.toMap()
    }
}
