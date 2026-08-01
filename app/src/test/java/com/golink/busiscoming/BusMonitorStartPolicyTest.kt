package com.golink.busiscoming

import com.golink.busiscoming.ui.main.BusMonitorStartPolicy
import com.golink.busiscoming.ui.main.MonitorStartAttempt
import com.golink.busiscoming.ui.main.MonitorStartCapabilities
import com.golink.busiscoming.ui.main.MonitorStartStep
import org.junit.Assert.assertEquals
import org.junit.Test

class BusMonitorStartPolicyTest {
    @Test
    fun notificationBlockingRunsBeforeOtherSystemSettings() {
        val capabilities = missingEveryCapability(notificationBlocking = true)

        assertEquals(
            MonitorStartStep.NOTIFICATION_SETTINGS,
            BusMonitorStartPolicy.nextStep(capabilities, MonitorStartAttempt())
        )
        assertEquals(
            MonitorStartStep.BLOCKED,
            BusMonitorStartPolicy.nextStep(
                capabilities,
                MonitorStartAttempt(notificationSettingsAttempted = true)
            )
        )
    }

    @Test
    fun exactAlarmThenBatteryThenServiceAreAttemptedOnce() {
        val capabilities = missingEveryCapability(notificationBlocking = false)

        assertEquals(
            MonitorStartStep.EXACT_ALARM,
            BusMonitorStartPolicy.nextStep(capabilities, MonitorStartAttempt())
        )
        assertEquals(
            MonitorStartStep.BATTERY_OPTIMIZATION,
            BusMonitorStartPolicy.nextStep(
                capabilities,
                MonitorStartAttempt(exactAlarmAttempted = true)
            )
        )
        assertEquals(
            MonitorStartStep.START_SERVICE,
            BusMonitorStartPolicy.nextStep(
                capabilities,
                MonitorStartAttempt(
                    exactAlarmAttempted = true,
                    batteryOptimizationAttempted = true
                )
            )
        )
    }

    @Test
    fun grantedCapabilitiesStartServiceWithoutPrompts() {
        assertEquals(
            MonitorStartStep.START_SERVICE,
            BusMonitorStartPolicy.nextStep(
                MonitorStartCapabilities(
                    notificationBlocking = false,
                    canScheduleExactAlarm = true,
                    ignoringBatteryOptimizations = true
                ),
                MonitorStartAttempt()
            )
        )
    }

    private fun missingEveryCapability(notificationBlocking: Boolean) =
        MonitorStartCapabilities(
            notificationBlocking = notificationBlocking,
            canScheduleExactAlarm = false,
            ignoringBatteryOptimizations = false
        )
}
