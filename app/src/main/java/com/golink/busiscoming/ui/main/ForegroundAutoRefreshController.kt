package com.golink.busiscoming.ui.main

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.golink.busiscoming.data.local.RouteAutoRefreshInterval
import kotlin.math.max

fun interface AutoRefreshScheduleHandle {
    fun cancel()
}

fun interface AutoRefreshScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): AutoRefreshScheduleHandle
}

sealed interface ForegroundAutoRefreshState {
    data object Disabled : ForegroundAutoRefreshState
    data class Waiting(val dueAtMillis: Long) : ForegroundAutoRefreshState
    data object Paused : ForegroundAutoRefreshState
    data class Refreshing(val generation: Int) : ForegroundAutoRefreshState
}

class ForegroundAutoRefreshController(
    private val monotonicClock: () -> Long,
    private val scheduler: AutoRefreshScheduler,
    private val onAutomaticTrigger: (generation: Int) -> Unit
) : AutoCloseable {
    constructor(onAutomaticTrigger: (generation: Int) -> Unit) : this(
        monotonicClock = SystemClock::elapsedRealtime,
        scheduler = HandlerAutoRefreshScheduler(),
        onAutomaticTrigger = onAutomaticTrigger
    )

    private var interval = RouteAutoRefreshInterval.MINUTES_1
    private var eligible = false
    private var externalBusy = false
    private var lastSuccessfulAt: Long? = null
    private var lastAttemptFinishedAt: Long? = null
    private var activeGeneration: Int? = null
    private var generation = 0
    private var scheduleToken = 0L
    private var scheduleHandle: AutoRefreshScheduleHandle? = null
    private var lastObservedNow = Long.MIN_VALUE
    private var closed = false

    var state: ForegroundAutoRefreshState = ForegroundAutoRefreshState.Paused
        private set

    fun setInterval(value: RouteAutoRefreshInterval) {
        if (interval == value && !closed) return
        interval = value
        if (value == RouteAutoRefreshInterval.OFF) invalidateActive()
        reschedule()
    }

    fun setEligible(value: Boolean) {
        if (eligible == value || closed) return
        eligible = value
        if (!value) invalidateActive()
        reschedule()
    }

    fun setExternalBusy(value: Boolean) {
        if (externalBusy == value || closed) return
        if (value && activeGeneration != null) return
        externalBusy = value
        reschedule()
    }

    fun recordSuccessfulBaseline() {
        if (closed) return
        lastSuccessfulAt = now()
        reschedule()
    }

    fun completeAutomatic(generation: Int, success: Boolean): Boolean {
        if (closed || activeGeneration != generation) return false
        val completedAt = now()
        activeGeneration = null
        lastAttemptFinishedAt = completedAt
        if (success) lastSuccessfulAt = completedAt
        reschedule()
        return true
    }

    fun canStartExternalQuery(): Boolean = activeGeneration == null && !externalBusy && !closed

    fun invalidate() {
        if (closed) return
        invalidateActive()
        lastSuccessfulAt = null
        lastAttemptFinishedAt = null
        reschedule()
    }

    override fun close() {
        if (closed) return
        closed = true
        scheduleToken += 1
        scheduleHandle?.cancel()
        scheduleHandle = null
        activeGeneration = null
        state = ForegroundAutoRefreshState.Paused
    }

    private fun reschedule() {
        val token = ++scheduleToken
        scheduleHandle?.cancel()
        scheduleHandle = null
        if (closed) return
        val intervalMillis = interval.millis
        if (intervalMillis == null) {
            state = ForegroundAutoRefreshState.Disabled
            return
        }
        activeGeneration?.let {
            state = ForegroundAutoRefreshState.Refreshing(it)
            return
        }
        val successAt = lastSuccessfulAt
        if (!eligible || externalBusy || successAt == null) {
            state = ForegroundAutoRefreshState.Paused
            return
        }
        val dueAt = max(
            successAt + intervalMillis,
            (lastAttemptFinishedAt ?: Long.MIN_VALUE) + intervalMillis
        )
        state = ForegroundAutoRefreshState.Waiting(dueAt)
        val delay = (dueAt - now()).coerceAtLeast(0L)
        if (delay == 0L) {
            triggerAutomatic(token)
        } else {
            scheduleHandle = scheduler.schedule(delay) { triggerAutomatic(token) }
        }
    }

    private fun triggerAutomatic(token: Long) {
        if (token != scheduleToken) return
        scheduleHandle = null
        if (closed || interval == RouteAutoRefreshInterval.OFF || !eligible || externalBusy || activeGeneration != null) {
            reschedule()
            return
        }
        val next = ++generation
        activeGeneration = next
        state = ForegroundAutoRefreshState.Refreshing(next)
        onAutomaticTrigger(next)
    }

    private fun invalidateActive() {
        activeGeneration = null
    }

    private fun now(): Long {
        val current = monotonicClock()
        lastObservedNow = if (lastObservedNow == Long.MIN_VALUE) current else max(lastObservedNow, current)
        return lastObservedNow
    }
}

private class HandlerAutoRefreshScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : AutoRefreshScheduler {
    override fun schedule(delayMillis: Long, action: () -> Unit): AutoRefreshScheduleHandle {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMillis)
        return AutoRefreshScheduleHandle { handler.removeCallbacks(runnable) }
    }
}
