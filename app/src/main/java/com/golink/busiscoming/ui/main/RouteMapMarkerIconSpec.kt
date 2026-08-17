package com.golink.busiscoming.ui.main

internal data class RouteMapMarkerSector(
    val startAngleDegrees: Float,
    val sweepAngleDegrees: Float,
    val color: Int
)

internal object RouteMapMarkerIconSpec {
    data class CurrentLocationGeometry(
        val sizePx: Int,
        val insetPx: Float,
        val outlineStrokePx: Float
    )

    data class TransferStyle(
        val fillSectors: List<RouteMapMarkerSector>,
        val outlineColor: Int,
        val glyphColor: Int
    )

    fun currentLocationGeometry(density: Float): CurrentLocationGeometry =
        CurrentLocationGeometry(
            sizePx = (CURRENT_LOCATION_SIZE_DP * density).toInt().coerceAtLeast(1),
            insetPx = CURRENT_LOCATION_INSET_DP * density,
            outlineStrokePx = CURRENT_LOCATION_OUTLINE_STROKE_DP * density
        )

    fun markerSizePx(role: RouteMapMarkerRole, selected: Boolean, density: Float): Int {
        val baseSize = when (role) {
            RouteMapMarkerRole.VIA -> 14f
            RouteMapMarkerRole.QUERY_ORIGIN,
            RouteMapMarkerRole.QUERY_DESTINATION -> 36f
            else -> 32f
        }
        return ((if (selected) baseSize * SELECTED_SCALE else baseSize) * density)
            .toInt()
            .coerceAtLeast(12)
    }

    fun transferStyle(
        previousColor: Int,
        nextColor: Int,
        contrastColor: Int
    ): TransferStyle = TransferStyle(
        fillSectors = listOf(
            RouteMapMarkerSector(-90f, 180f, previousColor),
            RouteMapMarkerSector(90f, 180f, nextColor)
        ),
        outlineColor = contrastColor,
        glyphColor = contrastColor
    )

    private const val CURRENT_LOCATION_SIZE_DP = 24f
    private const val CURRENT_LOCATION_INSET_DP = 2f
    private const val CURRENT_LOCATION_OUTLINE_STROKE_DP = 8f / 3f
    private const val SELECTED_SCALE = 1.18f
}
