package com.golink.busiscoming

import android.app.Notification
import android.app.NotificationManager
import com.golink.busiscoming.service.BusMonitorNotificationChannelManager
import com.golink.busiscoming.service.BusMonitorNotificationContract
import com.golink.busiscoming.service.MonitorNotificationChannelDefinition
import com.golink.busiscoming.service.MonitorNotificationChannelPlatform
import com.golink.busiscoming.service.MonitorNotificationChannelSnapshot
import com.golink.busiscoming.service.MonitorNotificationSeverity
import org.junit.Assert.assertEquals
import org.junit.Test

class BusMonitorNotificationChannelManagerTest {
    @Test
    fun readHealthEnsuresLocalizedChannelsBeforeReadingSystemState() {
        val events = mutableListOf<String>()
        val platform = object : MonitorNotificationChannelPlatform {
            override fun ensureChannels(definitions: List<MonitorNotificationChannelDefinition>) {
                events += "ensure:${definitions.joinToString { it.id }}"
            }

            override fun areNotificationsEnabled(): Boolean {
                events += "enabled"
                return true
            }

            override fun readChannel(channelId: String): MonitorNotificationChannelSnapshot {
                events += "read:$channelId"
                return when (channelId) {
                    BusMonitorNotificationContract.STATUS_CHANNEL_ID -> channel(
                        NotificationManager.IMPORTANCE_LOW
                    )
                    else -> channel(NotificationManager.IMPORTANCE_DEFAULT)
                }
            }
        }
        val manager = BusMonitorNotificationChannelManager(
            sdkInt = 36,
            platform = platform,
            definitions = definitions()
        )

        val health = manager.readHealth()

        assertEquals(MonitorNotificationSeverity.READY, health.severity)
        assertEquals(
            listOf(
                "ensure:${BusMonitorNotificationContract.STATUS_CHANNEL_ID}, ${BusMonitorNotificationContract.ALERT_CHANNEL_ID}",
                "enabled",
                "read:${BusMonitorNotificationContract.STATUS_CHANNEL_ID}",
                "read:${BusMonitorNotificationContract.ALERT_CHANNEL_ID}"
            ),
            events
        )
    }

    @Test
    fun legacyPlatformSkipsChannelCreationAndReturnsUnknown() {
        val events = mutableListOf<String>()
        val platform = object : MonitorNotificationChannelPlatform {
            override fun ensureChannels(definitions: List<MonitorNotificationChannelDefinition>) {
                events += "ensure"
            }

            override fun areNotificationsEnabled(): Boolean {
                events += "enabled"
                return true
            }

            override fun readChannel(channelId: String): MonitorNotificationChannelSnapshot {
                events += "read"
                return MonitorNotificationChannelSnapshot(exists = false)
            }
        }

        val health = BusMonitorNotificationChannelManager(
            sdkInt = 25,
            platform = platform,
            definitions = definitions()
        ).readHealth()

        assertEquals(MonitorNotificationSeverity.UNKNOWN, health.severity)
        assertEquals(listOf("enabled"), events)
    }

    private fun definitions() = listOf(
        MonitorNotificationChannelDefinition(
            id = BusMonitorNotificationContract.STATUS_CHANNEL_ID,
            name = "Status",
            description = "Status description",
            importance = NotificationManager.IMPORTANCE_LOW
        ),
        MonitorNotificationChannelDefinition(
            id = BusMonitorNotificationContract.ALERT_CHANNEL_ID,
            name = "Alert",
            description = "Alert description",
            importance = NotificationManager.IMPORTANCE_DEFAULT
        )
    )

    private fun channel(importance: Int) = MonitorNotificationChannelSnapshot(
        exists = true,
        importance = importance,
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    )
}
