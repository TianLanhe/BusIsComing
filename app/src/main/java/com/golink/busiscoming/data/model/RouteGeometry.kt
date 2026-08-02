package com.golink.busiscoming.data.model

data class RouteGeometryPoint(
    val pointId: String,
    val latitude: Double,
    val longitude: Double
)

data class RouteGeometryCoordinate(
    val latitude: Double,
    val longitude: Double
)

data class RouteGeometryKey(
    val routeVariant: String,
    val boardingSeq: Int,
    val alightingSeq: Int
) {
    val isValid: Boolean
        get() = routeVariant.isNotBlank() && boardingSeq <= alightingSeq
}

data class RouteGeometrySegment(
    val key: RouteGeometryKey,
    val points: List<RouteGeometryPoint>
)
