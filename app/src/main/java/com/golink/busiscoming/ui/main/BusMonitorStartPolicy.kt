package com.golink.busiscoming.ui.main

data class MonitorStartCapabilities(
    val notificationBlocking: Boolean,
    val canScheduleExactAlarm: Boolean,
    val ignoringBatteryOptimizations: Boolean
)

data class MonitorStartAttempt(
    val notificationSettingsAttempted: Boolean = false,
    val exactAlarmAttempted: Boolean = false,
    val batteryOptimizationAttempted: Boolean = false
)

enum class MonitorStartStep {
    NOTIFICATION_SETTINGS,
    BLOCKED,
    EXACT_ALARM,
    BATTERY_OPTIMIZATION,
    START_SERVICE
}

object BusMonitorStartPolicy {
    fun nextStep(
        capabilities: MonitorStartCapabilities,
        attempt: MonitorStartAttempt
    ): MonitorStartStep {
        if (capabilities.notificationBlocking) {
            return if (attempt.notificationSettingsAttempted) {
                MonitorStartStep.BLOCKED
            } else {
                MonitorStartStep.NOTIFICATION_SETTINGS
            }
        }
        if (!capabilities.canScheduleExactAlarm && !attempt.exactAlarmAttempted) {
            return MonitorStartStep.EXACT_ALARM
        }
        if (
            !capabilities.ignoringBatteryOptimizations &&
            !attempt.batteryOptimizationAttempted
        ) {
            return MonitorStartStep.BATTERY_OPTIMIZATION
        }
        return MonitorStartStep.START_SERVICE
    }
}
