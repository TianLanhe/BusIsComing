package com.golink.busiscoming.ui.main

import com.golink.busiscoming.ui.common.PlaceInputMessage

internal enum class SearchFieldValidation {
    MISSING_PLACE,
    SAME_AS_ORIGIN
}

internal enum class SearchFieldCaptionStatus {
    INSTRUCTION,
    GOOGLE_MAPS,
    NO_MATCHES,
    SEARCH_FAILED,
    LOCATION_FAILURE,
    MISSING_PLACE,
    SAME_AS_ORIGIN;

    val isError: Boolean
        get() = this == SEARCH_FAILED ||
            this == MISSING_PLACE ||
            this == SAME_AS_ORIGIN
}

internal class SearchFieldCaptionState {
    private var inputMessage = PlaceInputMessage.INSTRUCTION
    private var usesGoogleMaps = false
    private var locationFailure = false
    private var validation: SearchFieldValidation? = null

    fun onPlaceInputMessage(message: PlaceInputMessage) {
        inputMessage = message
        locationFailure = false
        validation = null
    }

    fun setGoogleMaps(value: Boolean) {
        usesGoogleMaps = value
    }

    fun setLocationFailure(value: Boolean) {
        locationFailure = value
    }

    fun setValidation(value: SearchFieldValidation?) {
        validation = value
    }

    fun visibleStatus(): SearchFieldCaptionStatus? =
        when (validation) {
            SearchFieldValidation.MISSING_PLACE -> SearchFieldCaptionStatus.MISSING_PLACE
            SearchFieldValidation.SAME_AS_ORIGIN -> SearchFieldCaptionStatus.SAME_AS_ORIGIN
            null -> when (inputMessage) {
                PlaceInputMessage.SEARCH_FAILED -> SearchFieldCaptionStatus.SEARCH_FAILED
                PlaceInputMessage.NO_MATCHES -> SearchFieldCaptionStatus.NO_MATCHES
                else -> when {
                    locationFailure -> SearchFieldCaptionStatus.LOCATION_FAILURE
                    usesGoogleMaps -> SearchFieldCaptionStatus.GOOGLE_MAPS
                    inputMessage == PlaceInputMessage.INSTRUCTION ->
                        SearchFieldCaptionStatus.INSTRUCTION
                    else -> null
                }
            }
        }
}
