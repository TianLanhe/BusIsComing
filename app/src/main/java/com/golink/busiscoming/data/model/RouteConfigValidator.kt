package com.golink.busiscoming.data.model

data class RouteConfigValidationResult(
    val nameError: RouteConfigValidationError? = null,
    val originError: RouteConfigValidationError? = null,
    val destinationError: RouteConfigValidationError? = null
) {
    val isValid: Boolean
        get() = nameError == null && originError == null && destinationError == null
}

enum class RouteConfigValidationError {
    REQUIRED,
    ORIGIN_REQUIRED,
    DESTINATION_REQUIRED,
    SAME_PLACES
}

object RouteConfigValidator {
    fun validate(
        name: String,
        origin: Place?,
        destination: Place?
    ): RouteConfigValidationResult {
        val nameError = if (name.isBlank()) RouteConfigValidationError.REQUIRED else null
        val originError = if (origin == null) RouteConfigValidationError.ORIGIN_REQUIRED else null
        val destinationError = if (destination == null) {
            RouteConfigValidationError.DESTINATION_REQUIRED
        } else {
            null
        }
        val samePlaceDestinationError = if (
            origin != null &&
            destination != null &&
            origin == destination
        ) {
            RouteConfigValidationError.SAME_PLACES
        } else {
            destinationError
        }

        return RouteConfigValidationResult(
            nameError = nameError,
            originError = originError,
            destinationError = samePlaceDestinationError
        )
    }
}
