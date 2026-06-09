package com.example.busiscoming.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.busiscoming.R
import com.example.busiscoming.data.model.BusMonitorNotificationFormatter
import com.example.busiscoming.data.model.BusMonitorRefreshPolicy
import com.example.busiscoming.data.model.BusMonitorSessionPolicy
import com.example.busiscoming.data.model.BusMonitorSessionSnapshot
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
    private lateinit var sessionStore: BusMonitorSessionStore
    private lateinit var refreshScheduler: BusMonitorRefreshScheduler
    private lateinit var speechController: BusMonitorSpeechController
    private var session: BusMonitorSessionSnapshot? = null
    private var isRefreshing = false

    private val refreshRunnable = Runnable { refreshEta() }

    override fun onCreate() {
        super.onCreate()
        sessionStore = BusMonitorSessionStore(this)
        refreshScheduler = BusMonitorRefreshScheduler(this)
        speechController = BusMonitorSpeechController(applicationContext)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring(clearSession = true)
                START_NOT_STICKY
            }
            ACTION_START -> startNewSession(intent)
            ACTION_REFRESH -> refreshExistingSession()
            else -> refreshExistingSession()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (sessionStore.load() != null) {
            refreshScheduler.scheduleNext()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        etaExecutor.shutdownNow()
        speechController.release()
        super.onDestroy()
    }

    private fun startNewSession(intent: Intent): Int {
        if (!canPostNotifications()) {
            sessionStore.clear()
            stopSelf()
            return START_NOT_STICKY
        }
        speechController.release()
        speechController = BusMonitorSpeechController(applicationContext)
        val nextSession = intent.toMonitorSessionSnapshot(nowMillis = System.currentTimeMillis())
            ?: return START_NOT_STICKY
        session = nextSession
        sessionStore.save(nextSession)
        startForegroundFor(
            snapshot = nextSession,
            text = "正在取得候車時間...",
            priorityStatus = null
        )
        refreshEta()
        return START_STICKY
    }

    private fun refreshExistingSession(): Int {
        if (!canPostNotifications()) {
            stopMonitoring(clearSession = true)
            return START_NOT_STICKY
        }
        val currentSession = restoreActiveSessionOrStop() ?: return START_NOT_STICKY
        startForegroundFor(
            snapshot = currentSession,
            text = currentSession.lastSuccessfulNotificationText ?: "正在取得候車時間...",
            priorityStatus = currentSession.lastStatus
        )
        refreshEta()
        return START_STICKY
    }

    private fun restoreActiveSessionOrStop(): BusMonitorSessionSnapshot? {
        val currentSession = session ?: sessionStore.load()
        if (currentSession == null) {
            stopMonitoring(clearSession = true)
            return null
        }
        if (BusMonitorSessionPolicy.shouldClearOnRestore(System.currentTimeMillis(), currentSession)) {
            stopMonitoring(clearSession = true)
            return null
        }
        session = currentSession
        return currentSession
    }

    private fun refreshEta() {
        val currentSession = restoreActiveSessionOrStop() ?: return
        if (isRefreshing) return
        isRefreshing = true
        etaExecutor.execute {
            val waitTimeState = runCatching { etaService.resolveWaitTime(currentSession.query) }
                .getOrDefault(WaitTimeState.Unavailable)
            mainHandler.post {
                isRefreshing = false
                val shouldContinue = updateNotification(waitTimeState, currentSession)
                if (shouldContinue) {
                    scheduleNextRefresh()
                }
            }
        }
    }

    private fun updateNotification(
        waitTimeState: WaitTimeState,
        currentSession: BusMonitorSessionSnapshot
    ): Boolean {
        val activeSession = session ?: currentSession
        val manager = getSystemService(NotificationManager::class.java)
        val available = waitTimeState as? WaitTimeState.Available
        if (available == null || available.arrivals.isEmpty()) {
            return handleFailedRefresh(manager, activeSession)
        }

        val firstArrival = available.arrivals.first()
        val status = BusMonitorStateEvaluator.evaluate(
            firstEtaMinutes = firstArrival.minutes,
            walkingMinutes = activeSession.walkingMinutes
        )
        val updatedAtText = NOW_TIME_FORMAT.get()!!.format(Date())
        val notificationText = BusMonitorNotificationFormatter.successText(
            status = status,
            firstEtaMinutes = firstArrival.minutes,
            nextEtaMinutes = available.nextArrival?.minutes,
            walkingMinutes = activeSession.walkingMinutes,
            updatedAtText = updatedAtText
        )
        var lastSpokenStatus = activeSession.lastSpokenStatus
        if (activeSession.voiceEnabled && BusMonitorSpeechPolicy.shouldSpeak(lastSpokenStatus, status)) {
            val phrase = BusMonitorSpeechFormatter.phrase(firstArrival.minutes, status)
            if (speechController.speak(phrase) != BusMonitorSpeechResult.UNAVAILABLE) {
                lastSpokenStatus = status
            }
        }

        val updatedSession = BusMonitorSessionPolicy.recordSuccessfulRefresh(
            snapshot = activeSession,
            nowMillis = System.currentTimeMillis(),
            status = status,
            lastSpokenStatus = lastSpokenStatus,
            notificationText = notificationText,
            firstEtaMillis = firstArrival.etaMillis,
            secondEtaMillis = available.nextArrival?.etaMillis
        )
        session = updatedSession
        sessionStore.save(updatedSession)
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                title = "${updatedSession.routeName} 通知欄監控",
                text = notificationText,
                priorityStatus = status
            )
        )

        if (shouldAutoStop(updatedSession)) {
            stopMonitoring(clearSession = true)
            return false
        }
        return true
    }

    private fun handleFailedRefresh(
        manager: NotificationManager,
        activeSession: BusMonitorSessionSnapshot
    ): Boolean {
        val failedSession = BusMonitorSessionPolicy.recordFailure(activeSession)
        session = failedSession
        sessionStore.save(failedSession)
        val fallbackText = BusMonitorNotificationFormatter.failureText(
            lastSuccessfulNotificationText = failedSession.lastSuccessfulNotificationText,
            failureCount = failedSession.consecutiveFailureCount
        )
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                title = "${failedSession.routeName} 通知欄監控",
                text = fallbackText,
                priorityStatus = failedSession.lastStatus
            )
        )
        if (BusMonitorRefreshPolicy.shouldStopAfterFailureCount(failedSession.consecutiveFailureCount)) {
            stopMonitoring(clearSession = true)
            return false
        }
        return true
    }

    private fun shouldAutoStop(currentSession: BusMonitorSessionSnapshot): Boolean {
        return BusMonitorStopPolicy.shouldAutoStop(
            nowMillis = System.currentTimeMillis(),
            firstEtaMillis = currentSession.firstEtaMillis,
            secondEtaMillis = currentSession.secondEtaMillis
        )
    }

    private fun scheduleNextRefresh() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, BusMonitorRefreshPolicy.REFRESH_INTERVAL_MILLIS)
        refreshScheduler.scheduleNext()
    }

    private fun stopMonitoring(clearSession: Boolean) {
        mainHandler.removeCallbacks(refreshRunnable)
        refreshScheduler.cancel()
        if (clearSession) {
            sessionStore.clear()
        }
        session = null
        speechController.release()
        stopForegroundCompat()
        stopSelf()
    }

    private fun startForegroundFor(
        snapshot: BusMonitorSessionSnapshot,
        text: String,
        priorityStatus: BusMonitorStatus?
    ) {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                title = "${snapshot.routeName} 通知欄監控",
                text = text,
                priorityStatus = priorityStatus
            )
        )
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
        val refreshIntent = servicePendingIntent(REQUEST_REFRESH, ACTION_REFRESH)
        val stopIntent = servicePendingIntent(REQUEST_STOP, ACTION_STOP)
        val channelId = BusMonitorNotificationContract.channelIdFor(priorityStatus)
        val priority = BusMonitorNotificationContract.priorityFor(priorityStatus)
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(BusMonitorNotificationContract.COMPAT_LOCKSCREEN_VISIBILITY)
            .setPublicVersion(buildPublicNotification(channelId, title, text))
            .setPriority(priority)
            .addAction(android.R.drawable.ic_menu_view, "打開 App", openIntent)
            .addAction(android.R.drawable.ic_popup_sync, "刷新", refreshIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent)
            .build()
    }

    private fun buildPublicNotification(channelId: String, title: String, text: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(BusMonitorNotificationContract.COMPAT_LOCKSCREEN_VISIBILITY)
            .build()
    }

    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, BusMonitorService::class.java).setAction(action)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, requestCode, intent, pendingIntentFlags())
        } else {
            PendingIntent.getService(this, requestCode, intent, pendingIntentFlags())
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val statusChannel = NotificationChannel(
            BusMonitorNotificationContract.STATUS_CHANNEL_ID,
            "巴士通知欄監控",
            BusMonitorNotificationContract.STATUS_CHANNEL_IMPORTANCE
        ).apply {
            description = "每分鐘嘗試更新候車時間和出門狀態"
            lockscreenVisibility = BusMonitorNotificationContract.LOCKSCREEN_VISIBILITY
            enableVibration(false)
            setSound(null, null)
            setShowBadge(false)
        }
        val alertChannel = NotificationChannel(
            BusMonitorNotificationContract.ALERT_CHANNEL_ID,
            "巴士出門提醒",
            BusMonitorNotificationContract.ALERT_CHANNEL_IMPORTANCE
        ).apply {
            description = "立即出門或快遲到時提高提醒權重"
            lockscreenVisibility = BusMonitorNotificationContract.LOCKSCREEN_VISIBILITY
            enableVibration(false)
            setSound(null, null)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(statusChannel)
            createNotificationChannel(alertChannel)
        }
    }

    private fun canPostNotifications(): Boolean {
        return !BusMonitorNotificationContract.requiresRuntimeNotificationPermission(Build.VERSION.SDK_INT) ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun Intent.toMonitorSessionSnapshot(nowMillis: Long): BusMonitorSessionSnapshot? {
        val query = FirstLegEtaQuery(
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
        return BusMonitorSessionPolicy.newSession(
            nowMillis = nowMillis,
            routeName = getStringExtra(EXTRA_ROUTE_NAME) ?: return null,
            walkingMinutes = getIntExtra(EXTRA_WALKING_MINUTES, 1),
            voiceEnabled = getBooleanExtra(EXTRA_VOICE_ENABLED, true),
            query = query
        )
    }

    companion object {
        internal const val NOTIFICATION_ID = 8201
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

        fun refreshIntent(context: Context): Intent {
            return Intent(context, BusMonitorService::class.java).setAction(ACTION_REFRESH)
        }

        internal fun pendingIntentFlags(): Int {
            return PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        }
    }
}
