package com.golink.busiscoming.service

import android.Manifest
import android.app.Notification
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
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.golink.busiscoming.R
import com.golink.busiscoming.data.localization.AppLanguageRuntime
import com.golink.busiscoming.data.model.BusMonitorRefreshPolicy
import com.golink.busiscoming.data.model.BusMonitorSessionPolicy
import com.golink.busiscoming.data.model.BusMonitorSessionSnapshot
import com.golink.busiscoming.data.model.BusMonitorSpeechPolicy
import com.golink.busiscoming.data.model.BusMonitorStateEvaluator
import com.golink.busiscoming.data.model.BusMonitorStatus
import com.golink.busiscoming.data.model.BusMonitorStopPolicy
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.model.EtaUnavailableReason
import com.golink.busiscoming.data.repository.CitybusFirstLegEtaService
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.TransitCodeEntryPoint
import com.golink.busiscoming.ui.common.localizedText
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
    private var monitorWakeLock: PowerManager.WakeLock? = null
    private val speechRetryAfterByStatus = mutableMapOf<BusMonitorStatus, Long>()
    private val speechFailureToastPolicy = BusMonitorSpeechFailureToastPolicy()
    private var languageVersion: Long = -1L

    private val refreshRunnable = Runnable { refreshEta() }

    override fun onCreate() {
        super.onCreate()
        sessionStore = BusMonitorSessionStore(this)
        refreshScheduler = BusMonitorRefreshScheduler(this)
        resetSpeechController()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        syncLanguageIfNeeded()
        return when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring(clearSession = true)
                START_NOT_STICKY
            }
            ACTION_AUTO_STOP -> {
                stopMonitoring(clearSession = true)
                START_NOT_STICKY
            }
            ACTION_START -> startNewSession(intent)
            ACTION_LANGUAGE_CHANGED -> handleLanguageChanged()
            ACTION_REFRESH -> refreshExistingSession()
            else -> refreshExistingSession()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        sessionStore.load()?.let { currentSession ->
            refreshScheduler.scheduleNext()
            scheduleAutoStopIfNeeded(currentSession)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(refreshRunnable)
        etaExecutor.shutdownNow()
        releaseWakeLock()
        speechController.release()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopMonitoring(clearSession = true)
        super.onTimeout(startId, fgsType)
    }

    private fun startNewSession(intent: Intent): Int {
        if (!canPostNotifications()) {
            sessionStore.clear()
            stopSelf()
            return START_NOT_STICKY
        }
        val nextSession = intent.toMonitorSessionSnapshot(nowMillis = System.currentTimeMillis())
            ?: return START_NOT_STICKY
        val snapshot = AppLanguageRuntime.snapshot()
        val localizedSession = nextSession.copy(query = nextSession.query.copy(lang = snapshot.citybusLanguage))
        session = localizedSession
        sessionStore.save(localizedSession)
        speechFailureToastPolicy.reset()
        speechRetryAfterByStatus.clear()
        resetSpeechController()
        acquireWakeLock(localizedSession)
        startForegroundFor(
            snapshot = localizedSession,
            text = getString(R.string.notification_loading),
            priorityStatus = null
        )
        refreshEta()
        return START_STICKY
    }

    private fun handleLanguageChanged(): Int {
        val currentSession = restoreActiveSessionOrStop() ?: return START_NOT_STICKY
        syncLanguageIfNeeded(force = true)
        ensureNotificationChannel()
        val localizedSession = session ?: currentSession
        startForegroundFor(
            snapshot = localizedSession,
            text = getString(R.string.notification_loading),
            priorityStatus = localizedSession.lastStatus
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
        acquireWakeLock(currentSession)
        scheduleAutoStopIfNeeded(currentSession)
        startForegroundFor(
            snapshot = currentSession,
                text = currentSession.lastSuccessfulNotificationText
                    ?: getString(R.string.notification_loading),
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
                .getOrDefault(WaitTimeState.Unavailable(EtaUnavailableReason.UNEXPECTED_ERROR))
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
        val notificationBody = BusMonitorNotificationFormatter.bodyText(
            firstEtaMinutes = firstArrival.minutes,
            nextEtaMinutes = available.nextArrival?.minutes,
            walkingMinutes = activeSession.walkingMinutes,
            updatedAtText = updatedAtText,
            text = localizedText()
        )
        var lastSpokenStatus = activeSession.lastSpokenStatus
        val shouldSpeak = activeSession.voiceEnabled &&
            BusMonitorSpeechPolicy.shouldSpeak(lastSpokenStatus, status)
        Log.d(
            TAG,
            "Monitor speech decision voiceEnabled=${activeSession.voiceEnabled} " +
                "firstEtaMinutes=${firstArrival.minutes} walkingMinutes=${activeSession.walkingMinutes} " +
                "lastStatus=${activeSession.lastStatus} nextStatus=$status lastSpokenStatus=$lastSpokenStatus " +
                "shouldSpeak=$shouldSpeak"
        )
        if (shouldSpeak && !shouldThrottleSpeech(status)) {
            val phrase = BusMonitorSpeechFormatter.phrase(
                firstArrival.minutes,
                status,
                localizedText()
            )
            Log.d(TAG, "Monitor speech request status=$status")
            val speechResult = speechController.speak(
                text = phrase,
                mode = BusMonitorSpeechAudioMode.MONITOR
            ) { result ->
                handleSpeechEvent(status, result)
            }
            Log.d(
                TAG,
                "Monitor speech immediateResult=$speechResult status=$status countsAsSpoken=${speechResult.countsAsSpoken}"
            )
            if (speechResult.countsAsSpoken) {
                lastSpokenStatus = status
            }
        } else if (shouldSpeak) {
            Log.d(TAG, "Monitor speech throttled status=$status retryAfter=${speechRetryAfterByStatus[status]}")
        }

        val updatedSession = BusMonitorSessionPolicy.recordSuccessfulRefresh(
            snapshot = activeSession,
            nowMillis = System.currentTimeMillis(),
            status = status,
            lastSpokenStatus = lastSpokenStatus,
            notificationText = notificationBody,
            firstEtaMillis = firstArrival.etaMillis,
            secondEtaMillis = available.nextArrival?.etaMillis
        )
        session = updatedSession
        sessionStore.save(updatedSession)
        acquireWakeLock(updatedSession)
        scheduleAutoStopIfNeeded(updatedSession)
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                title = BusMonitorNotificationFormatter.title(
                    updatedSession.routeName,
                    status,
                    localizedText()
                ),
                text = notificationBody,
                bigText = notificationBody,
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
        scheduleAutoStopIfNeeded(failedSession)
        val fallbackText = BusMonitorNotificationFormatter.failureText(
            lastSuccessfulNotificationText = failedSession.lastSuccessfulNotificationText,
            failureCount = failedSession.consecutiveFailureCount,
            text = localizedText()
        )
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                title = BusMonitorNotificationFormatter.title(
                    failedSession.routeName,
                    failedSession.lastStatus,
                    localizedText()
                ),
                text = fallbackText,
                bigText = fallbackText,
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
            stopAtMillis = currentSession.stopAtMillis
        )
    }

    private fun scheduleNextRefresh() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, BusMonitorRefreshPolicy.REFRESH_INTERVAL_MILLIS)
        refreshScheduler.scheduleNext()
        session?.let { scheduleAutoStopIfNeeded(it) }
    }

    private fun scheduleAutoStopIfNeeded(currentSession: BusMonitorSessionSnapshot) {
        val stopAtMillis = currentSession.stopAtMillis ?: return
        refreshScheduler.scheduleAutoStop(stopAtMillis)
    }

    private fun stopMonitoring(clearSession: Boolean) {
        mainHandler.removeCallbacks(refreshRunnable)
        refreshScheduler.cancel()
        speechRetryAfterByStatus.clear()
        if (clearSession) {
            sessionStore.clear()
        }
        session = null
        releaseWakeLock()
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
                title = BusMonitorNotificationFormatter.title(
                    snapshot.routeName,
                    priorityStatus,
                    localizedText()
                ),
                text = text,
                bigText = text,
                priorityStatus = priorityStatus
            )
        )
    }

    private fun handleSpeechEvent(status: BusMonitorStatus, result: BusMonitorSpeechResult) {
        Log.d(TAG, "Monitor speech event status=$status result=$result countsAsSpoken=${result.countsAsSpoken}")
        if (result.countsAsSpoken) {
            speechRetryAfterByStatus.remove(status)
            mainHandler.post { markSpokenStatus(status) }
        } else if (result is BusMonitorSpeechResult.Failure) {
            speechRetryAfterByStatus[status] =
                System.currentTimeMillis() + BusMonitorRefreshPolicy.REFRESH_INTERVAL_MILLIS
            Log.w(TAG, "Monitor speech failed: reason=${result.reason} detail=${result.detail}")
            showSpeechFailureOnce(result)
        }
    }

    private fun showSpeechFailureOnce(failure: BusMonitorSpeechResult.Failure) {
        if (!speechFailureToastPolicy.shouldShow(session?.voiceEnabled == true, failure.reason)) return
        val messageRes = BusMonitorSpeechFailureMessage.resourceId(failure.reason)
        mainHandler.post {
            Toast.makeText(applicationContext, messageRes, Toast.LENGTH_LONG).show()
        }
    }

    private fun resetSpeechController() {
        if (::speechController.isInitialized) speechController.release()
        val snapshot = AppLanguageRuntime.snapshot()
        languageVersion = snapshot.version
        speechController = BusMonitorSpeechController(
            context = applicationContext,
            languageFamily = snapshot.ttsLanguageFamily
        ) { failure -> showSpeechFailureOnce(failure) }
    }

    private fun syncLanguageIfNeeded(force: Boolean = false) {
        val snapshot = AppLanguageRuntime.snapshot()
        if (!force && snapshot.version == languageVersion) return
        resetSpeechController()
        session?.let { current ->
            val updated = current.copy(
                query = current.query.copy(lang = snapshot.citybusLanguage),
                lastSuccessfulNotificationText = null
            )
            session = updated
            sessionStore.save(updated)
        }
        ensureNotificationChannel()
    }

    private fun shouldThrottleSpeech(status: BusMonitorStatus): Boolean {
        val retryAfter = speechRetryAfterByStatus[status] ?: return false
        return System.currentTimeMillis() < retryAfter
    }

    private fun acquireWakeLock(snapshot: BusMonitorSessionSnapshot) {
        val timeoutMillis = BusMonitorWakeLockPolicy.timeoutMillis(
            nowMillis = System.currentTimeMillis(),
            expiresAtMillis = snapshot.expiresAtMillis,
            stopAtMillis = snapshot.stopAtMillis
        )
        val currentWakeLock = monitorWakeLock
        if (currentWakeLock?.isHeld == true) {
            currentWakeLock.release()
        }
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        monitorWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:BusMonitor"
        ).apply {
            setReferenceCounted(false)
            acquire(timeoutMillis)
        }
    }

    private fun releaseWakeLock() {
        val wakeLock = monitorWakeLock
        if (wakeLock?.isHeld == true) {
            wakeLock.release()
        }
        monitorWakeLock = null
    }

    private fun markSpokenStatus(status: BusMonitorStatus) {
        val currentSession = session ?: sessionStore.load() ?: return
        if (currentSession.lastSpokenStatus == status) return
        val updatedSession = currentSession.copy(lastSpokenStatus = status)
        session = updatedSession
        sessionStore.save(updatedSession)
    }

    private fun buildNotification(
        title: String,
        text: String,
        bigText: String,
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
        val transitCodeIntent = PendingIntent.getActivity(
            this,
            REQUEST_TRANSIT_CODE,
            TransitCodeEntryPoint.createIntent(this),
            pendingIntentFlags()
        )
        val stopIntent = servicePendingIntent(REQUEST_STOP, ACTION_STOP)
        val channelId = BusMonitorNotificationContract.channelIdFor(priorityStatus)
        val priority = BusMonitorNotificationContract.priorityFor(priorityStatus)
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(BusMonitorNotificationContract.COMPAT_LOCKSCREEN_VISIBILITY)
            .setPublicVersion(buildPublicNotification(channelId, title, text, bigText))
            .setPriority(priority)
            .addAction(
                android.R.drawable.ic_popup_sync,
                getString(R.string.notification_refresh),
                refreshIntent
            )
            .addAction(
                R.drawable.ic_transit_code,
                getString(R.string.transit_code),
                transitCodeIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_stop),
                stopIntent
            )
            .build()
    }

    private fun buildPublicNotification(channelId: String, title: String, text: String, bigText: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
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
        BusMonitorNotificationChannelManager.forContext(this).ensureChannels()
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
        private const val TAG = "BusMonitorService"
        private const val ACTION_START = "com.golink.busiscoming.action.START_BUS_MONITOR"
        private const val ACTION_REFRESH = "com.golink.busiscoming.action.REFRESH_BUS_MONITOR"
        private const val ACTION_AUTO_STOP = "com.golink.busiscoming.action.AUTO_STOP_BUS_MONITOR"
        private const val ACTION_STOP = "com.golink.busiscoming.action.STOP_BUS_MONITOR"
        private const val ACTION_LANGUAGE_CHANGED =
            "com.golink.busiscoming.action.BUS_MONITOR_LANGUAGE_CHANGED"

        private const val REQUEST_OPEN = 100
        private const val REQUEST_REFRESH = 101
        private const val REQUEST_TRANSIT_CODE = 102
        private const val REQUEST_STOP = 103

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

        fun autoStopIntent(context: Context): Intent {
            return Intent(context, BusMonitorService::class.java).setAction(ACTION_AUTO_STOP)
        }

        fun languageChangedIntent(context: Context): Intent =
            Intent(context, BusMonitorService::class.java).setAction(ACTION_LANGUAGE_CHANGED)

        internal fun pendingIntentFlags(): Int {
            return PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        }
    }
}
