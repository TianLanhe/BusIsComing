package com.golink.busiscoming.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.golink.busiscoming.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapColorScheme
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions

data class RouteMapRenderPalette(
    val busColors: IntArray,
    @param:ColorInt val busOutlineColor: Int,
    @param:ColorInt val markerOutlineColor: Int,
    @param:ColorInt val walkingColor: Int,
    @param:ColorInt val originColor: Int,
    @param:ColorInt val destinationColor: Int,
    @param:ColorInt val selectedColor: Int
) {
    companion object {
        fun from(context: Context): RouteMapRenderPalette = RouteMapRenderPalette(
            busColors = intArrayOf(
                ContextCompat.getColor(context, R.color.route_leg_0),
                ContextCompat.getColor(context, R.color.route_leg_1),
                ContextCompat.getColor(context, R.color.route_leg_2),
                ContextCompat.getColor(context, R.color.route_leg_3)
            ),
            busOutlineColor = ContextCompat.getColor(context, R.color.route_map_bus_outline),
            markerOutlineColor = ContextCompat.getColor(context, R.color.route_map_marker_outline),
            walkingColor = ContextCompat.getColor(context, R.color.route_timeline_walk),
            originColor = ContextCompat.getColor(context, R.color.bus_chip_selected),
            destinationColor = ContextCompat.getColor(context, R.color.bus_danger),
            selectedColor = ContextCompat.getColor(context, R.color.route_map_selected)
        )
    }
}

class GoogleRouteMapRenderer(
    private val context: Context,
    private val map: GoogleMap,
    private val palette: RouteMapRenderPalette = RouteMapRenderPalette.from(context),
    private val onMarkerSelected: (String) -> Unit
) {
    private val markerIcons = RouteMapMarkerIconFactory(context, palette)
    private val markers = mutableMapOf<String, RenderedMarker>()
    private val lines = mutableMapOf<String, RenderedLine>()
    private var presentation: RouteMapPresentation? = null

    init {
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = false
            isMapToolbarEnabled = false
            isMyLocationButtonEnabled = false
            isIndoorLevelPickerEnabled = false
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
            isScrollGesturesEnabled = true
            isZoomGesturesEnabled = true
        }
        map.isTrafficEnabled = false
        map.setOnMarkerClickListener { marker ->
            val stableId = marker.tag as? String ?: return@setOnMarkerClickListener false
            marker.showInfoWindow()
            onMarkerSelected(stableId)
            true
        }
    }

    fun setDarkMode(darkMode: Boolean) {
        map.setMapColorScheme(if (darkMode) MapColorScheme.DARK else MapColorScheme.LIGHT)
    }

    fun render(value: RouteMapPresentation) {
        presentation = value
        val nextMarkerIds = value.markers.mapTo(mutableSetOf()) { it.stableId }
        (markers.keys - nextMarkerIds).forEach { id -> markers.remove(id)?.marker?.remove() }
        value.markers.forEach { model -> renderMarker(model) }

        val nextLineIds = value.lines.mapTo(mutableSetOf()) { it.stableId }
        (lines.keys - nextLineIds).forEach { id -> lines.remove(id)?.remove() }
        value.lines.forEach { model -> renderLine(model) }
    }

    fun updatePadding(left: Int, top: Int, right: Int, bottom: Int) {
        map.setPadding(left, top, right, bottom)
    }

    fun fitOverview(animated: Boolean, paddingPx: Int): Boolean {
        val points = presentation?.boundsPoints.orEmpty()
        if (points.isEmpty()) return false
        val bounds = LatLngBounds.builder().apply {
            points.forEach { include(it.toLatLng()) }
        }.build()
        val update = CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)
        if (animated) map.animateCamera(update) else map.moveCamera(update)
        return true
    }

    fun focusMarker(stableId: String, zoom: Float = 16f): Boolean {
        val marker = markers[stableId]?.marker ?: return false
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, zoom))
        marker.showInfoWindow()
        return true
    }

    fun focusCoordinate(coordinate: RouteMapCoordinate, zoom: Float = 16f) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(coordinate.toLatLng(), zoom))
    }

    @SuppressLint("MissingPermission")
    fun setMyLocationEnabled(enabled: Boolean): Boolean {
        return runCatching {
            map.isMyLocationEnabled = enabled
            true
        }.getOrDefault(false)
    }

    fun clear() {
        markers.values.forEach { it.marker.remove() }
        lines.values.forEach(RenderedLine::remove)
        markers.clear()
        lines.clear()
        presentation = null
    }

    private fun renderMarker(model: RouteMapMarker) {
        val rendered = markers[model.stableId]
        if (rendered?.model == model) return
        if (rendered == null) {
            val marker = map.addMarker(
                MarkerOptions()
                    .position(model.position.toLatLng())
                    .title(accessibleTitle(model))
                    .icon(markerIcons.icon(model))
                    .anchor(0.5f, 0.5f)
                    .zIndex(if (model.selected) 20f else 10f)
            ) ?: return
            marker.tag = model.stableId
            markers[model.stableId] = RenderedMarker(marker, model)
        } else {
            rendered.marker.position = model.position.toLatLng()
            rendered.marker.title = accessibleTitle(model)
            rendered.marker.setIcon(markerIcons.icon(model))
            rendered.marker.zIndex = if (model.selected) 20f else 10f
            markers[model.stableId] = RenderedMarker(rendered.marker, model)
        }
    }

    private fun renderLine(model: RouteMapLine) {
        val rendered = lines[model.stableId]
        if (rendered?.model == model) return
        rendered?.remove()
        val points = model.points.map(RouteMapCoordinate::toLatLng)
        if (points.size < 2) return
        lines[model.stableId] = when (model.kind) {
            RouteMapLineKind.BUS -> {
                val color = palette.busColors[(model.colorSlot ?: 0).mod(palette.busColors.size)]
                val outline = map.addPolyline(
                    PolylineOptions().addAll(points).color(palette.busOutlineColor).width(dp(9f)).zIndex(1f)
                )
                val core = map.addPolyline(
                    PolylineOptions().addAll(points).color(color).width(dp(6f)).zIndex(2f)
                )
                RenderedLine(model, core, outline)
            }
            RouteMapLineKind.WALKING -> {
                val core = map.addPolyline(
                    PolylineOptions()
                        .addAll(points)
                        .color(palette.walkingColor)
                        .width(dp(4f))
                        .pattern(listOf(Dot(), Gap(dp(7f)), Dash(dp(10f)), Gap(dp(7f))))
                        .zIndex(3f)
                )
                RenderedLine(model, core, null)
            }
        }
    }

    private fun accessibleTitle(marker: RouteMapMarker): String {
        val firstRoute = marker.routeLabels.firstOrNull().orEmpty()
        return when (marker.role) {
            RouteMapMarkerRole.QUERY_ORIGIN -> context.getString(R.string.route_map_marker_origin, marker.title)
            RouteMapMarkerRole.QUERY_DESTINATION -> context.getString(R.string.route_map_marker_destination, marker.title)
            RouteMapMarkerRole.BOARDING -> context.getString(R.string.route_map_marker_boarding, firstRoute, marker.title)
            RouteMapMarkerRole.VIA -> context.getString(R.string.route_map_marker_via, firstRoute, marker.title)
            RouteMapMarkerRole.ALIGHTING -> context.getString(R.string.route_map_marker_alighting, firstRoute, marker.title)
            RouteMapMarkerRole.TRANSFER -> context.getString(
                R.string.route_map_marker_transfer,
                marker.routeLabels.getOrNull(0).orEmpty(),
                marker.routeLabels.getOrNull(1).orEmpty(),
                marker.title
            )
        }
    }

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density

    private data class RenderedMarker(val marker: Marker, val model: RouteMapMarker)

    private data class RenderedLine(
        val model: RouteMapLine,
        val core: Polyline,
        val outline: Polyline?
    ) {
        fun remove() {
            core.remove()
            outline?.remove()
        }
    }
}

private class RouteMapMarkerIconFactory(
    context: Context,
    private val palette: RouteMapRenderPalette
) {
    private val density = context.resources.displayMetrics.density

    fun icon(marker: RouteMapMarker): BitmapDescriptor {
        val baseSize = if (marker.role == RouteMapMarkerRole.VIA) 14f else 30f
        val size = ((if (marker.selected) baseSize * 1.28f else baseSize) * density).toInt().coerceAtLeast(12)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = markerColor(marker)
        }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            color = palette.markerOutlineColor
        }
        val inset = 2.5f * density
        val left = inset
        val top = inset
        val right = size - inset
        val bottom = size - inset
        when (marker.role) {
            RouteMapMarkerRole.QUERY_ORIGIN -> {
                val path = Path().apply {
                    moveTo(size / 2f, top)
                    lineTo(right, bottom)
                    lineTo(left, bottom)
                    close()
                }
                canvas.drawPath(path, fill)
                canvas.drawPath(path, outline)
            }
            RouteMapMarkerRole.ALIGHTING,
            RouteMapMarkerRole.QUERY_DESTINATION -> {
                canvas.drawRoundRect(left, top, right, bottom, 4f * density, 4f * density, fill)
                canvas.drawRoundRect(left, top, right, bottom, 4f * density, 4f * density, outline)
            }
            RouteMapMarkerRole.TRANSFER -> {
                val path = Path().apply {
                    moveTo(size / 2f, top)
                    lineTo(right, size / 2f)
                    lineTo(size / 2f, bottom)
                    lineTo(left, size / 2f)
                    close()
                }
                canvas.drawPath(path, fill)
                canvas.drawPath(path, outline)
            }
            RouteMapMarkerRole.BOARDING,
            RouteMapMarkerRole.VIA -> {
                canvas.drawCircle(size / 2f, size / 2f, (right - left) / 2f, fill)
                canvas.drawCircle(size / 2f, size / 2f, (right - left) / 2f, outline)
            }
        }
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    @ColorInt
    private fun markerColor(marker: RouteMapMarker): Int {
        if (marker.selected) return palette.selectedColor
        return when (marker.role) {
            RouteMapMarkerRole.QUERY_ORIGIN -> palette.originColor
            RouteMapMarkerRole.QUERY_DESTINATION -> palette.destinationColor
            else -> palette.busColors[(marker.legIndexes.minOrNull() ?: 0).mod(palette.busColors.size)]
        }
    }
}

private fun RouteMapCoordinate.toLatLng(): LatLng = LatLng(latitude, longitude)
