package com.example.busiscomming.data.model

data class RouteConfig(
    val id: Long,
    val name: String,
    val origin: String,
    val destination: String
) {
    fun displayLabel(): String = "$name：$origin -> $destination"
}
