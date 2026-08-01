package com.golink.busiscoming.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.golink.busiscoming.R

data class MonitorNotificationChannelDefinition(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int
)

interface MonitorNotificationChannelPlatform {
    fun ensureChannels(definitions: List<MonitorNotificationChannelDefinition>)
    fun areNotificationsEnabled(): Boolean
    fun readChannel(channelId: String): MonitorNotificationChannelSnapshot
}

class BusMonitorNotificationChannelManager(
    private val sdkInt: Int,
    private val platform: MonitorNotificationChannelPlatform,
    private val definitions: List<MonitorNotificationChannelDefinition>
) {
    fun ensureChannels() {
        if (sdkInt >= Build.VERSION_CODES.O) {
            platform.ensureChannels(definitions)
        }
    }

    fun readHealth(): MonitorNotificationHealth {
        ensureChannels()
        val appNotificationsEnabled = platform.areNotificationsEnabled()
        val channelsSupported = sdkInt >= Build.VERSION_CODES.O
        val status = if (channelsSupported) {
            platform.readChannel(BusMonitorNotificationContract.STATUS_CHANNEL_ID)
        } else {
            MonitorNotificationChannelSnapshot(exists = false)
        }
        val alert = if (channelsSupported) {
            platform.readChannel(BusMonitorNotificationContract.ALERT_CHANNEL_ID)
        } else {
            MonitorNotificationChannelSnapshot(exists = false)
        }
        return MonitorNotificationHealthPolicy.evaluate(
            MonitorNotificationSnapshot(
                sdkInt = sdkInt,
                appNotificationsEnabled = appNotificationsEnabled,
                statusChannel = status,
                alertChannel = alert
            )
        )
    }

    companion object {
        fun forContext(context: Context): BusMonitorNotificationChannelManager {
            val definitions = listOf(
                MonitorNotificationChannelDefinition(
                    id = BusMonitorNotificationContract.STATUS_CHANNEL_ID,
                    name = context.getString(R.string.notification_channel_monitor),
                    description = context.getString(
                        R.string.notification_channel_monitor_description
                    ),
                    importance = BusMonitorNotificationContract.STATUS_CHANNEL_IMPORTANCE
                ),
                MonitorNotificationChannelDefinition(
                    id = BusMonitorNotificationContract.ALERT_CHANNEL_ID,
                    name = context.getString(R.string.notification_channel_urgent),
                    description = context.getString(
                        R.string.notification_channel_urgent_description
                    ),
                    importance = BusMonitorNotificationContract.ALERT_CHANNEL_IMPORTANCE
                )
            )
            return BusMonitorNotificationChannelManager(
                sdkInt = Build.VERSION.SDK_INT,
                platform = AndroidMonitorNotificationChannelPlatform(context),
                definitions = definitions
            )
        }
    }
}

private class AndroidMonitorNotificationChannelPlatform(
    private val context: Context
) : MonitorNotificationChannelPlatform {
    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    override fun ensureChannels(definitions: List<MonitorNotificationChannelDefinition>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        definitions.forEach { definition ->
            val channel = NotificationChannel(
                definition.id,
                definition.name,
                definition.importance
            ).apply {
                description = definition.description
                lockscreenVisibility = BusMonitorNotificationContract.LOCKSCREEN_VISIBILITY
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    override fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    override fun readChannel(channelId: String): MonitorNotificationChannelSnapshot {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return MonitorNotificationChannelSnapshot(exists = false)
        }
        val channel = manager.getNotificationChannel(channelId)
            ?: return MonitorNotificationChannelSnapshot(exists = false)
        return MonitorNotificationChannelSnapshot(
            exists = true,
            importance = channel.importance,
            lockscreenVisibility = channel.lockscreenVisibility
        )
    }
}
