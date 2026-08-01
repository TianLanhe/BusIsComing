package com.golink.busiscoming

import android.app.Notification
import android.app.NotificationManager
import com.golink.busiscoming.service.BusMonitorNotificationContract
import com.golink.busiscoming.service.MonitorNotificationChannelSnapshot
import com.golink.busiscoming.service.MonitorNotificationHealthPolicy
import com.golink.busiscoming.service.MonitorNotificationIssue
import com.golink.busiscoming.service.MonitorNotificationSeverity
import com.golink.busiscoming.service.MonitorNotificationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusMonitorNotificationHealthPolicyTest {
    @Test
    fun disabledAppNotificationsBlockBeforeChannelSettings() {
        val health = MonitorNotificationHealthPolicy.evaluate(
            healthySnapshot().copy(appNotificationsEnabled = false)
        )

        assertEquals(MonitorNotificationSeverity.BLOCKING, health.severity)
        assertEquals(listOf(MonitorNotificationIssue.APP_NOTIFICATIONS_DISABLED), health.issues)
        assertNull(health.recommendedChannelId)
    }

    @Test
    fun missingOrDisabledStatusChannelBlocksAndTargetsStatusChannel() {
        listOf(
            MonitorNotificationChannelSnapshot(exists = false),
            channel(importance = NotificationManager.IMPORTANCE_NONE)
        ).forEach { statusChannel ->
            val health = MonitorNotificationHealthPolicy.evaluate(
                healthySnapshot().copy(statusChannel = statusChannel)
            )

            assertEquals(MonitorNotificationSeverity.BLOCKING, health.severity)
            assertEquals(BusMonitorNotificationContract.STATUS_CHANNEL_ID, health.recommendedChannelId)
        }
    }

    @Test
    fun alertChannelFailureWarnsWithoutBlockingBasicMonitoring() {
        listOf(
            MonitorNotificationChannelSnapshot(exists = false),
            channel(importance = NotificationManager.IMPORTANCE_NONE),
            channel(importance = NotificationManager.IMPORTANCE_LOW)
        ).forEach { alertChannel ->
            val health = MonitorNotificationHealthPolicy.evaluate(
                healthySnapshot().copy(alertChannel = alertChannel)
            )

            assertEquals(MonitorNotificationSeverity.WARNING, health.severity)
            assertEquals(BusMonitorNotificationContract.ALERT_CHANNEL_ID, health.recommendedChannelId)
        }
    }

    @Test
    fun secretLockscreenVisibilityWarnsAndTargetsAffectedChannel() {
        val health = MonitorNotificationHealthPolicy.evaluate(
            healthySnapshot().copy(
                statusChannel = channel(lockscreenVisibility = Notification.VISIBILITY_SECRET)
            )
        )

        assertEquals(MonitorNotificationSeverity.WARNING, health.severity)
        assertTrue(health.issues.contains(MonitorNotificationIssue.STATUS_LOCKSCREEN_HIDDEN))
        assertEquals(BusMonitorNotificationContract.STATUS_CHANNEL_ID, health.recommendedChannelId)
    }

    @Test
    fun legacyPlatformIsUnknownAndHealthyChannelsAreReady() {
        assertEquals(
            MonitorNotificationSeverity.UNKNOWN,
            MonitorNotificationHealthPolicy.evaluate(healthySnapshot().copy(sdkInt = 25)).severity
        )
        assertEquals(
            MonitorNotificationSeverity.READY,
            MonitorNotificationHealthPolicy.evaluate(healthySnapshot()).severity
        )
    }

    private fun healthySnapshot() = MonitorNotificationSnapshot(
        sdkInt = 36,
        appNotificationsEnabled = true,
        statusChannel = channel(importance = NotificationManager.IMPORTANCE_LOW),
        alertChannel = channel(importance = NotificationManager.IMPORTANCE_DEFAULT)
    )

    private fun channel(
        importance: Int = NotificationManager.IMPORTANCE_LOW,
        lockscreenVisibility: Int = Notification.VISIBILITY_PUBLIC
    ) = MonitorNotificationChannelSnapshot(
        exists = true,
        importance = importance,
        lockscreenVisibility = lockscreenVisibility
    )
}
