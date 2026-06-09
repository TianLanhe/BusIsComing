package com.example.busiscoming

import androidx.core.app.NotificationCompat
import com.example.busiscoming.data.model.BusMonitorStatus
import com.example.busiscoming.service.BusMonitorNotificationContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusMonitorNotificationContractTest {
    @Test
    fun usesMigratedChannelsAndPublicLockscreenVisibility() {
        assertEquals("bus_monitor_status_v2", BusMonitorNotificationContract.STATUS_CHANNEL_ID)
        assertEquals("bus_monitor_alert_v2", BusMonitorNotificationContract.ALERT_CHANNEL_ID)
        assertEquals(NotificationCompat.VISIBILITY_PUBLIC, BusMonitorNotificationContract.COMPAT_LOCKSCREEN_VISIBILITY)
    }

    @Test
    fun alertChannelIsReservedForUrgentMonitorStates() {
        assertEquals(
            BusMonitorNotificationContract.STATUS_CHANNEL_ID,
            BusMonitorNotificationContract.channelIdFor(BusMonitorStatus.PREPARE)
        )
        assertEquals(
            BusMonitorNotificationContract.ALERT_CHANNEL_ID,
            BusMonitorNotificationContract.channelIdFor(BusMonitorStatus.LEAVE_NOW)
        )
        assertEquals(
            BusMonitorNotificationContract.ALERT_CHANNEL_ID,
            BusMonitorNotificationContract.channelIdFor(BusMonitorStatus.LATE)
        )
        assertEquals(
            NotificationCompat.PRIORITY_HIGH,
            BusMonitorNotificationContract.priorityFor(BusMonitorStatus.LATE)
        )
    }

    @Test
    fun runtimeNotificationPermissionStartsOnAndroidThirteen() {
        assertFalse(BusMonitorNotificationContract.requiresRuntimeNotificationPermission(32))
        assertTrue(BusMonitorNotificationContract.requiresRuntimeNotificationPermission(33))
    }
}
