package com.golink.busiscoming

import com.golink.busiscoming.data.model.InitialInstallChannel
import com.golink.busiscoming.data.model.UpdateChannel
import com.golink.busiscoming.data.model.UpdateCheckTrigger
import com.golink.busiscoming.data.model.UpdateSnapshot
import com.golink.busiscoming.data.model.UpdateSnapshotState
import com.golink.busiscoming.data.update.UpdatePolicy
import com.golink.busiscoming.data.update.UpdateReminderState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    private val hongKong = TimeZone.getTimeZone("Asia/Hong_Kong")

    @Test
    fun updateModelsExpressAllStableChannelsAndStates() {
        assertTrue(InitialInstallChannel.entries.containsAll(
            listOf(
                InitialInstallChannel.PLAY,
                InitialInstallChannel.NON_PLAY,
                InitialInstallChannel.UNKNOWN_NON_PLAY
            )
        ))
        assertTrue(UpdateChannel.entries.containsAll(
            listOf(UpdateChannel.PLAY, UpdateChannel.WEBSITE, UpdateChannel.PLAY_UNAVAILABLE)
        ))
        assertTrue(UpdateSnapshotState.entries.containsAll(
            listOf(
                UpdateSnapshotState.NEVER_CHECKED,
                UpdateSnapshotState.UP_TO_DATE,
                UpdateSnapshotState.UPDATE_AVAILABLE
            )
        ))
    }

    @Test
    fun automaticCheckIsDueAtTwentyFourHoursButNotAfterClockRollback() {
        val now = 1_000_000_000L
        val policy = UpdatePolicy { now }

        assertTrue(policy.isAutomaticCheckDue(null))
        assertFalse(policy.isAutomaticCheckDue(now - UpdatePolicy.AUTO_CHECK_INTERVAL_MILLIS + 1L))
        assertTrue(policy.isAutomaticCheckDue(now - UpdatePolicy.AUTO_CHECK_INTERVAL_MILLIS))
        assertFalse(policy.isAutomaticCheckDue(now + 1L))
    }

    @Test
    fun websiteAgeUsesCompleteHongKongCalendarDays() {
        val now = hkTime("2026-07-23 00:00:00")
        val policy = UpdatePolicy { now }

        assertFalse(policy.hasReachedReminderAge(hkTime("2026-07-20 23:59:59")))
        assertTrue(policy.hasReachedReminderAge(hkTime("2026-07-20 00:00:00")))
        assertTrue(policy.hasReachedReminderAge(hkTime("2026-07-19 23:59:59")))
    }

    @Test
    fun automaticPromptHonorsAgeDeferAndSkippedVersion() {
        val now = hkTime("2026-07-23 12:00:00")
        val policy = UpdatePolicy { now }
        val snapshot = availableSnapshot(
            versionCode = 8L,
            availableSinceAt = hkTime("2026-07-20 00:00:00")
        )

        assertTrue(policy.shouldPrompt(UpdateCheckTrigger.AUTOMATIC, snapshot, UpdateReminderState()))
        assertFalse(policy.shouldPrompt(
            UpdateCheckTrigger.AUTOMATIC,
            snapshot,
            UpdateReminderState(deferredVersionCode = 8L, deferredUntil = now + 1L)
        ))
        assertFalse(policy.shouldPrompt(
            UpdateCheckTrigger.AUTOMATIC,
            snapshot,
            UpdateReminderState(skippedVersionCode = 8L)
        ))
    }

    @Test
    fun manualPromptBypassesAgeDeferAndSkip() {
        val now = hkTime("2026-07-23 12:00:00")
        val policy = UpdatePolicy { now }
        val snapshot = availableSnapshot(
            versionCode = 8L,
            availableSinceAt = now
        )

        assertTrue(policy.shouldPrompt(
            UpdateCheckTrigger.MANUAL,
            snapshot,
            UpdateReminderState(
                deferredVersionCode = 8L,
                deferredUntil = now + UpdatePolicy.DEFER_INTERVAL_MILLIS,
                skippedVersionCode = 8L
            )
        ))
    }

    private fun availableSnapshot(versionCode: Long, availableSinceAt: Long) = UpdateSnapshot(
        state = UpdateSnapshotState.UPDATE_AVAILABLE,
        channel = UpdateChannel.PLAY,
        installedVersionCode = 6L,
        availableVersionCode = versionCode,
        availableVersionName = "1.2",
        availableSinceAt = availableSinceAt,
        firstSeenAt = availableSinceAt,
        checkedAt = availableSinceAt,
        flexibleAllowed = true
    )

    private fun hkTime(value: String): Long = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.ROOT
    ).apply {
        isLenient = false
        timeZone = hongKong
    }.parse(value)!!.time
}
