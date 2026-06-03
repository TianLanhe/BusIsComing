package com.example.busiscomming.data.model

data class RouteConfig(
    val id: Long,
    val name: String,
    val origin: Place,
    val destination: Place
) {
    fun pathLabel(): String = "${origin.name} -> ${destination.name}"

    fun displayLabel(): String = "$name：${pathLabel()}"
}
