package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.RouteGeometryPoint

enum class RouteGeometryFailureKind {
    EMPTY_RESPONSE,
    INSUFFICIENT_POINTS,
    MALFORMED_COORDINATES,
    ENDPOINT_MISMATCH
}

class CitybusRouteGeometryParseException(
    message: String,
    val failureKind: RouteGeometryFailureKind = RouteGeometryFailureKind.MALFORMED_COORDINATES
) : IllegalArgumentException(message)

object RouteGeometryFailurePolicy {
    fun shouldAutoRetry(throwable: Throwable): Boolean {
        return when (throwable) {
            is java.io.IOException -> true
            is CitybusRouteGeometryParseException -> throwable.failureKind == RouteGeometryFailureKind.EMPTY_RESPONSE ||
                throwable.failureKind == RouteGeometryFailureKind.INSUFFICIENT_POINTS
            else -> false
        }
    }
}

object CitybusRouteGeometryParser {
    fun parse(response: String): List<RouteGeometryPoint> {
        val nonBlankLines = response.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        if (nonBlankLines.isEmpty()) {
            throw CitybusRouteGeometryParseException(
                "Route geometry response is empty",
                RouteGeometryFailureKind.EMPTY_RESPONSE
            )
        }
        val parsedRows = nonBlankLines.map(::parseLine)
        val points = parsedRows.mapNotNull { it }
        if (points.size < 2) {
            val kind = if (parsedRows.any { it == null }) {
                RouteGeometryFailureKind.MALFORMED_COORDINATES
            } else {
                RouteGeometryFailureKind.INSUFFICIENT_POINTS
            }
            throw CitybusRouteGeometryParseException("Route geometry needs at least two valid points", kind)
        }
        return points
    }

    private fun parseLine(line: String): RouteGeometryPoint? {
        val fields = line.trim().split(',')
        if (fields.size < 3) return null
        val pointId = fields[0].trim().takeIf { it.isNotBlank() } ?: return null
        val latitude = fields[1].trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 }
            ?: return null
        val longitude = fields[2].trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 }
            ?: return null
        return RouteGeometryPoint(pointId, latitude, longitude)
    }
}
