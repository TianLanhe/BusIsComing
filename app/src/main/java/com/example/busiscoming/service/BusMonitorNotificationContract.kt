package com.example.busiscoming.service

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.busiscoming.data.model.BusMonitorStatus

object BusMonitorNotificationContract {
    const val STATUS_CHANNEL_ID = "bus_monitor_status_v2"
    const val ALERT_CHANNEL_ID = "bus_monitor_alert_v2"
    const val LOCKSCREEN_VISIBILITY = Notification.VISIBILITY_PUBLIC
    const val COMPAT_LOCKSCREEN_VISIBILITY = NotificationCompat.VISIBILITY_PUBLIC
    const val STATUS_CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_LOW
    const val ALERT_CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_DEFAULT

    fun channelIdFor(status: BusMonitorStatus?): String {
        return if (status == BusMonitorStatus.LEAVE_NOW || status == BusMonitorStatus.LATE) {
            ALERT_CHANNEL_ID
        } else {
            STATUS_CHANNEL_ID
        }
    }

    fun priorityFor(status: BusMonitorStatus?): Int {
        return when (status) {
            BusMonitorStatus.LATE -> NotificationCompat.PRIORITY_HIGH
            BusMonitorStatus.LEAVE_NOW -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }
    }

    fun requiresRuntimeNotificationPermission(sdkInt: Int): Boolean {
        return sdkInt >= Build.VERSION_CODES.TIRAMISU
    }
}
