package com.example.busiscoming.data.model

data class RouteConfig(
    val id: Long,
    val name: String,
    val origin: Place,
    val destination: Place,
    val usageCount: Int = 0,
    val lastUsedAt: Long? = null
) {
    fun pathLabel(): String = "${origin.name} -> ${destination.name}"

    fun displayLabel(): String = "$name：${pathLabel()}"
}
