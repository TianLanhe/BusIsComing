package com.example.busiscoming.data.model

data class RouteConfigValidationResult(
    val nameError: String? = null,
    val originError: String? = null,
    val destinationError: String? = null
) {
    val isValid: Boolean
        get() = nameError == null && originError == null && destinationError == null
}

object RouteConfigValidator {
    fun validate(
        name: String,
        origin: Place?,
        destination: Place?
    ): RouteConfigValidationResult {
        val nameError = if (name.isBlank()) "必填" else null
        val originError = if (origin == null) "請選擇起點地點" else null
        val destinationError = if (destination == null) "請選擇終點地點" else null
        val samePlaceDestinationError = if (
            origin != null &&
            destination != null &&
            origin == destination
        ) {
            "起點和終點不能相同"
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
