package com.example.busiscoming.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.example.busiscoming.data.model.BusMonitorRefreshPolicy

class BusMonitorRefreshScheduler(private val context: Context) {
    private val alarmManager: AlarmManager? =
        context.applicationContext.getSystemService(AlarmManager::class.java)

    fun scheduleNext(delayMillis: Long = BusMonitorRefreshPolicy.REFRESH_INTERVAL_MILLIS) {
        val triggerAtElapsedRealtime = BusMonitorRefreshPolicy.nextTriggerElapsedRealtime(
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            delayMillis = delayMillis
        )
        scheduleAtElapsedRealtime(triggerAtElapsedRealtime, refreshPendingIntent())
    }

    fun scheduleAutoStop(stopAtMillis: Long, nowMillis: Long = System.currentTimeMillis()) {
        val delayMillis = (stopAtMillis - nowMillis).coerceAtLeast(0L)
        val triggerAtElapsedRealtime = SystemClock.elapsedRealtime() + delayMillis
        scheduleAtElapsedRealtime(triggerAtElapsedRealtime, autoStopPendingIntent())
    }

    fun cancelRefresh() {
        alarmManager?.cancel(refreshPendingIntent())
    }

    fun cancelAutoStop() {
        alarmManager?.cancel(autoStopPendingIntent())
    }

    fun cancel() {
        cancelRefresh()
        cancelAutoStop()
    }

    private fun scheduleAtElapsedRealtime(
        triggerAtElapsedRealtime: Long,
        pendingIntent: PendingIntent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldUseExactIdleAlarm()) {
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsedRealtime,
                    pendingIntent
                )
            } else {
                alarmManager?.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsedRealtime,
                    pendingIntent
                )
            }
        } else {
            alarmManager?.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsedRealtime,
                pendingIntent
            )
        }
    }

    private fun shouldUseExactIdleAlarm(): Boolean {
        return BusMonitorSchedulingCapability.shouldUseExactIdleAlarm(
            sdkInt = Build.VERSION.SDK_INT,
            canScheduleExactAlarms = BusMonitorSchedulingCapability.canScheduleExactAlarms(alarmManager)
        )
    }

    private fun refreshPendingIntent(): PendingIntent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                REQUEST_REFRESH,
                BusMonitorService.refreshIntent(context),
                BusMonitorService.pendingIntentFlags()
            )
        } else {
            PendingIntent.getService(
                context,
                REQUEST_REFRESH,
                BusMonitorService.refreshIntent(context),
                BusMonitorService.pendingIntentFlags()
            )
        }
    }

    private fun autoStopPendingIntent(): PendingIntent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                REQUEST_AUTO_STOP,
                BusMonitorService.autoStopIntent(context),
                BusMonitorService.pendingIntentFlags()
            )
        } else {
            PendingIntent.getService(
                context,
                REQUEST_AUTO_STOP,
                BusMonitorService.autoStopIntent(context),
                BusMonitorService.pendingIntentFlags()
            )
        }
    }

    companion object {
        private const val REQUEST_REFRESH = 201
        private const val REQUEST_AUTO_STOP = 202
    }
}
