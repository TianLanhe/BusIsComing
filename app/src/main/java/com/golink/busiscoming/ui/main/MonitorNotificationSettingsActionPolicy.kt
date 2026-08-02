package com.golink.busiscoming.ui.main

import com.golink.busiscoming.service.MonitorNotificationSeverity

object MonitorNotificationSettingsActionPolicy {
    fun isVisible(severity: MonitorNotificationSeverity): Boolean {
        return severity != MonitorNotificationSeverity.READY
    }
}
