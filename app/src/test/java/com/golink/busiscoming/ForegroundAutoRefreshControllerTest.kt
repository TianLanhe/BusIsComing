package com.golink.busiscoming

import com.golink.busiscoming.data.local.RouteAutoRefreshInterval
import com.golink.busiscoming.ui.main.AutoRefreshScheduleHandle
import com.golink.busiscoming.ui.main.AutoRefreshScheduler
import com.golink.busiscoming.ui.main.ForegroundAutoRefreshController
import com.golink.busiscoming.ui.main.ForegroundAutoRefreshState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAutoRefreshControllerTest {
    @Test
    fun firesExactlyAtDueTimeAndNeverCatchesUpMultipleIntervals() {
        val clock = FakeClock(1_000)
        val scheduler = FakeScheduler(clock)
        val generations = mutableListOf<Int>()
        val controller = ForegroundAutoRefreshController(clock::now, scheduler) { generations += it }
        controller.setInterval(RouteAutoRefreshInterval.MINUTES_1)
        controller.setEligible(true)
        controller.recordSuccessfulBaseline()

        scheduler.advanceBy(59_999)
        assertTrue(generations.isEmpty())
        scheduler.advanceBy(1)
        assertEquals(listOf(1), generations)
        assertTrue(controller.state is ForegroundAutoRefreshState.Refreshing)

        scheduler.advanceBy(180_000)
        assertEquals(1, generations.size)
        controller.completeAutomatic(1, success = false)
        scheduler.advanceBy(59_999)
        assertEquals(1, generations.size)
        scheduler.advanceBy(1)
        assertEquals(listOf(1, 2), generations)
    }

    @Test
    fun pauseResumeIntervalChangesAndDisableInvalidateOldCallbacks() {
        val clock = FakeClock(0)
        val scheduler = FakeScheduler(clock)
        val generations = mutableListOf<Int>()
        val controller = ForegroundAutoRefreshController(clock::now, scheduler) { generations += it }
        controller.recordSuccessfulBaseline()
        controller.setEligible(true)
        controller.setInterval(RouteAutoRefreshInterval.MINUTES_2)
        scheduler.advanceBy(60_000)
        controller.setEligible(false)
        scheduler.advanceBy(120_000)
        assertTrue(generations.isEmpty())

        controller.setEligible(true)
        assertEquals(listOf(1), generations)
        controller.setInterval(RouteAutoRefreshInterval.OFF)
        assertTrue(controller.state is ForegroundAutoRefreshState.Disabled)
        controller.completeAutomatic(1, success = true)
        assertTrue(controller.state is ForegroundAutoRefreshState.Disabled)
    }

    @Test
    fun nextDueUsesLaterOfSuccessAndAttemptCompletionAndExternalBusyPauses() {
        val clock = FakeClock(10_000)
        val scheduler = FakeScheduler(clock)
        val generations = mutableListOf<Int>()
        val controller = ForegroundAutoRefreshController(clock::now, scheduler) { generations += it }
        controller.setInterval(RouteAutoRefreshInterval.MINUTES_1)
        controller.setEligible(true)
        controller.recordSuccessfulBaseline()
        scheduler.advanceBy(60_000)
        assertEquals(listOf(1), generations)
        scheduler.advanceBy(20_000)
        controller.completeAutomatic(1, success = true)
        controller.setExternalBusy(true)
        scheduler.advanceBy(60_000)
        assertEquals(1, generations.size)
        controller.setExternalBusy(false)
        assertEquals(listOf(1, 2), generations)
        assertFalse(controller.canStartExternalQuery())
    }

    private class FakeClock(var value: Long) {
        fun now(): Long = value
    }

    private class FakeScheduler(private val clock: FakeClock) : AutoRefreshScheduler {
        private val tasks = mutableListOf<Task>()
        override fun schedule(delayMillis: Long, action: () -> Unit): AutoRefreshScheduleHandle {
            val task = Task(clock.value + delayMillis, action)
            tasks += task
            return AutoRefreshScheduleHandle { task.cancelled = true }
        }

        fun advanceBy(delta: Long) {
            clock.value += delta
            while (true) {
                val task = tasks.filterNot(Task::cancelled).minByOrNull(Task::at) ?: return
                if (task.at > clock.value) return
                tasks.remove(task)
                task.action()
            }
        }

        private data class Task(val at: Long, val action: () -> Unit, var cancelled: Boolean = false)
    }
}
