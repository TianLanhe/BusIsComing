package com.golink.busiscoming.data.location

fun interface LocationHeadingSubscription {
    fun close()
}

fun interface HeadingLivenessScheduler {
    fun schedule(delayMillis: Long, block: () -> Unit): LocationHeadingSubscription
}

interface ForegroundLocationUpdatesCallback {
    fun onLocation(snapshot: CurrentLocationSnapshot)
    fun onFailure() = Unit
}

fun interface ForegroundLocationUpdatesSource {
    fun start(callback: ForegroundLocationUpdatesCallback): LocationHeadingSubscription
}

data class DeviceHeadingSnapshot(
    val headingDegrees: Float,
    val headingErrorDegrees: Float,
    val conservativeErrorDegrees: Float?,
    val elapsedRealtimeMillis: Long
)

interface DeviceHeadingUpdatesCallback {
    fun onHeading(snapshot: DeviceHeadingSnapshot)
    fun onFailure()
}

fun interface DeviceHeadingUpdatesSource {
    fun start(callback: DeviceHeadingUpdatesCallback): LocationHeadingSubscription
}

data class ForegroundLocationHeadingState(
    val location: CurrentLocationSnapshot? = null,
    val locationUnavailable: Boolean = false,
    val locationUnavailableSequence: Long = 0L,
    val headingDegrees: Float? = null,
    val calibrationRequired: Boolean = false,
    val calibrationPromptSequence: Long = 0L,
    val headingUnavailable: Boolean = false,
    val headingUnavailableSequence: Long = 0L
)

interface ForegroundLocationHeadingTracker {
    fun start(observer: (ForegroundLocationHeadingState) -> Unit)
    fun stop()
    fun latestLocation(): CurrentLocationSnapshot?
}

class ForegroundLocationHeadingCoordinator(
    private val locationSource: ForegroundLocationUpdatesSource,
    private val headingSource: DeviceHeadingUpdatesSource,
    private val headingLivenessScheduler: HeadingLivenessScheduler
) : ForegroundLocationHeadingTracker {
    private var generation = 0L
    private var observer: ((ForegroundLocationHeadingState) -> Unit)? = null
    private var locationSubscription: LocationHeadingSubscription? = null
    private var headingSubscription: LocationHeadingSubscription? = null
    private var headingLivenessSubscription: LocationHeadingSubscription? = null
    private var state = ForegroundLocationHeadingState()
    private var latestHeadingElapsedRealtimeMillis: Long? = null

    override fun start(observer: (ForegroundLocationHeadingState) -> Unit) {
        stop()
        val activeGeneration = ++generation
        this.observer = observer
        state = ForegroundLocationHeadingState()
        latestHeadingElapsedRealtimeMillis = null
        observer(state)

        locationSubscription = runCatching {
            locationSource.start(object : ForegroundLocationUpdatesCallback {
                override fun onLocation(snapshot: CurrentLocationSnapshot) {
                    if (!isActive(activeGeneration) || !snapshot.isUsable()) return
                    val previous = state.location
                    if (previous != null && snapshot.elapsedRealtimeMillis < previous.elapsedRealtimeMillis) return
                    publish(state.copy(location = snapshot, locationUnavailable = false))
                }

                override fun onFailure() {
                    publishLocationFailure(activeGeneration)
                }
            })
        }.getOrElse {
            publishLocationFailure(activeGeneration)
            null
        }

        if (!isActive(activeGeneration)) return
        scheduleHeadingLivenessCheck(
            activeGeneration = activeGeneration,
            expectedElapsedRealtimeMillis = null
        )
        headingSubscription = runCatching {
            headingSource.start(object : DeviceHeadingUpdatesCallback {
                override fun onHeading(snapshot: DeviceHeadingSnapshot) {
                    if (!isActive(activeGeneration) || !snapshot.isUsable()) return
                    val previousElapsed = latestHeadingElapsedRealtimeMillis
                    if (previousElapsed != null && snapshot.elapsedRealtimeMillis < previousElapsed) return
                    latestHeadingElapsedRealtimeMillis = snapshot.elapsedRealtimeMillis
                    val calibrationRequired = snapshot.conservativeErrorDegrees?.let {
                        !it.isFinite() || it >= COMPLETELY_UNKNOWN_HEADING_ERROR_DEGREES
                    } ?: true
                    val promptSequence = if (calibrationRequired && !state.calibrationRequired) {
                        state.calibrationPromptSequence + 1L
                    } else {
                        state.calibrationPromptSequence
                    }
                    scheduleHeadingLivenessCheck(
                        activeGeneration = activeGeneration,
                        expectedElapsedRealtimeMillis = snapshot.elapsedRealtimeMillis
                    )
                    publish(
                        state.copy(
                            headingDegrees = snapshot.headingDegrees,
                            calibrationRequired = calibrationRequired,
                            calibrationPromptSequence = promptSequence,
                            headingUnavailable = false
                        )
                    )
                }

                override fun onFailure() {
                    publishHeadingFailure(activeGeneration)
                }
            })
        }.getOrElse {
            publishHeadingFailure(activeGeneration)
            null
        }
    }

    override fun stop() {
        generation += 1L
        observer = null
        locationSubscription?.close()
        headingSubscription?.close()
        headingLivenessSubscription?.close()
        locationSubscription = null
        headingSubscription = null
        headingLivenessSubscription = null
        latestHeadingElapsedRealtimeMillis = null
        state = ForegroundLocationHeadingState()
    }

    override fun latestLocation(): CurrentLocationSnapshot? = state.location

    private fun publish(next: ForegroundLocationHeadingState) {
        state = next
        observer?.invoke(next)
    }

    private fun publishLocationFailure(expectedGeneration: Long) {
        if (!isActive(expectedGeneration) || state.locationUnavailable) return
        publish(
            state.copy(
                locationUnavailable = true,
                locationUnavailableSequence = state.locationUnavailableSequence + 1L
            )
        )
    }

    private fun scheduleHeadingLivenessCheck(
        activeGeneration: Long,
        expectedElapsedRealtimeMillis: Long?
    ) {
        headingLivenessSubscription?.close()
        headingLivenessSubscription = headingLivenessScheduler.schedule(
            HEADING_LIVENESS_TIMEOUT_MILLIS
        ) {
            if (
                !isActive(activeGeneration) ||
                latestHeadingElapsedRealtimeMillis != expectedElapsedRealtimeMillis
            ) return@schedule
            publishHeadingFailure(activeGeneration)
        }
    }

    private fun publishHeadingFailure(expectedGeneration: Long) {
        if (!isActive(expectedGeneration) || state.headingUnavailable) return
        headingLivenessSubscription?.close()
        headingLivenessSubscription = null
        publish(
            state.copy(
                headingDegrees = null,
                calibrationRequired = false,
                headingUnavailable = true,
                headingUnavailableSequence = state.headingUnavailableSequence + 1L
            )
        )
    }

    private fun isActive(expectedGeneration: Long): Boolean =
        generation == expectedGeneration && observer != null

    private fun CurrentLocationSnapshot.isUsable(): Boolean =
        latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0 &&
            elapsedRealtimeMillis >= 0L

    private fun DeviceHeadingSnapshot.isUsable(): Boolean =
        headingDegrees.isFinite() && headingDegrees >= 0f && headingDegrees < 360f &&
            headingErrorDegrees.isFinite() && headingErrorDegrees >= 0f &&
            elapsedRealtimeMillis >= 0L

    private companion object {
        const val COMPLETELY_UNKNOWN_HEADING_ERROR_DEGREES = 180f
        const val HEADING_LIVENESS_TIMEOUT_MILLIS = 2_000L
    }
}
