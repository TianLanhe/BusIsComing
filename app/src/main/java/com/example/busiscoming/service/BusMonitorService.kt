package com.example.busiscoming.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.example.busiscoming.R
import com.example.busiscoming.data.model.BusMonitorSpeechFormatter
import com.example.busiscoming.data.model.BusMonitorSpeechPolicy
import com.example.busiscoming.data.model.BusMonitorStateEvaluator
import com.example.busiscoming.data.model.BusMonitorStatus
import com.example.busiscoming.data.model.BusMonitorStopPolicy
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.FirstLegEtaQuery
import com.example.busiscoming.data.model.WaitTimeState
import com.example.busiscoming.data.repository.CitybusFirstLegEtaService
import com.example.busiscoming.ui.main.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BusMonitorService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val etaExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val etaService = CitybusFirstLegEtaService()
    private var session: MonitorSession? = null
    private var isRefreshing = false
    private var lastStatus: BusMonitorStatus? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var lastSuccessfulNotificationText: String? = null
    private var consecutiveFailureCount = 0

    private val refreshRunnable = Runnable { refreshEta() }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        textToSpeech = TextToSpeech(applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                textToSpeech?.language = Locale.TRADITIONAL_CHINESE
                pendingSpeech?.let { speak(it) }
                pendingSpeech = null
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                refreshEta()
                return START_STICKY
            }
            ACTION_START -> {
                val nextSession = intent.toMonitorSession() ?: return START_NOT_STICKY
                session = nextSession
                lastStatus = null
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(
                        title = "${nextSession.routeName} 通知欄監控",
                        text = "正在更新候車時間...",
                        priorityStatus = null
                    )
                )
                refreshEta()
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        etaExecutor.shutdownNow()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }

    private fun refreshEta() {
        val currentSession = session ?: return
        if (isRefreshing) return
        isRefreshing = true
        etaExecutor.execute {
            val waitTimeState = runCatching { etaService.resolveWaitTime(currentSession.query) }
                .getOrDefault(WaitTimeState.Unavailable)
            mainHandler.post {
                isRefreshing = false
                updateNotification(waitTimeState, currentSession)
                scheduleNextRefresh()
            }
        }
    }

    private fun updateNotification(waitTimeState: WaitTimeState, currentSession: MonitorSession) {
        val manager = getSystemService(NotificationManager::class.java)
        val available = waitTimeState as? WaitTimeState.Available
        if (available == null || available.arrivals.isEmpty()) {
            consecutiveFailureCount += 1
            val fallbackText = lastSuccessfulNotificationText?.let { "$it · 更新失敗" }
                ?: "暫無 ETA，1 分鐘後重試"
            manager.notify(
                NOTIFICATION_ID,
                buildNotification(
                    title = "${currentSession.routeName} 通知欄監控",
                    text = fallbackText,
                    priorityStatus = null
                )
            )
            if (consecutiveFailureCount >= MAX_CONSECUTIVE_FAILURES) {
                stopMonitoring()
            }
            return
        }

        consecutiveFailureCount = 0
        val firstArrival = available.arrivals.first()
        val status = BusMonitorStateEvaluator.evaluate(
            firstEtaMinutes = firstArrival.minutes,
            walkingMinutes = currentSession.walkingMinutes
        )
        val remainingText = if (firstArrival.minutes <= 0) {
            "即將到站"
        } else {
            "剩餘 ${firstArrival.minutes} 分鐘"
        }
        val nextText = available.nextArrival?.let { next ->
            if (next.minutes <= 0) "下一班即將到站" else "下一班 ${next.minutes} 分鐘"
        }
        val notificationText = listOfNotNull(
            "${status.displayText} · $remainingText",
            nextText,
            "步行 ${currentSession.walkingMinutes} 分鐘"
        ).joinToString(" · ") + " · 更新 ${NOW_TIME_FORMAT.get()!!.format(Date())}"
        lastSuccessfulNotificationText = notificationText

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                title = "${currentSession.routeName} 通知欄監控",
                text = notificationText,
                priorityStatus = status
            )
        )

        if (currentSession.voiceEnabled && BusMonitorSpeechPolicy.shouldSpeak(lastStatus, status)) {
            val phrase = BusMonitorSpeechFormatter.phrase(firstArrival.minutes, status)
            speak(phrase)
        }
        lastStatus = status

        if (shouldAutoStop(available, currentSession)) {
            stopMonitoring()
        }
    }

    private fun shouldAutoStop(waitTimeState: WaitTimeState.Available, currentSession: MonitorSession): Boolean {
        val now = System.currentTimeMillis()
        return BusMonitorStopPolicy.shouldAutoStop(
            nowMillis = now,
            firstEtaMillis = waitTimeState.arrivals.firstOrNull()?.etaMillis,
            secondEtaMillis = waitTimeState.nextArrival?.etaMillis
        )
    }

    private fun scheduleNextRefresh() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MILLIS)
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            pendingSpeech = text
            return
        }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bus-monitor-${System.currentTimeMillis()}")
    }

    private fun stopMonitoring() {
        mainHandler.removeCallbacks(refreshRunnable)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(
        title: String,
        text: String,
        priorityStatus: BusMonitorStatus?
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            pendingIntentFlags()
        )
        val refreshIntent = PendingIntent.getService(
            this,
            REQUEST_REFRESH,
            Intent(this, BusMonitorService::class.java).setAction(ACTION_REFRESH),
            pendingIntentFlags()
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, BusMonitorService::class.java).setAction(ACTION_STOP),
            pendingIntentFlags()
        )
        val channelId = if (priorityStatus == BusMonitorStatus.LEAVE_NOW ||
            priorityStatus == BusMonitorStatus.LATE
        ) {
            CHANNEL_ALERT_ID
        } else {
            CHANNEL_STATUS_ID
        }
        val priority = when (priorityStatus) {
            BusMonitorStatus.LATE -> NotificationCompat.PRIORITY_HIGH
            BusMonitorStatus.LEAVE_NOW -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(priority)
            .addAction(android.R.drawable.ic_menu_view, "打開 App", openIntent)
            .addAction(android.R.drawable.ic_popup_sync, "刷新", refreshIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val statusChannel = NotificationChannel(
            CHANNEL_STATUS_ID,
            "巴士通知欄監控",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "每分鐘更新候車時間和出門狀態"
        }
        val alertChannel = NotificationChannel(
            CHANNEL_ALERT_ID,
            "巴士出門提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "狀態跨過出門門檻時使用"
        }
        getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(statusChannel)
            createNotificationChannel(alertChannel)
        }
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private fun Intent.toMonitorSession(): MonitorSession? {
        return MonitorSession(
            routeName = getStringExtra(EXTRA_ROUTE_NAME) ?: return null,
            walkingMinutes = getIntExtra(EXTRA_WALKING_MINUTES, 1).coerceAtLeast(1),
            voiceEnabled = getBooleanExtra(EXTRA_VOICE_ENABLED, true),
            query = FirstLegEtaQuery(
                company = getStringExtra(EXTRA_COMPANY) ?: return null,
                routeVariant = getStringExtra(EXTRA_ROUTE_VARIANT) ?: return null,
                route = getStringExtra(EXTRA_ROUTE) ?: return null,
                boardingSeq = getIntExtra(EXTRA_BOARDING_SEQ, -1).takeIf { it >= 0 } ?: return null,
                alightingSeq = getIntExtra(EXTRA_ALIGHTING_SEQ, -1).takeIf { it >= 0 } ?: return null,
                bound = getStringExtra(EXTRA_BOUND) ?: return null,
                directionPath = getStringExtra(EXTRA_DIRECTION_PATH) ?: return null,
                rawInfo = getStringExtra(EXTRA_RAW_INFO).orEmpty(),
                lang = getStringExtra(EXTRA_LANG).orEmpty().ifBlank { "0" }
            )
        )
    }

    private data class MonitorSession(
        val routeName: String,
        val walkingMinutes: Int,
        val voiceEnabled: Boolean,
        val query: FirstLegEtaQuery
    )

    companion object {
        private const val CHANNEL_STATUS_ID = "bus_monitor_status"
        private const val CHANNEL_ALERT_ID = "bus_monitor_alert"
        private const val NOTIFICATION_ID = 8201
        private const val REFRESH_INTERVAL_MILLIS = 60_000L
        private const val MAX_CONSECUTIVE_FAILURES = 10
        private const val ACTION_START = "com.example.busiscoming.action.START_BUS_MONITOR"
        private const val ACTION_REFRESH = "com.example.busiscoming.action.REFRESH_BUS_MONITOR"
        private const val ACTION_STOP = "com.example.busiscoming.action.STOP_BUS_MONITOR"
        private const val REQUEST_OPEN = 100
        private const val REQUEST_REFRESH = 101
        private const val REQUEST_STOP = 102

        private const val EXTRA_ROUTE_NAME = "route_name"
        private const val EXTRA_WALKING_MINUTES = "walking_minutes"
        private const val EXTRA_VOICE_ENABLED = "voice_enabled"
        private const val EXTRA_COMPANY = "company"
        private const val EXTRA_ROUTE_VARIANT = "route_variant"
        private const val EXTRA_ROUTE = "route"
        private const val EXTRA_BOARDING_SEQ = "boarding_seq"
        private const val EXTRA_ALIGHTING_SEQ = "alighting_seq"
        private const val EXTRA_BOUND = "bound"
        private const val EXTRA_DIRECTION_PATH = "direction_path"
        private const val EXTRA_RAW_INFO = "raw_info"
        private const val EXTRA_LANG = "lang"

        private val NOW_TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("HH:mm", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
                }
            }
        }

        fun startIntent(
            context: Context,
            route: BusRouteOption,
            walkingMinutes: Int,
            voiceEnabled: Boolean
        ): Intent {
            val query = route.firstLegEtaQuery ?: error("First leg ETA query is required")
            return Intent(context, BusMonitorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ROUTE_NAME, route.routeSegments.firstOrNull() ?: route.routeName)
                putExtra(EXTRA_WALKING_MINUTES, walkingMinutes)
                putExtra(EXTRA_VOICE_ENABLED, voiceEnabled)
                putExtra(EXTRA_COMPANY, query.company)
                putExtra(EXTRA_ROUTE_VARIANT, query.routeVariant)
                putExtra(EXTRA_ROUTE, query.route)
                putExtra(EXTRA_BOARDING_SEQ, query.boardingSeq)
                putExtra(EXTRA_ALIGHTING_SEQ, query.alightingSeq)
                putExtra(EXTRA_BOUND, query.bound)
                putExtra(EXTRA_DIRECTION_PATH, query.directionPath)
                putExtra(EXTRA_RAW_INFO, query.rawInfo)
                putExtra(EXTRA_LANG, query.lang)
            }
        }
    }
}
