package com.golink.busiscoming.data.location

import com.golink.busiscoming.data.model.JourneyCoordinate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class JourneyProjection(
    val coordinate: JourneyCoordinate,
    val distanceMeters: Double,
    val distanceAlongMeters: Double,
    val segmentIndex: Int,
    val segmentFraction: Double
)

internal object JourneyGeometry {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(first: JourneyCoordinate, second: JourneyCoordinate): Double {
        val lat1 = first.latitude.toRadians()
        val lat2 = second.latitude.toRadians()
        val deltaLat = lat2 - lat1
        val deltaLon = (second.longitude - first.longitude).toRadians()
        val a = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
        return EARTH_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(max(0.0, 1.0 - a)))
    }

    fun pathLengthMeters(points: List<JourneyCoordinate>): Double =
        points.zipWithNext().sumOf { (first, second) -> distanceMeters(first, second) }

    fun project(point: JourneyCoordinate, polyline: List<JourneyCoordinate>): JourneyProjection? {
        if (!point.isValidWgs84 || polyline.size < 2 || polyline.any { !it.isValidWgs84 }) return null
        var travelled = 0.0
        var best: JourneyProjection? = null
        polyline.zipWithNext().forEachIndexed { index, (start, end) ->
            val segmentLength = distanceMeters(start, end)
            if (segmentLength <= 0.0) return@forEachIndexed
            val projection = projectOnSegment(point, start, end, travelled, segmentLength, index)
            if (best == null || projection.distanceMeters < requireNotNull(best).distanceMeters) {
                best = projection
            }
            travelled += segmentLength
        }
        return best
    }

    fun projections(point: JourneyCoordinate, polyline: List<JourneyCoordinate>): List<JourneyProjection> {
        if (!point.isValidWgs84 || polyline.size < 2 || polyline.any { !it.isValidWgs84 }) return emptyList()
        var travelled = 0.0
        return buildList {
            polyline.zipWithNext().forEachIndexed { index, (start, end) ->
                val segmentLength = distanceMeters(start, end)
                if (segmentLength > 0.0) {
                    add(projectOnSegment(point, start, end, travelled, segmentLength, index))
                    travelled += segmentLength
                }
            }
        }
    }

    fun slice(
        polyline: List<JourneyCoordinate>,
        start: JourneyProjection,
        end: JourneyProjection
    ): List<JourneyCoordinate> {
        if (end.distanceAlongMeters < start.distanceAlongMeters) return emptyList()
        if (start.segmentIndex == end.segmentIndex) {
            return listOf(start.coordinate, end.coordinate).distinct()
        }
        return buildList {
            add(start.coordinate)
            for (vertexIndex in (start.segmentIndex + 1)..end.segmentIndex) {
                add(polyline[vertexIndex])
            }
            add(end.coordinate)
        }.distinct()
    }

    private fun projectOnSegment(
        point: JourneyCoordinate,
        start: JourneyCoordinate,
        end: JourneyCoordinate,
        travelled: Double,
        segmentLength: Double,
        segmentIndex: Int
    ): JourneyProjection {
        val referenceLatitude = ((start.latitude + end.latitude + point.latitude) / 3.0).toRadians()
        val startX = longitudeMeters(start.longitude, referenceLatitude)
        val startY = latitudeMeters(start.latitude)
        val endX = longitudeMeters(end.longitude, referenceLatitude)
        val endY = latitudeMeters(end.latitude)
        val pointX = longitudeMeters(point.longitude, referenceLatitude)
        val pointY = latitudeMeters(point.latitude)
        val deltaX = endX - startX
        val deltaY = endY - startY
        val denominator = deltaX * deltaX + deltaY * deltaY
        val fraction = if (denominator == 0.0) 0.0 else {
            ((pointX - startX) * deltaX + (pointY - startY) * deltaY) / denominator
        }.coerceIn(0.0, 1.0)
        val coordinate = JourneyCoordinate(
            latitude = start.latitude + (end.latitude - start.latitude) * fraction,
            longitude = start.longitude + (end.longitude - start.longitude) * fraction
        )
        return JourneyProjection(
            coordinate = coordinate,
            distanceMeters = distanceMeters(point, coordinate),
            distanceAlongMeters = travelled + segmentLength * fraction,
            segmentIndex = segmentIndex,
            segmentFraction = fraction
        )
    }

    private fun longitudeMeters(longitude: Double, referenceLatitude: Double): Double =
        longitude.toRadians() * EARTH_RADIUS_METERS * cos(referenceLatitude)

    private fun latitudeMeters(latitude: Double): Double = latitude.toRadians() * EARTH_RADIUS_METERS

    private fun Double.toRadians(): Double = this * PI / 180.0
}
