package com.example.busiscoming.data.model

data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double
) {
    override fun toString(): String = name
}
