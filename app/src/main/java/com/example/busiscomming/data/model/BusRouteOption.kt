package com.example.busiscomming.data.model

data class BusRouteOption(
    val routeName: String,
    val routeSegments: List<String>,
    val priceHkd: Double,
    val durationMinutes: Int,
    val arrivalMinutes: Int,
    val transferCount: Int,
    val walkingDistanceMeters: Int
)
