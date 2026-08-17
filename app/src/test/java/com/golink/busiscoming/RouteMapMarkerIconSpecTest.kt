package com.golink.busiscoming

import com.golink.busiscoming.ui.main.RouteMapMarkerIconSpec
import com.golink.busiscoming.ui.main.RouteMapMarkerRole
import com.golink.busiscoming.ui.main.RouteMapMarkerSector
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteMapMarkerIconSpecTest {
    @Test
    fun currentLocationUsesFixedTwentyFourDpSizeWithExistingShapeProportions() {
        assertEquals(
            RouteMapMarkerIconSpec.CurrentLocationGeometry(24, 2f, 8f / 3f),
            RouteMapMarkerIconSpec.currentLocationGeometry(density = 1f)
        )
        assertEquals(
            RouteMapMarkerIconSpec.CurrentLocationGeometry(48, 4f, 16f / 3f),
            RouteMapMarkerIconSpec.currentLocationGeometry(density = 2f)
        )
    }

    @Test
    fun routeMarkerSizesAndSelectedScaleRemainUnchanged() {
        assertEquals(14, RouteMapMarkerIconSpec.markerSizePx(RouteMapMarkerRole.VIA, false, 1f))
        assertEquals(
            36,
            RouteMapMarkerIconSpec.markerSizePx(RouteMapMarkerRole.QUERY_ORIGIN, false, 1f)
        )
        assertEquals(
            36,
            RouteMapMarkerIconSpec.markerSizePx(RouteMapMarkerRole.QUERY_DESTINATION, false, 1f)
        )
        assertEquals(32, RouteMapMarkerIconSpec.markerSizePx(RouteMapMarkerRole.BOARDING, false, 1f))
        assertEquals(32, RouteMapMarkerIconSpec.markerSizePx(RouteMapMarkerRole.ALIGHTING, false, 1f))
        assertEquals(32, RouteMapMarkerIconSpec.markerSizePx(RouteMapMarkerRole.TRANSFER, false, 1f))
        assertEquals(37, RouteMapMarkerIconSpec.markerSizePx(RouteMapMarkerRole.TRANSFER, true, 1f))
    }

    @Test
    fun transferUsesTwoSolidSemicirclesAndContrastOutlineAndGlyph() {
        val previousColor = 0xFF176B5B.toInt()
        val nextColor = 0xFF315F9D.toInt()
        val contrastColor = 0xFFFFFFFF.toInt()

        assertEquals(
            RouteMapMarkerIconSpec.TransferStyle(
                fillSectors = listOf(
                    RouteMapMarkerSector(-90f, 180f, previousColor),
                    RouteMapMarkerSector(90f, 180f, nextColor)
                ),
                outlineColor = contrastColor,
                glyphColor = contrastColor
            ),
            RouteMapMarkerIconSpec.transferStyle(previousColor, nextColor, contrastColor)
        )
    }
}
