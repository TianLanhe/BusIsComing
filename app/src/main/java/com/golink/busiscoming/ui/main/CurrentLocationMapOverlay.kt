package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.location.ForegroundLocationHeadingState

data class CurrentLocationMapOverlay(
    val coordinate: RouteMapCoordinate,
    val accuracyMeters: Float?,
    val headingDegrees: Float?
) {
    companion object {
        fun from(state: ForegroundLocationHeadingState): CurrentLocationMapOverlay? {
            val location = state.location ?: return null
            return CurrentLocationMapOverlay(
                coordinate = RouteMapCoordinate(location.latitude, location.longitude),
                accuracyMeters = state.location.accuracyMeters?.takeIf { it.isFinite() && it >= 0f },
                headingDegrees = state.headingDegrees
            )
        }
    }
}
