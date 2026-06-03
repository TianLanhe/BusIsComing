package com.example.busiscomming.data.model

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
        val originError = if (origin == null) "请选择起点地点" else null
        val destinationError = if (destination == null) "请选择终点地点" else null
        val samePlaceDestinationError = if (
            origin != null &&
            destination != null &&
            origin == destination
        ) {
            "起点和终点不能相同"
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
