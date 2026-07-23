package com.golink.busiscoming.data.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.golink.busiscoming.data.model.AppUpdateState
import com.golink.busiscoming.data.model.InitialInstallChannel
import com.golink.busiscoming.data.model.UpdateAttemptOutcome
import com.golink.busiscoming.data.model.UpdateChannel
import com.golink.busiscoming.data.model.UpdateCheckTrigger
import com.golink.busiscoming.data.model.UpdateFailure
import com.golink.busiscoming.data.model.UpdateFailureKind
import com.golink.busiscoming.data.model.UpdateSnapshot
import com.golink.busiscoming.data.model.UpdateSnapshotState
import java.util.concurrent.Executor

class AppUpdateCoordinator(
    private val installedVersionCode: Long,
    private val stateStore: UpdateStateStore,
    private val policy: UpdatePolicy,
    private val playSource: PlayUpdateSource,
    private val websiteSource: WebsiteUpdateSource,
    private val playPackageProbe: PlayPackageProbe,
    private val installSourceReader: InstallSourceReader,
    private val forceWebsiteOnly: Boolean = false,
    private val clock: () -> Long = System::currentTimeMillis,
    private val callbackExecutor: Executor
) {
    private val lock = Any()
    private val observers = linkedSetOf<(AppUpdateState) -> Unit>()
    private var checkInFlight = false
    private var activeTrigger = UpdateCheckTrigger.AUTOMATIC
    private var state: AppUpdateState

    init {
        val stored = stateStore.synchronizeInstalledVersion(installedVersionCode, clock())
        state = stored.toAppState()
        if (!forceWebsiteOnly) {
            playSource.setDownloadedListener(::recordPlayDownloaded)
        }
    }

    fun currentState(): AppUpdateState = synchronized(lock) { state }

    fun observe(observer: (AppUpdateState) -> Unit): AutoCloseable {
        val initial = synchronized(lock) {
            observers += observer
            state
        }
        callbackExecutor.execute {
            val isStillObserved = synchronized(lock) { observer in observers }
            if (isStillObserved) observer(initial)
        }
        return AutoCloseable {
            synchronized(lock) { observers -= observer }
        }
    }

    fun check(trigger: UpdateCheckTrigger): Boolean {
        val now = clock()
        var attached = false
        synchronized(lock) {
            if (checkInFlight) {
                if (trigger == UpdateCheckTrigger.MANUAL) {
                    activeTrigger = UpdateCheckTrigger.MANUAL
                    state = state.copy(lastTrigger = UpdateCheckTrigger.MANUAL)
                }
                attached = true
            } else {
                val stored = stateStore.load()
                if (
                    trigger == UpdateCheckTrigger.AUTOMATIC &&
                    !policy.isAutomaticCheckDue(stored.lastAutoAttemptAt)
                ) {
                    return false
                }
                val initialized = if (forceWebsiteOnly) {
                    stored
                } else {
                    ensureInitialInstallChannel(stored)
                }
                val withAttempt = initialized.copy(
                    lastAutoAttemptAt = if (trigger == UpdateCheckTrigger.AUTOMATIC) {
                        now
                    } else {
                        initialized.lastAutoAttemptAt
                    },
                    lastAttemptAt = now
                )
                stateStore.save(withAttempt)
                activeTrigger = trigger
                checkInFlight = true
                state = withAttempt.toAppState().copy(
                    isChecking = true,
                    lastTrigger = trigger,
                    lastFailure = null,
                    resultGeneration = state.resultGeneration
                )
            }
        }
        publishCurrentState()
        if (attached) return true
        startChannelCheck()
        return true
    }

    fun shouldPrompt(trigger: UpdateCheckTrigger? = null): Boolean {
        val current = currentState()
        val effectiveTrigger = trigger ?: current.lastTrigger ?: UpdateCheckTrigger.AUTOMATIC
        return policy.shouldPrompt(
            trigger = effectiveTrigger,
            snapshot = current.snapshot,
            reminder = UpdateReminderState(
                deferredVersionCode = current.deferredVersionCode,
                deferredUntil = current.deferredUntil,
                skippedVersionCode = current.skippedVersionCode
            )
        )
    }

    fun reloadPersistedState() {
        val stored: UpdateStoredState
        synchronized(lock) {
            if (checkInFlight) return
            stored = stateStore.synchronizeInstalledVersion(installedVersionCode, clock())
            state = stored.toAppState().copy(
                lastTrigger = state.lastTrigger,
                lastFailure = state.lastFailure,
                resultGeneration = state.resultGeneration
            )
        }
        publishCurrentState()
    }

    fun deferCurrentVersion(): Boolean = updateReminder { stored, version ->
        stored.copy(
            deferredVersionCode = version,
            deferredUntil = policy.deferredUntil()
        )
    }

    fun skipCurrentVersion(): Boolean = updateReminder { stored, version ->
        stored.copy(skippedVersionCode = version)
    }

    fun startFlexibleUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean = !forceWebsiteOnly && playSource.startFlexibleUpdate(activity, launcher)

    fun refreshPlayInstallStatus() {
        if (!forceWebsiteOnly) playSource.refreshInstallStatus()
    }

    fun completePlayUpdate(callback: (Boolean) -> Unit) {
        if (forceWebsiteOnly) {
            callback(false)
        } else {
            playSource.completeUpdate(callback)
        }
    }

    private fun startChannelCheck() {
        if (forceWebsiteOnly) {
            checkWebsite(UpdateChannel.WEBSITE)
            return
        }
        val stored = stateStore.load()
        val initialChannel = stored.initialInstallChannel ?: InitialInstallChannel.UNKNOWN_NON_PLAY
        val playAvailable = playPackageProbe.isPlayAvailable()
        if (!playAvailable) {
            when (
                UpdateChannelResolver.resolve(
                    playPackageAvailable = false,
                    initialInstallChannel = initialChannel,
                    playResult = null
                )
            ) {
                UpdateChannelDecision.WEBSITE -> checkWebsite(UpdateChannel.WEBSITE)
                UpdateChannelDecision.PLAY_UNAVAILABLE -> completeFailure(
                    UpdateFailureKind.PLAY_UNAVAILABLE
                )
                else -> completeFailure(UpdateFailureKind.UNKNOWN)
            }
            return
        }
        playSource.check(::handlePlayResult)
    }

    private fun handlePlayResult(result: PlayUpdateResult) {
        when (
            UpdateChannelResolver.resolve(
                playPackageAvailable = true,
                initialInstallChannel = stateStore.load().initialInstallChannel
                    ?: InitialInstallChannel.UNKNOWN_NON_PLAY,
                playResult = result
            )
        ) {
            UpdateChannelDecision.PLAY -> when (result) {
                is PlayUpdateResult.Available -> completePlayAvailable(result)
                PlayUpdateResult.NotAvailable -> completeReliable(
                    UpdateSnapshot.upToDate(
                        installedVersionCode = installedVersionCode,
                        channel = UpdateChannel.PLAY,
                        checkedAt = clock()
                    )
                )
                else -> completeFailure(UpdateFailureKind.PLAY_TEMPORARY)
            }
            UpdateChannelDecision.PLAY_WITH_WEBSITE_METADATA -> checkWebsite(UpdateChannel.PLAY)
            UpdateChannelDecision.PLAY_FAILED -> completeFailure(
                (result as? PlayUpdateResult.Failed)?.kind ?: UpdateFailureKind.PLAY_TEMPORARY
            )
            else -> completeFailure(UpdateFailureKind.UNKNOWN)
        }
    }

    private fun completePlayAvailable(result: PlayUpdateResult.Available) {
        val now = clock()
        val previous = stateStore.load().snapshot
        val firstSeen = if (previous.availableVersionCode == result.availableVersionCode) {
            previous.firstSeenAt ?: now
        } else {
            now
        }
        val availableSince = result.stalenessDays?.coerceAtLeast(0)?.let { days ->
            now - days.toLong() * UpdatePolicy.DAY_MILLIS
        } ?: firstSeen
        completeReliable(
            UpdateSnapshot(
                state = UpdateSnapshotState.UPDATE_AVAILABLE,
                channel = UpdateChannel.PLAY,
                installedVersionCode = installedVersionCode,
                availableVersionCode = result.availableVersionCode,
                availableVersionName = result.availableVersionCode.toString(),
                availableSinceAt = availableSince,
                firstSeenAt = firstSeen,
                checkedAt = now,
                flexibleAllowed = result.flexibleAllowed
            ),
            playDownloaded = result.downloaded
        )
    }

    private fun checkWebsite(resultChannel: UpdateChannel) {
        val checkedAt = clock()
        websiteSource.check(installedVersionCode, checkedAt) { result ->
            when (result) {
                is WebsiteUpdateResult.Available -> completeReliable(
                    result.snapshot.copy(channel = resultChannel)
                )
                is WebsiteUpdateResult.UpToDate -> completeReliable(
                    result.snapshot.copy(channel = resultChannel)
                )
                is WebsiteUpdateResult.Failed -> completeFailure(result.kind)
            }
        }
    }

    private fun completeReliable(snapshot: UpdateSnapshot, playDownloaded: Boolean = false) {
        val stored = stateStore.recordReliableSnapshot(snapshot).copy(
            playUpdateDownloaded = playDownloaded
        )
        stateStore.save(stored)
        synchronized(lock) {
            checkInFlight = false
            state = stored.toAppState().copy(
                lastTrigger = activeTrigger,
                lastFailure = null,
                resultGeneration = state.resultGeneration + 1L
            )
        }
        publishCurrentState()
    }

    private fun completeFailure(kind: UpdateFailureKind) {
        val stored = stateStore.load().copy(lastAttemptOutcome = UpdateAttemptOutcome.FAILED)
        stateStore.save(stored)
        val retained = stored.snapshot.state != UpdateSnapshotState.NEVER_CHECKED
        synchronized(lock) {
            checkInFlight = false
            state = stored.toAppState().copy(
                lastTrigger = activeTrigger,
                lastFailure = UpdateFailure(kind, retainedReliableSnapshot = retained),
                resultGeneration = state.resultGeneration + 1L
            )
        }
        publishCurrentState()
    }

    private fun ensureInitialInstallChannel(stored: UpdateStoredState): UpdateStoredState {
        if (stored.initialInstallChannel != null) return stored
        return stored.copy(
            initialInstallChannel = InitialInstallChannelDetector.detect(installSourceReader)
        ).also(stateStore::save)
    }

    private fun updateReminder(
        transform: (UpdateStoredState, Long) -> UpdateStoredState
    ): Boolean {
        val stored = stateStore.load()
        val version = stored.snapshot.availableVersionCode ?: return false
        if (!stored.snapshot.hasNewerVersion) return false
        val updated = transform(stored, version)
        stateStore.save(updated)
        synchronized(lock) {
            state = updated.toAppState().copy(
                lastTrigger = UpdateCheckTrigger.AUTOMATIC,
                lastFailure = state.lastFailure,
                resultGeneration = state.resultGeneration
            )
        }
        publishCurrentState()
        return true
    }

    private fun recordPlayDownloaded(downloaded: Boolean) {
        val stored = stateStore.load().copy(playUpdateDownloaded = downloaded)
        stateStore.save(stored)
        synchronized(lock) {
            state = stored.toAppState().copy(
                lastTrigger = state.lastTrigger,
                lastFailure = state.lastFailure,
                resultGeneration = state.resultGeneration
            )
        }
        publishCurrentState()
    }

    private fun UpdateStoredState.toAppState(): AppUpdateState = AppUpdateState(
        snapshot = snapshot,
        isChecking = false,
        lastFailure = null,
        playUpdateDownloaded = playUpdateDownloaded,
        deferredVersionCode = deferredVersionCode,
        deferredUntil = deferredUntil,
        skippedVersionCode = skippedVersionCode
    )

    private fun publishCurrentState() {
        val snapshot: AppUpdateState
        val currentObservers: List<(AppUpdateState) -> Unit>
        synchronized(lock) {
            snapshot = state
            currentObservers = observers.toList()
        }
        currentObservers.forEach { observer ->
            callbackExecutor.execute {
                val isStillObserved = synchronized(lock) { observer in observers }
                if (isStillObserved) observer(snapshot)
            }
        }
    }
}
