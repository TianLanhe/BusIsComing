package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.RouteGeometryPoint

class CitybusRouteGeometryParseException(message: String) : IllegalArgumentException(message)

object CitybusRouteGeometryParser {
    fun parse(response: String): List<RouteGeometryPoint> {
        val points = response.lineSequence().mapNotNull(::parseLine).toList()
        if (points.size < 2) {
            throw CitybusRouteGeometryParseException("Route geometry needs at least two valid points")
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
