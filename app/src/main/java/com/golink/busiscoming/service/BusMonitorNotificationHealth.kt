package com.golink.busiscoming.service

import android.app.Notification
import android.app.NotificationManager
import android.os.Build

enum class MonitorNotificationSeverity {
    READY,
    WARNING,
    BLOCKING,
    UNKNOWN
}

enum class MonitorNotificationIssue {
    APP_NOTIFICATIONS_DISABLED,
    STATUS_CHANNEL_MISSING,
    STATUS_CHANNEL_DISABLED,
    ALERT_CHANNEL_MISSING,
    ALERT_CHANNEL_DISABLED,
    ALERT_CHANNEL_IMPORTANCE_LOW,
    STATUS_LOCKSCREEN_HIDDEN,
    ALERT_LOCKSCREEN_HIDDEN,
    PLATFORM_CHANNELS_UNAVAILABLE
}

data class MonitorNotificationChannelSnapshot(
    val exists: Boolean,
    val importance: Int? = null,
    val lockscreenVisibility: Int? = null
)

data class MonitorNotificationSnapshot(
    val sdkInt: Int,
    val appNotificationsEnabled: Boolean,
    val statusChannel: MonitorNotificationChannelSnapshot,
    val alertChannel: MonitorNotificationChannelSnapshot
)

data class MonitorNotificationHealth(
    val severity: MonitorNotificationSeverity,
    val issues: List<MonitorNotificationIssue>,
    val recommendedChannelId: String? = null
)

object MonitorNotificationHealthPolicy {
    fun evaluate(snapshot: MonitorNotificationSnapshot): MonitorNotificationHealth {
        if (!snapshot.appNotificationsEnabled) {
            return MonitorNotificationHealth(
                severity = MonitorNotificationSeverity.BLOCKING,
                issues = listOf(MonitorNotificationIssue.APP_NOTIFICATIONS_DISABLED)
            )
        }
        if (snapshot.sdkInt < Build.VERSION_CODES.O) {
            return MonitorNotificationHealth(
                severity = MonitorNotificationSeverity.UNKNOWN,
                issues = listOf(MonitorNotificationIssue.PLATFORM_CHANNELS_UNAVAILABLE)
            )
        }
        if (!snapshot.statusChannel.exists || snapshot.statusChannel.importance == null) {
            return statusBlocking(MonitorNotificationIssue.STATUS_CHANNEL_MISSING)
        }
        if (snapshot.statusChannel.importance == NotificationManager.IMPORTANCE_NONE) {
            return statusBlocking(MonitorNotificationIssue.STATUS_CHANNEL_DISABLED)
        }

        val issues = buildList {
            if (snapshot.statusChannel.lockscreenVisibility == Notification.VISIBILITY_SECRET) {
                add(MonitorNotificationIssue.STATUS_LOCKSCREEN_HIDDEN)
            }
            when {
                !snapshot.alertChannel.exists || snapshot.alertChannel.importance == null ->
                    add(MonitorNotificationIssue.ALERT_CHANNEL_MISSING)
                snapshot.alertChannel.importance == NotificationManager.IMPORTANCE_NONE ->
                    add(MonitorNotificationIssue.ALERT_CHANNEL_DISABLED)
                snapshot.alertChannel.importance < BusMonitorNotificationContract.ALERT_CHANNEL_IMPORTANCE ->
                    add(MonitorNotificationIssue.ALERT_CHANNEL_IMPORTANCE_LOW)
            }
            if (snapshot.alertChannel.lockscreenVisibility == Notification.VISIBILITY_SECRET) {
                add(MonitorNotificationIssue.ALERT_LOCKSCREEN_HIDDEN)
            }
        }
        if (issues.isEmpty()) {
            return MonitorNotificationHealth(
                severity = MonitorNotificationSeverity.READY,
                issues = emptyList()
            )
        }
        val targetChannelId = if (issues.contains(MonitorNotificationIssue.STATUS_LOCKSCREEN_HIDDEN)) {
            BusMonitorNotificationContract.STATUS_CHANNEL_ID
        } else {
            BusMonitorNotificationContract.ALERT_CHANNEL_ID
        }
        return MonitorNotificationHealth(
            severity = MonitorNotificationSeverity.WARNING,
            issues = issues,
            recommendedChannelId = targetChannelId
        )
    }

    private fun statusBlocking(issue: MonitorNotificationIssue): MonitorNotificationHealth {
        return MonitorNotificationHealth(
            severity = MonitorNotificationSeverity.BLOCKING,
            issues = listOf(issue),
            recommendedChannelId = BusMonitorNotificationContract.STATUS_CHANNEL_ID
        )
    }
}
