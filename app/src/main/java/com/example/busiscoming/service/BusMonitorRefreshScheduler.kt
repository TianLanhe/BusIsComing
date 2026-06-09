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
        val pendingIntent = refreshPendingIntent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager?.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsedRealtime,
                pendingIntent
            )
        } else {
            alarmManager?.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsedRealtime,
                pendingIntent
            )
        }
    }

    fun cancel() {
        alarmManager?.cancel(refreshPendingIntent())
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

    companion object {
        private const val REQUEST_REFRESH = 201
    }
}
