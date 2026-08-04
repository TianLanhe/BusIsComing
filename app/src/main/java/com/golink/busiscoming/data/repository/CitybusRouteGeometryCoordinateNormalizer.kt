package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.RouteGeometryPoint

/** 把 getlinep2p.php 的 Citybus 舊底圖路線坐標對齊至 Google Maps 使用的 WGS84。 */
object CitybusRouteGeometryCoordinateNormalizer {
    fun toWgs84(points: List<RouteGeometryPoint>): List<RouteGeometryPoint> {
        return points.map { point ->
            point.copy(
                latitude = point.latitude - LEGACY_BASEMAP_LATITUDE_OFFSET,
                longitude = point.longitude - LEGACY_BASEMAP_LONGITUDE_OFFSET
            )
        }
    }

    // Citybus mobile 舊底圖用這組偏移把 WGS84 站點轉成自家底圖坐標。
    private const val LEGACY_BASEMAP_LATITUDE_OFFSET = -0.0001935197
    private const val LEGACY_BASEMAP_LONGITUDE_OFFSET = 0.0000697374
}
