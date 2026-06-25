package com.example.busiscoming.data.location

import com.example.busiscoming.data.model.Place

sealed class CurrentPlaceSelectionResult {
    data class Success(
        val place: Place,
        val snapshot: CurrentLocationSnapshot,
        val attribution: PlaceAttribution? = null
    ) : CurrentPlaceSelectionResult()

    data object Failure : CurrentPlaceSelectionResult()
}
