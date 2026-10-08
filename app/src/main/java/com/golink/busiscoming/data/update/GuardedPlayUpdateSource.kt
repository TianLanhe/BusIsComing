package com.golink.busiscoming.data.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.golink.busiscoming.data.model.UpdateFailureKind

/** 所有 Play SDK 入口共用能力邊界；正常來源只建立一次，狀態改變才釋放。 */
class GuardedPlayUpdateSource(
    private val availability: () -> PlayStoreAvailability,
    private val factory: () -> PlayUpdateSource
) : PlayUpdateSource {
    private var source: PlayUpdateSource? = null
    private var generation = 0L
    private var downloadedListener: ((Boolean) -> Unit)? = null

    private fun currentSource(): PlayUpdateSource? {
        val available = runCatching(availability).getOrDefault(PlayStoreAvailability.UNUSABLE)
        if (available != PlayStoreAvailability.AVAILABLE) {
            val previous = source
            source = null
            generation++
            runCatching { previous?.setDownloadedListener(null) }
            return null
        }
        source?.let { return it }
        return try {
            factory().also { created ->
                source = created
                val expected = generation
                created.setDownloadedListener { downloaded ->
                    if (expected == generation && currentSource() === created) {
                        downloadedListener?.invoke(downloaded)
                    }
                }
            }
        } catch (_: RuntimeException) {
            runCatching { source?.setDownloadedListener(null) }
            source = null
            generation++
            null
        }
    }

    override fun check(callback: (PlayUpdateResult) -> Unit) {
        val current = currentSource()
        if (current == null) {
            callback(PlayUpdateResult.Failed(UpdateFailureKind.PLAY_UNAVAILABLE))
            return
        }
        val expected = generation
        try {
            current.check { result ->
                callback(if (expected == generation && currentSource() === current) result
                    else PlayUpdateResult.Failed(UpdateFailureKind.PLAY_UNAVAILABLE))
            }
        } catch (_: RuntimeException) {
            callback(PlayUpdateResult.Failed(UpdateFailureKind.PLAY_TEMPORARY))
        }
    }

    override fun startFlexibleUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean = try {
        currentSource()?.startFlexibleUpdate(activity, launcher) ?: false
    } catch (_: RuntimeException) { false }

    override fun refreshInstallStatus() {
        runCatching { currentSource()?.refreshInstallStatus() }
    }

    override fun completeUpdate(callback: (Boolean) -> Unit) {
        val current = currentSource()
        if (current == null) { callback(false); return }
        val expected = generation
        try {
            current.completeUpdate { success ->
                if (expected == generation && currentSource() === current) callback(success)
            }
        } catch (_: RuntimeException) { callback(false) }
    }

    override fun setDownloadedListener(listener: ((Boolean) -> Unit)?) {
        downloadedListener = listener
        currentSource()
    }
}
