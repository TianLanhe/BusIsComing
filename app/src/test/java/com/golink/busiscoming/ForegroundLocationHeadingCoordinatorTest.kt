package com.golink.busiscoming

import com.golink.busiscoming.data.location.CurrentLocationSnapshot
import com.golink.busiscoming.data.location.DeviceHeadingSnapshot
import com.golink.busiscoming.data.location.DeviceHeadingUpdatesCallback
import com.golink.busiscoming.data.location.DeviceHeadingUpdatesSource
import com.golink.busiscoming.data.location.ForegroundLocationHeadingCoordinator
import com.golink.busiscoming.data.location.ForegroundLocationHeadingState
import com.golink.busiscoming.data.location.ForegroundLocationUpdatesCallback
import com.golink.busiscoming.data.location.ForegroundLocationUpdatesSource
import com.golink.busiscoming.data.location.HeadingLivenessScheduler
import com.golink.busiscoming.data.location.LocationHeadingSubscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundLocationHeadingCoordinatorTest {
    @Test
    fun combinesLatestLocationAndHeadingRegardlessOfArrivalOrder() {
        val locationSource = FakeLocationSource()
        val headingSource = FakeHeadingSource()
        val states = mutableListOf<ForegroundLocationHeadingState>()
        val coordinator = createCoordinator(locationSource, headingSource)

        coordinator.start(states::add)
        headingSource.emit(heading(90f, elapsedRealtimeMillis = 120L))

        assertNull(states.last().location)
        assertEquals(90f, states.last().headingDegrees)

        locationSource.emit(location(22.3193, 114.1694, elapsedRealtimeMillis = 140L))

        assertEquals(22.3193, states.last().location?.latitude ?: 0.0, 0.0)
        assertEquals(114.1694, states.last().location?.longitude ?: 0.0, 0.0)
        assertEquals(90f, states.last().headingDegrees)
        assertFalse(states.last().calibrationRequired)
    }

    @Test
    fun ignoresOlderEventsAndCallbacksFromStoppedGeneration() {
        val locationSource = FakeLocationSource()
        val headingSource = FakeHeadingSource()
        val states = mutableListOf<ForegroundLocationHeadingState>()
        val coordinator = createCoordinator(locationSource, headingSource)

        coordinator.start(states::add)
        locationSource.emit(location(22.3, 114.1, elapsedRealtimeMillis = 200L))
        headingSource.emit(heading(120f, elapsedRealtimeMillis = 200L))
        locationSource.emit(location(23.0, 115.0, elapsedRealtimeMillis = 100L))
        headingSource.emit(heading(300f, elapsedRealtimeMillis = 100L))

        assertEquals(22.3, states.last().location?.latitude ?: 0.0, 0.0)
        assertEquals(120f, states.last().headingDegrees)

        val stateCountBeforeStop = states.size
        coordinator.stop()
        locationSource.emit(location(24.0, 116.0, elapsedRealtimeMillis = 300L))
        headingSource.emit(heading(240f, elapsedRealtimeMillis = 300L))

        assertEquals(stateCountBeforeStop, states.size)
        assertTrue(locationSource.subscriptions.single().closed)
        assertTrue(headingSource.subscriptions.single().closed)
    }

    @Test
    fun promptsOncePerCompletelyUnknownIntervalAndResetsAfterRecovery() {
        val headingSource = FakeHeadingSource()
        val states = mutableListOf<ForegroundLocationHeadingState>()
        val coordinator = createCoordinator(FakeLocationSource(), headingSource)

        coordinator.start(states::add)
        headingSource.emit(heading(10f, conservativeErrorDegrees = 180f, elapsedRealtimeMillis = 10L))
        val firstPromptSequence = states.last().calibrationPromptSequence
        headingSource.emit(heading(20f, conservativeErrorDegrees = 180f, elapsedRealtimeMillis = 20L))

        assertTrue(states.last().calibrationRequired)
        assertEquals(1L, firstPromptSequence)
        assertEquals(firstPromptSequence, states.last().calibrationPromptSequence)

        headingSource.emit(heading(30f, conservativeErrorDegrees = 12f, elapsedRealtimeMillis = 30L))
        assertFalse(states.last().calibrationRequired)

        headingSource.emit(heading(40f, conservativeErrorDegrees = null, elapsedRealtimeMillis = 40L))
        assertTrue(states.last().calibrationRequired)
        assertEquals(2L, states.last().calibrationPromptSequence)
    }

    @Test
    fun reportsHeadingFailureOncePerFailureIntervalAndRecoversOnValidHeading() {
        val headingSource = FakeHeadingSource()
        val states = mutableListOf<ForegroundLocationHeadingState>()
        val coordinator = createCoordinator(FakeLocationSource(), headingSource)

        coordinator.start(states::add)
        headingSource.emit(heading(25f, elapsedRealtimeMillis = 25L))
        headingSource.fail()
        val firstFailureSequence = states.last().headingUnavailableSequence
        headingSource.fail()

        assertTrue(states.last().headingUnavailable)
        assertNull(states.last().headingDegrees)
        assertEquals(1L, firstFailureSequence)
        assertEquals(firstFailureSequence, states.last().headingUnavailableSequence)

        headingSource.emit(heading(45f, elapsedRealtimeMillis = 50L))
        assertFalse(states.last().headingUnavailable)

        headingSource.fail()
        assertTrue(states.last().headingUnavailable)
        assertEquals(2L, states.last().headingUnavailableSequence)
    }

    @Test
    fun ignoresInvalidHeadingWithoutReplacingLastValidDirection() {
        val headingSource = FakeHeadingSource()
        val states = mutableListOf<ForegroundLocationHeadingState>()
        val coordinator = createCoordinator(FakeLocationSource(), headingSource)

        coordinator.start(states::add)
        headingSource.emit(heading(80f, elapsedRealtimeMillis = 10L))
        headingSource.emit(heading(Float.NaN, elapsedRealtimeMillis = 20L))
        headingSource.emit(heading(360f, elapsedRealtimeMillis = 30L))

        assertEquals(80f, states.last().headingDegrees)
    }

    @Test
    fun reportsLocationSourceFailureWithoutStoppingHeadingUpdates() {
        val headingSource = FakeHeadingSource()
        val states = mutableListOf<ForegroundLocationHeadingState>()
        val coordinator = createCoordinator(
            locationSource = ForegroundLocationUpdatesSource { error("location unavailable") },
            headingSource = headingSource
        )

        coordinator.start(states::add)
        headingSource.emit(heading(135f, elapsedRealtimeMillis = 60L))

        assertTrue(states.last().locationUnavailable)
        assertEquals(1L, states.last().locationUnavailableSequence)
        assertEquals(135f, states.last().headingDegrees)
    }

    @Test
    fun restartRejectsCallbacksCapturedByThePreviousGeneration() {
        val locationSource = FakeLocationSource()
        val headingSource = FakeHeadingSource()
        val states = mutableListOf<ForegroundLocationHeadingState>()
        val coordinator = createCoordinator(locationSource, headingSource)

        coordinator.start(states::add)
        coordinator.start(states::add)
        locationSource.emitFrom(0, location(22.0, 114.0, elapsedRealtimeMillis = 100L))
        headingSource.emitFrom(0, heading(180f, elapsedRealtimeMillis = 100L))

        assertNull(states.last().location)
        assertNull(states.last().headingDegrees)

        locationSource.emitFrom(1, location(22.3, 114.2, elapsedRealtimeMillis = 200L))
        headingSource.emitFrom(1, heading(45f, elapsedRealtimeMillis = 200L))

        assertEquals(22.3, states.last().location?.latitude ?: 0.0, 0.0)
        assertEquals(45f, states.last().headingDegrees)
    }

    @Test
    fun clearsStaleHeadingAndRecoversWhenFreshUpdatesResume() {
        val headingSource = FakeHeadingSource()
        val scheduler = FakeHeadingLivenessScheduler()
        val states = mutableListOf<ForegroundLocationHeadingState>()
        val coordinator = createCoordinator(
            locationSource = FakeLocationSource(),
            headingSource = headingSource,
            headingLivenessScheduler = scheduler
        )

        coordinator.start(states::add)
        headingSource.emit(heading(75f, elapsedRealtimeMillis = 100L))
        scheduler.runLatest()

        assertTrue(states.last().headingUnavailable)
        assertNull(states.last().headingDegrees)
        assertEquals(1L, states.last().headingUnavailableSequence)

        headingSource.emit(heading(80f, elapsedRealtimeMillis = 200L))

        assertFalse(states.last().headingUnavailable)
        assertEquals(80f, states.last().headingDegrees)
        assertEquals(3, scheduler.scheduledCount)
    }

    @Test
    fun reportsUnavailableWhenFirstHeadingNeverArrivesAndRecoversLater() {
        val headingSource = FakeHeadingSource()
        val scheduler = FakeHeadingLivenessScheduler()
        val states = mutableListOf<ForegroundLocationHeadingState>()
        val coordinator = createCoordinator(
            locationSource = FakeLocationSource(),
            headingSource = headingSource,
            headingLivenessScheduler = scheduler
        )

        coordinator.start(states::add)
        scheduler.runLatest()

        assertTrue(states.last().headingUnavailable)
        assertNull(states.last().headingDegrees)
        assertEquals(1L, states.last().headingUnavailableSequence)

        headingSource.emit(heading(35f, elapsedRealtimeMillis = 100L))

        assertFalse(states.last().headingUnavailable)
        assertEquals(35f, states.last().headingDegrees)
    }

    @Test
    fun cancelsHeadingLivenessTimeoutOnStopAndSupersedesItOnNewHeading() {
        val headingSource = FakeHeadingSource()
        val scheduler = FakeHeadingLivenessScheduler()
        val states = mutableListOf<ForegroundLocationHeadingState>()
        val coordinator = createCoordinator(
            locationSource = FakeLocationSource(),
            headingSource = headingSource,
            headingLivenessScheduler = scheduler
        )

        coordinator.start(states::add)
        headingSource.emit(heading(10f, elapsedRealtimeMillis = 100L))
        val firstTimeout = scheduler.latest()
        headingSource.emit(heading(20f, elapsedRealtimeMillis = 200L))

        firstTimeout.runEvenIfCancelled()
        assertEquals(20f, states.last().headingDegrees)
        assertFalse(states.last().headingUnavailable)

        val stateCountBeforeStop = states.size
        coordinator.stop()
        scheduler.runLatestEvenIfCancelled()

        assertEquals(stateCountBeforeStop, states.size)
        assertTrue(scheduler.latest().closed)
    }

    private fun location(
        latitude: Double,
        longitude: Double,
        elapsedRealtimeMillis: Long
    ) = CurrentLocationSnapshot(latitude, longitude, 15f, elapsedRealtimeMillis)

    private fun heading(
        degrees: Float,
        conservativeErrorDegrees: Float? = 10f,
        elapsedRealtimeMillis: Long
    ) = DeviceHeadingSnapshot(
        headingDegrees = degrees,
        headingErrorDegrees = 8f,
        conservativeErrorDegrees = conservativeErrorDegrees,
        elapsedRealtimeMillis = elapsedRealtimeMillis
    )

    private fun createCoordinator(
        locationSource: ForegroundLocationUpdatesSource,
        headingSource: DeviceHeadingUpdatesSource,
        headingLivenessScheduler: HeadingLivenessScheduler = FakeHeadingLivenessScheduler()
    ) = ForegroundLocationHeadingCoordinator(
        locationSource = locationSource,
        headingSource = headingSource,
        headingLivenessScheduler = headingLivenessScheduler
    )
}

private class FakeLocationSource : ForegroundLocationUpdatesSource {
    val subscriptions = mutableListOf<FakeSubscription>()
    private val callbacks = mutableListOf<ForegroundLocationUpdatesCallback>()

    override fun start(callback: ForegroundLocationUpdatesCallback): LocationHeadingSubscription {
        callbacks += callback
        return FakeSubscription().also(subscriptions::add)
    }

    fun emit(snapshot: CurrentLocationSnapshot) {
        callbacks.forEach { it.onLocation(snapshot) }
    }

    fun emitFrom(index: Int, snapshot: CurrentLocationSnapshot) {
        callbacks[index].onLocation(snapshot)
    }
}

private class FakeHeadingSource : DeviceHeadingUpdatesSource {
    val subscriptions = mutableListOf<FakeSubscription>()
    private val callbacks = mutableListOf<DeviceHeadingUpdatesCallback>()

    override fun start(callback: DeviceHeadingUpdatesCallback): LocationHeadingSubscription {
        callbacks += callback
        return FakeSubscription().also(subscriptions::add)
    }

    fun emit(snapshot: DeviceHeadingSnapshot) {
        callbacks.forEach { it.onHeading(snapshot) }
    }

    fun emitFrom(index: Int, snapshot: DeviceHeadingSnapshot) {
        callbacks[index].onHeading(snapshot)
    }

    fun fail() {
        callbacks.forEach(DeviceHeadingUpdatesCallback::onFailure)
    }
}

private class FakeSubscription : LocationHeadingSubscription {
    var closed = false

    override fun close() {
        closed = true
    }
}

private class FakeHeadingLivenessScheduler : HeadingLivenessScheduler {
    private val tasks = mutableListOf<ScheduledTask>()
    val scheduledCount: Int
        get() = tasks.size

    override fun schedule(delayMillis: Long, block: () -> Unit): LocationHeadingSubscription {
        require(delayMillis > 0L)
        return ScheduledTask(block).also(tasks::add)
    }

    fun latest(): ScheduledTask = tasks.last()

    fun runLatest() = latest().run()

    fun runLatestEvenIfCancelled() = latest().runEvenIfCancelled()
}

private class ScheduledTask(
    private val block: () -> Unit
) : LocationHeadingSubscription {
    var closed = false
        private set

    override fun close() {
        closed = true
    }

    fun run() {
        if (!closed) block()
    }

    fun runEvenIfCancelled() = block()
}
