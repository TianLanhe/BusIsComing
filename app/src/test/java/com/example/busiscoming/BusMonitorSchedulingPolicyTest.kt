package com.example.busiscoming

import com.example.busiscoming.service.BusMonitorSchedulingCapability
import com.example.busiscoming.service.BusMonitorWakeLockPolicy
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
