package com.golink.busiscoming.data.location

import com.golink.busiscoming.data.model.JourneyPosition
import com.golink.busiscoming.data.model.RouteJourneyAxis

enum class RouteDetailLocationPermission {
    GRANTED,
    REQUESTABLE,
    PERMANENTLY_DENIED
}

sealed interface RouteDetailLocationEffect {
    data object ShowPermissionSnackbar : RouteDetailLocationEffect
    data object RequestPermission : RouteDetailLocationEffect
    data object OpenAppSettings : RouteDetailLocationEffect
    data object ShowSystemLocationSnackbar : RouteDetailLocationEffect
    data object OpenSystemLocationSettings : RouteDetailLocationEffect
    data object ShowFirstFixTimeout : RouteDetailLocationEffect
    data class AutoExpandLeg(val legIndex: Int) : RouteDetailLocationEffect
    data class AnnouncePosition(val position: JourneyPosition) : RouteDetailLocationEffect
}

sealed interface RouteDetailLocationUiState {
    data object Inactive : RouteDetailLocationUiState
    data object WaitingPermission : RouteDetailLocationUiState
    data object WaitingFix : RouteDetailLocationUiState
    data object Hidden : RouteDetailLocationUiState

    data class Visible(
        val pageGeneration: Long,
        val position: JourneyPosition
    ) : RouteDetailLocationUiState
}

data class RouteDetailLocationSessionState(
    val permissionSnackbarShown: Boolean = false,
    val systemLocationSnackbarShown: Boolean = false,
    val firstFixTimeoutShown: Boolean = false,
    val interactionState: RouteCurrentPositionInteractionState =
        RouteCurrentPositionInteractionState()
)

class RouteDetailLocationController(
    private val pageGeneration: Long,
    private val source: ForegroundLocationSource,
    private val permission: () -> RouteDetailLocationPermission,
    private val systemLocationEnabled: () -> Boolean,
    private val scheduler: RouteDetailLocationScheduler,
    private val nowElapsedMillis: () -> Long,
    private val onState: (RouteDetailLocationUiState) -> Unit,
    private val onEffect: (RouteDetailLocationEffect) -> Unit,
    private val matcher: RouteJourneyPositionMatcher = RouteJourneyPositionMatcher(),
    private val stabilizer: RouteJourneyPositionStabilizer = RouteJourneyPositionStabilizer(),
    restoredSessionState: RouteDetailLocationSessionState? = null,
    private val interactionPolicy: RouteCurrentPositionInteractionPolicy =
        RouteCurrentPositionInteractionPolicy(restoredSessionState?.interactionState)
) {
    private var foreground = false
    private var sourceGeneration = 0L
    private var sourceSubscription: ForegroundLocationSubscription? = null
    private var timeoutSubscription: ForegroundLocationSubscription? = null
    private var staleFixSubscription: ForegroundLocationSubscription? = null
    private var axis: RouteJourneyAxis? = null
    private var latestFix: JourneyLocationFix? = null
    private var state: RouteDetailLocationUiState = RouteDetailLocationUiState.Inactive
    private var permissionSnackbarShown = restoredSessionState?.permissionSnackbarShown ?: false
    private var systemLocationSnackbarShown =
        restoredSessionState?.systemLocationSnackbarShown ?: false
    private var firstFixTimeoutShown = restoredSessionState?.firstFixTimeoutShown ?: false

    fun startForeground() {
        if (foreground) return
        foreground = true
        evaluateAvailability()
    }

    fun stopForeground() {
        if (!foreground) return
        foreground = false
        stopSource()
        emitState(RouteDetailLocationUiState.Inactive)
    }

    fun updateAxis(updatedAxis: RouteJourneyAxis?) {
        if (updatedAxis?.identity?.pageGeneration != null &&
            updatedAxis.identity.pageGeneration != pageGeneration
        ) return
        if (axis?.identity != updatedAxis?.identity) stabilizer.reset()
        axis = updatedAxis
        latestFix?.let(::publishFix)
    }

    fun onPermissionAction() {
        onEffect(
            if (permission() == RouteDetailLocationPermission.PERMANENTLY_DENIED) {
                RouteDetailLocationEffect.OpenAppSettings
            } else {
                RouteDetailLocationEffect.RequestPermission
            }
        )
    }

    fun onPermissionResult() {
        if (foreground) evaluateAvailability()
    }

    fun onReturnedFromSettings() {
        if (foreground) evaluateAvailability()
    }

    fun onSystemLocationAction() {
        onEffect(RouteDetailLocationEffect.OpenSystemLocationSettings)
    }

    fun onUserCollapsedLeg(legIndex: Int) {
        interactionPolicy.userCollapsedLeg(legIndex)
    }

    fun sessionState(): RouteDetailLocationSessionState = RouteDetailLocationSessionState(
        permissionSnackbarShown = permissionSnackbarShown,
        systemLocationSnackbarShown = systemLocationSnackbarShown,
        firstFixTimeoutShown = firstFixTimeoutShown,
        interactionState = interactionPolicy.state()
    )

    private fun evaluateAvailability() {
        when (permission()) {
            RouteDetailLocationPermission.GRANTED -> Unit
            RouteDetailLocationPermission.REQUESTABLE,
            RouteDetailLocationPermission.PERMANENTLY_DENIED -> {
                stopSource()
                emitState(RouteDetailLocationUiState.WaitingPermission)
                if (!permissionSnackbarShown) {
                    permissionSnackbarShown = true
                    onEffect(RouteDetailLocationEffect.ShowPermissionSnackbar)
                }
                return
            }
        }
        if (!systemLocationEnabled()) {
            stopSource()
            emitState(RouteDetailLocationUiState.Hidden)
            if (!systemLocationSnackbarShown) {
                systemLocationSnackbarShown = true
                onEffect(RouteDetailLocationEffect.ShowSystemLocationSnackbar)
            }
            return
        }
        if (sourceSubscription == null) startSource()
    }

    private fun startSource() {
        sourceGeneration += 1L
        val activeGeneration = sourceGeneration
        emitState(RouteDetailLocationUiState.WaitingFix)
        val onLocation: (JourneyLocationFix) -> Unit = location@{ fix ->
            if (!foreground || sourceGeneration != activeGeneration) return@location
            timeoutSubscription?.close()
            timeoutSubscription = null
            staleFixSubscription?.close()
            latestFix = fix
            publishFix(fix)
            val staleDelay = (
                fix.elapsedRealtimeMillis + RouteJourneyPositionMatcher.MAX_FIX_AGE_MILLIS -
                    nowElapsedMillis() + 1L
                ).coerceAtLeast(1L)
            staleFixSubscription = scheduler.schedule(staleDelay) {
                if (!foreground || sourceGeneration != activeGeneration || latestFix !== fix) {
                    return@schedule
                }
                latestFix = null
                stabilizer.reset()
                emitState(RouteDetailLocationUiState.Hidden)
            }
        }
        sourceSubscription = try {
            source.start(onLocation)
        } catch (_: SecurityException) {
            ForegroundLocationSubscription {}
        }
        if (!firstFixTimeoutShown) {
            timeoutSubscription = scheduler.schedule(FIRST_FIX_TIMEOUT_MILLIS) {
                if (!foreground || sourceGeneration != activeGeneration || latestFix != null) {
                    return@schedule
                }
                firstFixTimeoutShown = true
                onEffect(RouteDetailLocationEffect.ShowFirstFixTimeout)
            }
        }
    }

    private fun stopSource() {
        sourceGeneration += 1L
        sourceSubscription?.close()
        sourceSubscription = null
        timeoutSubscription?.close()
        timeoutSubscription = null
        staleFixSubscription?.close()
        staleFixSubscription = null
        latestFix = null
        stabilizer.reset()
    }

    private fun publishFix(fix: JourneyLocationFix) {
        val currentAxis = axis
        if (currentAxis == null) {
            emitState(RouteDetailLocationUiState.Hidden)
            return
        }
        val candidate = matcher.match(currentAxis, fix, nowElapsedMillis())
        val position = stabilizer.update(currentAxis, candidate, fix)
        if (position === JourneyPosition.Unreliable) {
            emitState(RouteDetailLocationUiState.Hidden)
            return
        }
        interactionPolicy.update(position).forEach { interaction ->
            onEffect(
                when (interaction) {
                    is RouteCurrentPositionInteraction.AutoExpandLeg ->
                        RouteDetailLocationEffect.AutoExpandLeg(interaction.legIndex)
                    is RouteCurrentPositionInteraction.Announce ->
                        RouteDetailLocationEffect.AnnouncePosition(interaction.position)
                }
            )
        }
        val current = state as? RouteDetailLocationUiState.Visible
        if (current != null && current.position.hasSameVisualPosition(position)) return
        emitState(RouteDetailLocationUiState.Visible(pageGeneration, position))
    }

    private fun emitState(updatedState: RouteDetailLocationUiState) {
        if (state == updatedState) return
        state = updatedState
        onState(updatedState)
    }

    private companion object {
        const val FIRST_FIX_TIMEOUT_MILLIS = 10_000L
    }
}

private fun JourneyPosition.hasSameVisualPosition(other: JourneyPosition): Boolean = when {
    this is JourneyPosition.AtNode && other is JourneyPosition.AtNode -> nodeId == other.nodeId
    this is JourneyPosition.BetweenNodes && other is JourneyPosition.BetweenNodes ->
        edgeId == other.edgeId
    this is JourneyPosition.WalkingProgress && other is JourneyPosition.WalkingProgress ->
        edgeId == other.edgeId && progress == other.progress
    else -> false
}
