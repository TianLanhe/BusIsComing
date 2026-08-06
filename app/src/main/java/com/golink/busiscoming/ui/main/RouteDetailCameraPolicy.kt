package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.RouteGeometryKey

enum class RouteDetailCameraMoveOrigin {
    GESTURE,
    PROGRAMMATIC
}

object RouteDetailCameraPolicy {
    const val HONG_KONG_LATITUDE = 22.3193
    const val HONG_KONG_LONGITUDE = 114.1694
    const val HONG_KONG_ZOOM = 10.5f

    fun initialCamera(saved: RouteDetailCameraSnapshot?): RouteDetailCameraSnapshot {
        return saved ?: RouteDetailCameraSnapshot(
            latitude = HONG_KONG_LATITUDE,
            longitude = HONG_KONG_LONGITUDE,
            zoom = HONG_KONG_ZOOM
        )
    }

    fun shouldAutoFit(
        hasReliableStructure: Boolean,
        owner: RouteDetailCameraOwner,
        initialFitDone: Boolean,
        geometryStates: Map<RouteGeometryKey, ProgressiveValue<*>>
    ): Boolean {
        if (!hasReliableStructure || owner != RouteDetailCameraOwner.PAGE || initialFitDone) return false
        return geometryStates.values.all { state ->
            state is ProgressiveValue.Success || state is ProgressiveValue.Failure
        }
    }

    fun ownerAfterMoveStarted(
        current: RouteDetailCameraOwner,
        origin: RouteDetailCameraMoveOrigin
    ): RouteDetailCameraOwner {
        return if (origin == RouteDetailCameraMoveOrigin.GESTURE) {
            RouteDetailCameraOwner.USER
        } else {
            current
        }
    }
}
