package com.golink.busiscoming

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.golink.busiscoming.data.location.*
import com.golink.busiscoming.data.model.InitialInstallChannel
import com.golink.busiscoming.data.model.UpdateFailureKind
import com.golink.busiscoming.data.update.*
import org.junit.Assert.*
import org.junit.Test

class GoogleServiceCompatibilityTest {
    @Test fun `正常 Google 不建立系統來源而未知也不轉後備`() {
        assertEquals("google", GoogleServiceCapability.AVAILABLE.select(
            { "google" }, { error("正常分支不得啟動系統來源") }, { error("不應降級") }))
        assertEquals("system", GoogleServiceCapability.UNAVAILABLE.select(
            { error("不得建立缺失的 SDK") }, { "system" }, { error("應有明確後備") }))
        assertEquals("unavailable", GoogleServiceCapability.UNKNOWN.select(
            { error("未知不可安全建立") }, { error("未知不是缺失") }, { "unavailable" }))
    }

    @Test fun `所有初始渠道只有明確缺失停用才走官網`() {
        for (initial in InitialInstallChannel.entries) {
            assertEquals(UpdateChannelDecision.WEBSITE, UpdateChannelResolver.resolve(PlayStoreAvailability.MISSING, initial, null))
            assertEquals(UpdateChannelDecision.WEBSITE, UpdateChannelResolver.resolve(PlayStoreAvailability.DISABLED, initial, null))
            assertEquals(UpdateChannelDecision.PLAY_UNAVAILABLE, UpdateChannelResolver.resolve(PlayStoreAvailability.UNUSABLE, initial, null))
            assertEquals(UpdateChannelDecision.PLAY, UpdateChannelResolver.resolve(PlayStoreAvailability.AVAILABLE, initial, PlayUpdateResult.NotAvailable))
        }
    }

    @Test fun `缺失商店所有非畫面入口不建立 SDK`() {
        for (availability in listOf(PlayStoreAvailability.MISSING, PlayStoreAvailability.DISABLED, PlayStoreAvailability.UNUSABLE)) {
            var creations = 0
            val source = GuardedPlayUpdateSource({ availability }) { creations++; error("不應建立 Play SDK") }
            var result: PlayUpdateResult? = null
            source.setDownloadedListener { error("缺失服務不應回報下載") }
            source.check { result = it }
            source.refreshInstallStatus()
            var completed = true
            source.completeUpdate { completed = it }
            assertEquals(PlayUpdateResult.Failed(UpdateFailureKind.PLAY_UNAVAILABLE), result)
            assertFalse(completed)
            assertEquals(0, creations)
        }
    }

    @Test fun `正常商店重用來源而停用使舊下載通知失效`() {
        var availability = PlayStoreAvailability.AVAILABLE
        val delegates = mutableListOf<GuardTestPlay>()
        val source = GuardedPlayUpdateSource({ availability }) { GuardTestPlay().also(delegates::add) }
        var downloaded = false
        source.setDownloadedListener { downloaded = it }
        source.refreshInstallStatus()
        val first = delegates.single()
        val oldListener = first.listener!!
        oldListener(true)
        assertTrue(downloaded)
        availability = PlayStoreAvailability.DISABLED
        source.refreshInstallStatus()
        downloaded = false
        oldListener(true)
        assertFalse(downloaded)
        assertNull(first.listener)
        availability = PlayStoreAvailability.AVAILABLE
        source.refreshInstallStatus()
        assertEquals(2, delegates.size)
        delegates.last().listener!!(true)
        assertTrue(downloaded)
    }

    @Test fun `同步 SDK 與探測例外都轉受控結果`() {
        val factoryFailure = GuardedPlayUpdateSource({ PlayStoreAvailability.AVAILABLE }) { throw IllegalStateException() }
        var result: PlayUpdateResult? = null
        factoryFailure.setDownloadedListener {}
        factoryFailure.check { result = it }
        assertEquals(PlayUpdateResult.Failed(UpdateFailureKind.PLAY_UNAVAILABLE), result)
        val probeFailure = GuardedPlayUpdateSource({ throw SecurityException() }) { error("不得呼叫") }
        probeFailure.check { result = it }
        assertEquals(PlayUpdateResult.Failed(UpdateFailureKind.PLAY_UNAVAILABLE), result)
    }
}

private class GuardTestPlay : PlayUpdateSource {
    var listener: ((Boolean) -> Unit)? = null
    override fun setDownloadedListener(listener: ((Boolean) -> Unit)?) { this.listener = listener }
    override fun check(callback: (PlayUpdateResult) -> Unit) = callback(PlayUpdateResult.NotAvailable)
    override fun refreshInstallStatus() = Unit
    override fun completeUpdate(callback: (Boolean) -> Unit) = callback(true)
    override fun startFlexibleUpdate(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) = false
}
