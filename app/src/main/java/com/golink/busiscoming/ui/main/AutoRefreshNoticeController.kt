package com.golink.busiscoming.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.LinearLayout
import android.widget.TextView
import com.golink.busiscoming.R
import com.golink.busiscoming.data.local.AutoRefreshNoticeStore
import com.golink.busiscoming.data.local.RouteAutoRefreshInterval
import com.golink.busiscoming.data.local.RouteAutoRefreshSettingsStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class AutoRefreshNoticeController(
    private val context: Context,
    private val root: View,
    private val settingsStore: RouteAutoRefreshSettingsStore,
    private val noticeStore: AutoRefreshNoticeStore,
    private val onOpenSettings: () -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper())
) : AutoCloseable {
    private val body: LinearLayout = root.findViewById(R.id.autoRefreshNoticeBody)
    private val message: TextView = root.findViewById(R.id.autoRefreshNoticeMessage)
    private val settings: MaterialButton = root.findViewById(R.id.autoRefreshNoticeSettings)
    private val countdown: LinearProgressIndicator =
        root.findViewById(R.id.autoRefreshNoticeCountdown)
    private var completionRunnable: Runnable? = null
    private var countdownAnimator: ValueAnimator? = null
    private var showing = false

    init {
        applyResponsiveLayout()
        settings.setOnClickListener {
            if (!showing) return@setOnClickListener
            noticeStore.complete()
            dismiss(animate = animationsEnabled())
            onOpenSettings()
        }
    }

    fun showAfterSuccessfulQuery(interval: RouteAutoRefreshInterval) {
        if (
            showing ||
            interval == RouteAutoRefreshInterval.OFF ||
            !noticeStore.shouldShow(settingsStore.hasExplicitSelection())
        ) return
        showing = true
        message.text = context.getString(
            R.string.auto_refresh_notice_interval,
            context.getString(interval.labelRes())
        )
        countdown.progress = COUNTDOWN_MAX
        root.animate().cancel()
        root.visibility = View.VISIBLE
        root.alpha = if (animationsEnabled()) 0f else 1f
        root.translationY = if (animationsEnabled()) -root.resources.displayMetrics.density * 8f else 0f
        val announceAndStart = {
            root.announceForAccessibility(
                "${context.getString(R.string.auto_refresh_notice_title)}. ${message.text}"
            )
            startVisibleCountdown()
        }
        if (animationsEnabled()) {
            root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(ENTER_EXIT_DURATION_MS)
                .withEndAction(announceAndStart)
                .start()
        } else {
            announceAndStart()
        }
    }

    fun interrupt() {
        if (!showing) return
        dismiss(animate = false)
    }

    override fun close() {
        interrupt()
        settings.setOnClickListener(null)
    }

    private fun startVisibleCountdown() {
        if (!showing) return
        val visibleMillis = recommendedVisibleTimeoutMillis()
        if (animationsEnabled()) {
            countdownAnimator = ValueAnimator.ofInt(COUNTDOWN_MAX, 0).apply {
                duration = visibleMillis
                addUpdateListener { countdown.progress = it.animatedValue as Int }
                start()
            }
        }
        val runnable = Runnable {
            if (!showing) return@Runnable
            noticeStore.complete()
            dismiss(animate = animationsEnabled())
        }
        completionRunnable = runnable
        handler.postDelayed(runnable, visibleMillis)
    }

    private fun dismiss(animate: Boolean) {
        showing = false
        completionRunnable?.let(handler::removeCallbacks)
        completionRunnable = null
        countdownAnimator?.cancel()
        countdownAnimator = null
        root.animate().cancel()
        if (animate) {
            root.animate()
                .alpha(0f)
                .translationY(-root.resources.displayMetrics.density * 8f)
                .setDuration(ENTER_EXIT_DURATION_MS)
                .withEndAction(::hideImmediately)
                .start()
        } else {
            hideImmediately()
        }
    }

    private fun hideImmediately() {
        root.visibility = View.GONE
        root.alpha = 1f
        root.translationY = 0f
        countdown.progress = COUNTDOWN_MAX
    }

    private fun recommendedVisibleTimeoutMillis(): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return BASE_VISIBLE_DURATION_MS
        val manager = context.getSystemService(AccessibilityManager::class.java)
            ?: return BASE_VISIBLE_DURATION_MS
        return manager.getRecommendedTimeoutMillis(
            BASE_VISIBLE_DURATION_MS.toInt(),
            AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS
        ).toLong()
    }

    private fun applyResponsiveLayout() {
        val configuration = context.resources.configuration
        if (configuration.screenWidthDp >= 360 && configuration.fontScale < 1.3f) return
        body.orientation = LinearLayout.VERTICAL
        val textGroup = body.getChildAt(0)
        textGroup.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        settings.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dp(48)
        ).apply { gravity = android.view.Gravity.END }
    }

    private fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    private fun RouteAutoRefreshInterval.labelRes(): Int = when (this) {
        RouteAutoRefreshInterval.OFF -> R.string.auto_refresh_off
        RouteAutoRefreshInterval.MINUTES_1 -> R.string.auto_refresh_one_minute
        RouteAutoRefreshInterval.MINUTES_2 -> R.string.auto_refresh_two_minutes
        RouteAutoRefreshInterval.MINUTES_5 -> R.string.auto_refresh_five_minutes
        RouteAutoRefreshInterval.MINUTES_10 -> R.string.auto_refresh_ten_minutes
    }

    private fun dp(value: Int): Int = (value * root.resources.displayMetrics.density).toInt()

    private companion object {
        const val COUNTDOWN_MAX = 1000
        const val ENTER_EXIT_DURATION_MS = 200L
        const val BASE_VISIBLE_DURATION_MS = 5_000L
    }
}
