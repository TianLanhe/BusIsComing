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
import com.golink.busiscoming.data.update.AppUpdateCoordinator
import com.golink.busiscoming.data.update.InstallSourceReader
import com.golink.busiscoming.data.update.PlayPackageProbe
import com.golink.busiscoming.data.update.PlayUpdateResult
import com.golink.busiscoming.data.update.PlayUpdateSource
import com.golink.busiscoming.data.update.SharedPreferencesUpdateStateStore
import com.golink.busiscoming.data.update.UpdateKeyValueStore
import com.golink.busiscoming.data.update.UpdatePolicy
import com.golink.busiscoming.data.update.UpdateStoredState
import com.golink.busiscoming.data.update.WebsiteUpdateResult
import com.golink.busiscoming.data.update.WebsiteUpdateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun websiteOnlyAutomaticCheckRunsAgainExactlyAtTwentyFourHours() {
        var now = 1_000_000_000L
        val website = FakeWebsiteUpdateSource(
            WebsiteUpdateResult.Available(availableSnapshot(9L, 1L, UpdateChannel.WEBSITE))
        )
        val coordinator = coordinator(
            now = { now },
            website = website,
            forceWebsiteOnly = true
        )

        assertTrue(coordinator.check(UpdateCheckTrigger.AUTOMATIC))
        assertEquals(1, website.checkCount)

        now += UpdatePolicy.AUTO_CHECK_INTERVAL_MILLIS - 1L
        assertFalse(coordinator.check(UpdateCheckTrigger.AUTOMATIC))
        assertEquals(1, website.checkCount)

        now += 1L
        assertTrue(coordinator.check(UpdateCheckTrigger.AUTOMATIC))
        assertEquals(2, website.checkCount)
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
    fun websiteOnlyPreLaunchSwitchBypassesPlayForPlayInstall() {
        val play = FakePlayUpdateSource(
            immediateResult = availablePlay(versionCode = 10L, stalenessDays = 3)
        )
        val website = FakeWebsiteUpdateSource(
            WebsiteUpdateResult.Available(availableSnapshot(9L, 1L, UpdateChannel.WEBSITE))
        )
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            play = play,
            website = website,
            playAvailable = true,
            initialChannel = InitialInstallChannel.PLAY,
            forceWebsiteOnly = true
        )

        coordinator.check(UpdateCheckTrigger.MANUAL)
        coordinator.refreshPlayInstallStatus()
        var completeResult: Boolean? = null
        coordinator.completePlayUpdate { completeResult = it }

        assertEquals(0, play.checkCount)
        assertEquals(0, play.listenerSetCount)
        assertEquals(0, play.refreshCount)
        assertEquals(0, play.completeCount)
        assertFalse(completeResult ?: true)
        assertEquals(1, website.checkCount)
        assertEquals(UpdateChannel.WEBSITE, coordinator.currentState().snapshot.channel)
        assertEquals(9L, coordinator.currentState().snapshot.availableVersionCode)
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
        forceWebsiteOnly: Boolean = false,
        callbackExecutor: (Runnable) -> Unit = { it.run() }
    ) = AppUpdateCoordinator(
        installedVersionCode = 6L,
        stateStore = store,
        policy = UpdatePolicy(now),
        playSource = play,
        websiteSource = website,
        playPackageProbe = object : PlayPackageProbe {
            override fun isPlayAvailable(): Boolean = playAvailable
        },
        installSourceReader = object : InstallSourceReader {
            override fun installerPackageName(): String? = when (initialChannel) {
                InitialInstallChannel.PLAY -> "com.android.vending"
                InitialInstallChannel.NON_PLAY -> "com.android.packageinstaller"
                InitialInstallChannel.UNKNOWN_NON_PLAY -> null
            }
        },
        forceWebsiteOnly = forceWebsiteOnly,
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
        channel: UpdateChannel = UpdateChannel.PLAY
    ) = UpdateSnapshot(
        state = UpdateSnapshotState.UPDATE_AVAILABLE,
        channel = channel,
        installedVersionCode = 6L,
        availableVersionCode = versionCode,
        availableVersionName = "1.$versionCode",
        availableSinceAt = firstSeenAt,
        firstSeenAt = firstSeenAt,
        checkedAt = firstSeenAt,
        flexibleAllowed = channel == UpdateChannel.PLAY
    )
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
