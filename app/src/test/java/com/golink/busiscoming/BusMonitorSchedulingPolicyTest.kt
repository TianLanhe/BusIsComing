package com.golink.busiscoming

import com.golink.busiscoming.service.BusMonitorSchedulingCapability
import com.golink.busiscoming.service.BusMonitorWakeLockPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusMonitorSchedulingPolicyTest {
    @Test
    fun exactAlarmPolicyRequiresSpecialAccessOnlyOnAndroidTwelveAndAbove() {
        assertFalse(BusMonitorSchedulingCapability.requiresExactAlarmSpecialAccess(30))
        assertTrue(BusMonitorSchedulingCapability.requiresExactAlarmSpecialAccess(31))

        assertTrue(BusMonitorSchedulingCapability.shouldUseExactIdleAlarm(30, canScheduleExactAlarms = false))
        assertTrue(BusMonitorSchedulingCapability.shouldUseExactIdleAlarm(31, canScheduleExactAlarms = true))
        assertFalse(BusMonitorSchedulingCapability.shouldUseExactIdleAlarm(31, canScheduleExactAlarms = false))
    }

    @Test
    fun batteryOptimizationExemptionIsRequestedOnlyWhenSupportedAndMissing() {
        assertFalse(
            BusMonitorSchedulingCapability.shouldRequestBatteryOptimizationExemption(
                sdkInt = 22,
                isIgnoringBatteryOptimizations = false
            )
        )
        assertTrue(
            BusMonitorSchedulingCapability.shouldRequestBatteryOptimizationExemption(
                sdkInt = 23,
                isIgnoringBatteryOptimizations = false
            )
        )
        assertFalse(
            BusMonitorSchedulingCapability.shouldRequestBatteryOptimizationExemption(
                sdkInt = 36,
                isIgnoringBatteryOptimizations = true
            )
        )
        assertEquals(
            "package:com.golink.busiscoming",
            BusMonitorSchedulingCapability.batteryOptimizationPackageUri("com.golink.busiscoming")
        )
    }

    @Test
    fun wakeLockTimeoutIsCappedByStopTargetProtectionWindowAndSessionExpiry() {
        assertEquals(
            240_000L,
            BusMonitorWakeLockPolicy.timeoutMillis(
                nowMillis = 1_000L,
                expiresAtMillis = 601_000L,
                stopAtMillis = 121_000L
            )
        )
        assertEquals(
            60_000L,
            BusMonitorWakeLockPolicy.timeoutMillis(
                nowMillis = 1_000L,
                expiresAtMillis = 30_000L,
                stopAtMillis = null
            )
        )
    }
}
