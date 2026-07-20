package com.golink.busiscoming.ui.main

internal class SearchPlaceAttributionState(
    originUsesGoogleMaps: Boolean = false,
    destinationUsesGoogleMaps: Boolean = false
) {
    var originUsesGoogleMaps: Boolean = originUsesGoogleMaps
        private set

    var destinationUsesGoogleMaps: Boolean = destinationUsesGoogleMaps
        private set

    fun setOriginGoogleMaps(value: Boolean) {
        originUsesGoogleMaps = value
    }

    fun setDestinationGoogleMaps(value: Boolean) {
        destinationUsesGoogleMaps = value
    }

    fun clearOrigin() {
        originUsesGoogleMaps = false
    }

    fun clearDestination() {
        destinationUsesGoogleMaps = false
    }

    fun swap() {
        val previousOrigin = originUsesGoogleMaps
        originUsesGoogleMaps = destinationUsesGoogleMaps
        destinationUsesGoogleMaps = previousOrigin
    }
}
