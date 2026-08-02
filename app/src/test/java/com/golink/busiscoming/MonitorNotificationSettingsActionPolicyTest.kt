package com.golink.busiscoming

import com.golink.busiscoming.service.MonitorNotificationSeverity
import com.golink.busiscoming.ui.main.MonitorNotificationSettingsActionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorNotificationSettingsActionPolicyTest {
    @Test
    fun settingsActionIsHiddenOnlyWhenNotificationHealthIsReady() {
        assertFalse(
            MonitorNotificationSettingsActionPolicy.isVisible(
                MonitorNotificationSeverity.READY
            )
        )
        listOf(
            MonitorNotificationSeverity.WARNING,
            MonitorNotificationSeverity.BLOCKING,
            MonitorNotificationSeverity.UNKNOWN
        ).forEach { severity ->
            assertTrue(
                "$severity should retain the notification settings action",
                MonitorNotificationSettingsActionPolicy.isVisible(severity)
            )
        }
    }
}
