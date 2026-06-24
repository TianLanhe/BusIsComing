package com.example.busiscoming.data.location

data class CurrentLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val elapsedRealtimeMillis: Long
)

sealed class CurrentLocationResult {
    data class Success(val snapshot: CurrentLocationSnapshot) : CurrentLocationResult()
    data object NoPermission : CurrentLocationResult()
    data object Timeout : CurrentLocationResult()
    data object Unavailable : CurrentLocationResult()
}
