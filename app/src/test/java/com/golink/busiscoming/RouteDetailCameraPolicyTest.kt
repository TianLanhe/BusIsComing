package com.golink.busiscoming

import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.ui.main.ProgressiveValue
import com.golink.busiscoming.ui.main.RouteDetailCameraMoveOrigin
import com.golink.busiscoming.ui.main.RouteDetailCameraOwner
import com.golink.busiscoming.ui.main.RouteDetailCameraPolicy
import com.golink.busiscoming.ui.main.RouteDetailCameraSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailCameraPolicyTest {
    private val first = RouteGeometryKey("A", 1, 2)
    private val second = RouteGeometryKey("B", 3, 4)

    @Test
    fun savedCameraWinsOtherwiseHongKongIsTheInitialCamera() {
        val saved = RouteDetailCameraSnapshot(22.28, 114.15, 15f, bearing = 2f, tilt = 3f)

        assertEquals(saved, RouteDetailCameraPolicy.initialCamera(saved))
        assertEquals(22.3193, RouteDetailCameraPolicy.initialCamera(null).latitude, 0.00001)
        assertEquals(114.1694, RouteDetailCameraPolicy.initialCamera(null).longitude, 0.00001)
        assertEquals(10.5f, RouteDetailCameraPolicy.initialCamera(null).zoom)
    }

    @Test
    fun autoFitWaitsForReliableStructureAndEveryGeometryTerminalIncludingFailure() {
        val pending = mapOf(
            first to ProgressiveValue.Success(Unit),
            second to ProgressiveValue.Loading
        )
        val terminal = mapOf(
            first to ProgressiveValue.Success(Unit),
            second to ProgressiveValue.Failure<Unit>(null, "local failure")
        )

        assertFalse(RouteDetailCameraPolicy.shouldAutoFit(false, RouteDetailCameraOwner.PAGE, false, terminal))
        assertFalse(RouteDetailCameraPolicy.shouldAutoFit(true, RouteDetailCameraOwner.PAGE, true, terminal))
        assertFalse(RouteDetailCameraPolicy.shouldAutoFit(true, RouteDetailCameraOwner.PAGE, false, pending))
        assertTrue(RouteDetailCameraPolicy.shouldAutoFit(true, RouteDetailCameraOwner.PAGE, false, terminal))
        assertFalse(RouteDetailCameraPolicy.shouldAutoFit(true, RouteDetailCameraOwner.USER, false, terminal))
    }

    @Test
    fun onlyGestureTransfersCameraOwnershipToUser() {
        assertEquals(
            RouteDetailCameraOwner.USER,
            RouteDetailCameraPolicy.ownerAfterMoveStarted(
                RouteDetailCameraOwner.PAGE,
                RouteDetailCameraMoveOrigin.GESTURE
            )
        )
        assertEquals(
            RouteDetailCameraOwner.PAGE,
            RouteDetailCameraPolicy.ownerAfterMoveStarted(
                RouteDetailCameraOwner.PAGE,
                RouteDetailCameraMoveOrigin.PROGRAMMATIC
            )
        )
    }
}
