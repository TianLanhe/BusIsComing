package com.example.busiscoming

import androidx.core.app.NotificationCompat
import com.example.busiscoming.data.model.BusMonitorStatus
import com.example.busiscoming.service.BusMonitorNotificationContract
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusMonitorNotificationContractTest {
    private val serviceKt = File("src/main/java/com/example/busiscoming/service/BusMonitorService.kt").readText()
    private val schedulerKt = File("src/main/java/com/example/busiscoming/service/BusMonitorRefreshScheduler.kt").readText()
    private val manifestXml = File("src/main/AndroidManifest.xml").readText()

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

    @Test
    fun monitorNotificationUsesFormatterTitleAndOnlyRefreshStopActions() {
        assertTrue(serviceKt.contains("BusMonitorNotificationFormatter.title"))
        assertFalse(serviceKt.contains("\"打開 App\""))
        assertTrue(serviceKt.contains(".addAction(android.R.drawable.ic_popup_sync, \"刷新\", refreshIntent)"))
        assertTrue(serviceKt.contains(".addAction(android.R.drawable.ic_menu_close_clear_cancel, \"停止\", stopIntent)"))
        assertTrue(serviceKt.contains(".setContentIntent(openIntent)"))
    }

    @Test
    fun monitorNotificationUsesSingleBodyForCollapsedExpandedAndPublicVersions() {
        assertTrue(serviceKt.contains("val notificationBody = BusMonitorNotificationFormatter.bodyText"))
        assertFalse(serviceKt.contains("val notificationText = BusMonitorNotificationFormatter.successText"))
        assertFalse(serviceKt.contains("val notificationBigText = BusMonitorNotificationFormatter.bigText"))
        assertTrue(serviceKt.contains("text = notificationBody"))
        assertTrue(serviceKt.contains("bigText = notificationBody"))
        assertTrue(serviceKt.contains(".setPublicVersion(buildPublicNotification(channelId, title, text, bigText))"))
    }

    @Test
    fun monitorServiceSupportsHighPrioritySchedulingAndAutoStop() {
        assertTrue(manifestXml.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        assertTrue(manifestXml.contains("android.permission.WAKE_LOCK"))
        assertTrue(manifestXml.contains("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))

        assertTrue(serviceKt.contains("ACTION_AUTO_STOP"))
        assertTrue(serviceKt.contains("acquireWakeLock"))
        assertTrue(serviceKt.contains("releaseWakeLock"))
        assertTrue(serviceKt.contains("onTimeout"))
        assertTrue(serviceKt.contains("scheduleAutoStop"))
        assertTrue(serviceKt.contains("fun autoStopIntent"))
    }

    @Test
    fun refreshSchedulerSeparatesExactRefreshAndStopAlarms() {
        assertTrue(schedulerKt.contains("setExactAndAllowWhileIdle"))
        assertTrue(schedulerKt.contains("setAndAllowWhileIdle"))
        assertTrue(schedulerKt.contains("scheduleAutoStop"))
        assertTrue(schedulerKt.contains("cancelRefresh"))
        assertTrue(schedulerKt.contains("cancelAutoStop"))
        assertTrue(schedulerKt.contains("REQUEST_REFRESH"))
        assertTrue(schedulerKt.contains("REQUEST_AUTO_STOP"))
    }

    @Test
    fun monitorSpeechDecisionIsLoggedAndFailuresAreThrottled() {
        assertTrue(serviceKt.contains("Monitor speech decision"))
        assertTrue(serviceKt.contains("voiceEnabled="))
        assertTrue(serviceKt.contains("firstEtaMinutes="))
        assertTrue(serviceKt.contains("lastSpokenStatus="))
        assertTrue(serviceKt.contains("shouldSpeak="))
        assertTrue(serviceKt.contains("Monitor speech immediateResult="))
        assertTrue(serviceKt.contains("speechRetryAfterByStatus"))
        assertTrue(serviceKt.contains("shouldThrottleSpeech(status)"))
    }
}
