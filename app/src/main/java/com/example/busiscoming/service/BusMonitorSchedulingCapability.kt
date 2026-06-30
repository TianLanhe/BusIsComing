package com.example.busiscoming.service

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.busiscoming.data.model.BusMonitorSessionPolicy

object BusMonitorSchedulingCapability {
    fun requiresExactAlarmSpecialAccess(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        return sdkInt >= Build.VERSION_CODES.S
    }

    fun canScheduleExactAlarms(
        alarmManager: AlarmManager?,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (!requiresExactAlarmSpecialAccess(sdkInt)) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }
    }

    fun shouldUseExactIdleAlarm(sdkInt: Int, canScheduleExactAlarms: Boolean): Boolean {
        return !requiresExactAlarmSpecialAccess(sdkInt) || canScheduleExactAlarms
    }

    fun exactAlarmSettingsIntent(context: Context): Intent? {
        if (!requiresExactAlarmSpecialAccess()) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}

object BusMonitorWakeLockPolicy {
    const val STOP_TARGET_PROTECTION_WINDOW_MILLIS = 120_000L
    const val MIN_TIMEOUT_MILLIS = 60_000L

    fun timeoutMillis(
        nowMillis: Long,
        expiresAtMillis: Long,
        stopAtMillis: Long?
    ): Long {
        val sessionRemaining = expiresAtMillis - nowMillis
        val stopRemaining = stopAtMillis?.let {
            it - nowMillis + STOP_TARGET_PROTECTION_WINDOW_MILLIS
        }
        val capped = listOfNotNull(sessionRemaining, stopRemaining)
            .minOrNull()
            ?: BusMonitorSessionPolicy.MAX_SESSION_DURATION_MILLIS
        return capped.coerceAtLeast(MIN_TIMEOUT_MILLIS)
    }
}
