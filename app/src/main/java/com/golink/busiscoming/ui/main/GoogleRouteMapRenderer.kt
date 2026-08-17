package com.golink.busiscoming.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.golink.busiscoming.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapColorScheme
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlin.math.ceil

data class RouteMapRenderPalette(
    val busColors: IntArray,
    @param:ColorInt val busOutlineColor: Int,
    @param:ColorInt val markerOutlineColor: Int,
    @param:ColorInt val walkingColor: Int,
    @param:ColorInt val originColor: Int,
    @param:ColorInt val destinationColor: Int,
    @param:ColorInt val selectedColor: Int,
    @param:ColorInt val currentLocationColor: Int,
    @param:ColorInt val currentLocationAccuracyFillColor: Int,
    @param:ColorInt val currentLocationAccuracyStrokeColor: Int
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
            selectedColor = ContextCompat.getColor(context, R.color.route_map_selected),
            currentLocationColor = ContextCompat.getColor(context, R.color.route_map_current_location),
            currentLocationAccuracyFillColor = ContextCompat.getColor(
                context,
                R.color.route_map_current_location_accuracy_fill
            ),
            currentLocationAccuracyStrokeColor = ContextCompat.getColor(
                context,
                R.color.route_map_current_location_accuracy_stroke
            )
        )
    }
}

internal data class RouteMapRendererPerformanceSnapshot(
    val directionRelayouts: Int,
    val labelRelayouts: Int,
    val activeDirectionMarkers: Int
)

internal data class CurrentLocationRenderSnapshot(
    val hasDirectionMarker: Boolean,
    val hasAccuracyArea: Boolean,
    val coordinate: RouteMapCoordinate?,
    val headingDegrees: Float?
)

class GoogleRouteMapRenderer(
    private val context: Context,
    private val map: GoogleMap,
    private val palette: RouteMapRenderPalette = RouteMapRenderPalette.from(context),
    private val onMarkerSelected: (String) -> Unit
) {
    private val markerIcons = RouteMapMarkerIconFactory(context, palette)
    private val density = context.resources.displayMetrics.density
    private val busDirectionStyle = RouteMapDirectionStyle.bus(density)
    private val walkingDirectionStyle = RouteMapDirectionStyle.walking(density)
    private val busDirectionIcon by lazy {
        createDirectionArrowIcon(
            busDirectionStyle.glyphWidth,
            busDirectionStyle.glyphHeight,
            busDirectionStyle.strokeWidth,
            palette.busOutlineColor
        )
    }
    private val walkingDirectionIcon by lazy {
        createDirectionArrowIcon(
            walkingDirectionStyle.glyphWidth,
            walkingDirectionStyle.glyphHeight,
            walkingDirectionStyle.strokeWidth,
            palette.walkingColor
        )
    }
    private val currentLocationIcon by lazy { createCurrentLocationIcon() }
    private val markers = mutableMapOf<String, RenderedMarker>()
    private val labelMarkers = mutableMapOf<String, Marker>()
    private val labelSides = mutableMapOf<String, RouteMapLabelSide>()
    private val lines = mutableMapOf<String, RenderedLine>()
    private var presentation: RouteMapPresentation? = null
    private var paddingLeft = 0
    private var paddingTop = 0
    private var paddingRight = 0
    private var paddingBottom = 0
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var reservedLabelRects: List<RouteMapLabelRect> = emptyList()
    private var viewportTransitionActive = false
    private var labelAlpha = 1f
    private var labelAnimator: ValueAnimator? = null
    private var currentLocationHeadingAnimator: ValueAnimator? = null
    private var currentLocationMarker: Marker? = null
    private var currentLocationAccuracyCircle: Circle? = null
    private var currentLocationCoordinate: RouteMapCoordinate? = null
    private var renderedCurrentLocationHeading: Float? = null
    private var directionRelayoutCount = 0
    private var labelRelayoutCount = 0

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
            val stableId = marker.tag as? String ?: return@setOnMarkerClickListener true
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
        if (!viewportTransitionActive) {
            relayoutDirectionArrows()
            relayoutLabels()
        }
    }

    fun updatePadding(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        reservedLabelRects: List<RouteMapLabelRect> = emptyList()
    ) {
        val paddingChanged = paddingLeft != left || paddingTop != top ||
            paddingRight != right || paddingBottom != bottom
        val viewportChanged = this.viewportWidth != viewportWidth || this.viewportHeight != viewportHeight
        val reservedChanged = this.reservedLabelRects != reservedLabelRects
        if (!paddingChanged && !viewportChanged && !reservedChanged) return
        paddingLeft = left
        paddingTop = top
        paddingRight = right
        paddingBottom = bottom
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        this.reservedLabelRects = reservedLabelRects
        if (paddingChanged) map.setPadding(left, top, right, bottom)
        if (!viewportTransitionActive) {
            relayoutDirectionArrows()
            relayoutLabels()
        }
    }

    fun onCameraIdle() {
        if (viewportTransitionActive) return
        relayoutDirectionArrows()
        relayoutLabels()
    }

    fun beginViewportTransition() {
        if (viewportTransitionActive) return
        viewportTransitionActive = true
        labelAnimator?.cancel()
        labelAnimator = null
        labelAlpha = 0f
        labelMarkers.values.forEach { it.alpha = 0f }
    }

    fun endViewportTransition() {
        if (!viewportTransitionActive) return
        viewportTransitionActive = false
        relayoutDirectionArrows()
        relayoutLabels()
        labelAnimator?.cancel()
        labelAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = LABEL_FADE_DURATION_MILLIS
            addUpdateListener { animator ->
                labelAlpha = animator.animatedValue as Float
                labelMarkers.values.forEach { it.alpha = labelAlpha }
            }
            start()
        }
    }

    fun hasRenderedWalkingPaths(): Boolean = lines.values.any {
        it.model.kind == RouteMapLineKind.WALKING
    }

    internal fun performanceSnapshot() = RouteMapRendererPerformanceSnapshot(
        directionRelayouts = directionRelayoutCount,
        labelRelayouts = labelRelayoutCount,
        activeDirectionMarkers = lines.values.sumOf { it.arrows.size }
    )

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

    fun renderCurrentLocation(value: CurrentLocationMapOverlay?) {
        if (value == null) {
            clearCurrentLocation()
            return
        }
        currentLocationCoordinate = value.coordinate
        val position = value.coordinate.toLatLng()
        renderCurrentLocationAccuracy(position, value.accuracyMeters)
        renderCurrentLocationDirection(position, value.headingDegrees)
    }

    fun clearCurrentLocation() {
        currentLocationHeadingAnimator?.cancel()
        currentLocationMarker?.remove()
        currentLocationMarker = null
        currentLocationAccuracyCircle?.remove()
        currentLocationAccuracyCircle = null
        currentLocationCoordinate = null
        renderedCurrentLocationHeading = null
    }

    internal fun currentLocationSnapshot() = CurrentLocationRenderSnapshot(
        hasDirectionMarker = currentLocationMarker != null,
        hasAccuracyArea = currentLocationAccuracyCircle != null,
        coordinate = currentLocationCoordinate,
        headingDegrees = renderedCurrentLocationHeading?.let(::normalizeDegrees)
    )

    private fun renderCurrentLocationAccuracy(position: LatLng, accuracyMeters: Float?) {
        if (accuracyMeters == null) {
            currentLocationAccuracyCircle?.remove()
            currentLocationAccuracyCircle = null
            return
        }
        val circle = currentLocationAccuracyCircle
        if (circle == null) {
            currentLocationAccuracyCircle = map.addCircle(
                CircleOptions()
                    .center(position)
                    .radius(accuracyMeters.toDouble())
                    .fillColor(palette.currentLocationAccuracyFillColor)
                    .strokeColor(palette.currentLocationAccuracyStrokeColor)
                    .strokeWidth(dp(1.25f))
                    .clickable(false)
                    .zIndex(29f)
            )
        } else {
            circle.center = position
            circle.radius = accuracyMeters.toDouble()
        }
    }

    private fun renderCurrentLocationDirection(position: LatLng, headingDegrees: Float?) {
        if (headingDegrees == null) {
            currentLocationHeadingAnimator?.cancel()
            currentLocationMarker?.remove()
            currentLocationMarker = null
            renderedCurrentLocationHeading = null
            return
        }
        val marker = currentLocationMarker
        if (marker == null) {
            currentLocationMarker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(context.getString(R.string.route_map_current_location_marker))
                    .icon(currentLocationIcon)
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .rotation(headingDegrees)
                    .zIndex(30f)
            )
            renderedCurrentLocationHeading = headingDegrees
            return
        }
        marker.position = position
        val from = renderedCurrentLocationHeading ?: marker.rotation
        val target = HeadingRotation.shortestTarget(from, headingDegrees)
        if (target == from) return
        val animator = currentLocationHeadingAnimator ?: ValueAnimator().apply {
            duration = CURRENT_LOCATION_HEADING_ANIMATION_MILLIS
            addUpdateListener { runningAnimator ->
                val animatedHeading = runningAnimator.animatedValue as Float
                renderedCurrentLocationHeading = animatedHeading
                currentLocationMarker?.rotation = animatedHeading
            }
        }.also {
            currentLocationHeadingAnimator = it
        }
        animator.cancel()
        animator.setFloatValues(from, target)
        animator.start()
    }

    fun clear() {
        labelAnimator?.cancel()
        labelAnimator = null
        clearCurrentLocation()
        markers.values.forEach { it.marker.remove() }
        labelMarkers.values.forEach(Marker::remove)
        lines.values.forEach(RenderedLine::remove)
        markers.clear()
        labelMarkers.clear()
        labelSides.clear()
        lines.clear()
        presentation = null
    }

    private fun createCurrentLocationIcon(): BitmapDescriptor {
        val geometry = RouteMapMarkerIconSpec.currentLocationGeometry(density)
        val size = geometry.sizePx
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = geometry.insetPx
        val centerX = size / 2f
        val path = Path().apply {
            moveTo(centerX, inset)
            lineTo(size - inset, size - inset)
            lineTo(centerX, size * 0.70f)
            lineTo(inset, size - inset)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = geometry.outlineStrokePx
            color = palette.markerOutlineColor
        })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = palette.currentLocationColor
        })
        return BitmapDescriptorFactory.fromBitmap(bitmap)
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
                    .anchor(
                        0.5f,
                        if (
                            model.role == RouteMapMarkerRole.QUERY_ORIGIN ||
                            model.role == RouteMapMarkerRole.QUERY_DESTINATION
                        ) 1f else 0.5f
                    )
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
        val points = model.points.map(RouteMapCoordinate::toLatLng)
        if (points.size < 2) {
            lines.remove(model.stableId)?.remove()
            return
        }
        if (rendered != null && rendered.model.kind == model.kind) {
            rendered.model = model
            rendered.outline?.points = points
            rendered.core?.points = points
            if (model.kind == RouteMapLineKind.BUS) {
                rendered.core?.color = palette.busColors[(model.colorSlot ?: 0).mod(palette.busColors.size)]
            }
            return
        }
        rendered?.remove()
        lines[model.stableId] = when (model.kind) {
            RouteMapLineKind.BUS -> {
                val color = palette.busColors[(model.colorSlot ?: 0).mod(palette.busColors.size)]
                val outline = map.addPolyline(
                    PolylineOptions().addAll(points).color(palette.busOutlineColor).width(dp(9f)).zIndex(1f)
                )
                val core = map.addPolyline(
                    PolylineOptions()
                        .addAll(points)
                        .color(color)
                        .width(dp(7f))
                        .zIndex(2f)
                )
                RenderedLine(model, core, outline)
            }
            RouteMapLineKind.WALKING -> RenderedLine(model, null, null)
        }
    }

    private fun createDirectionArrowIcon(
        widthPx: Float,
        heightPx: Float,
        strokeWidthPx: Float,
        @ColorInt color: Int
    ): BitmapDescriptor {
        val width = ceil(widthPx.toDouble()).toInt().coerceAtLeast(1)
        val height = ceil(heightPx.toDouble()).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = strokeWidthPx / 2f + 1f
        val chevron = Path().apply {
            moveTo(inset, height - inset)
            lineTo(width / 2f, inset)
            lineTo(width - inset, height - inset)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }
        canvas.drawPath(chevron, paint)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun relayoutDirectionArrows() {
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        directionRelayoutCount += 1
        val viewport = RouteMapScreenRect(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            (viewportWidth - paddingRight).toFloat(),
            (viewportHeight - paddingBottom).toFloat()
        )
        lines.values.forEach { rendered ->
            val style = if (rendered.model.kind == RouteMapLineKind.BUS) {
                busDirectionStyle
            } else {
                walkingDirectionStyle
            }
            val icon = if (rendered.model.kind == RouteMapLineKind.BUS) {
                busDirectionIcon
            } else {
                walkingDirectionIcon
            }
            val zIndex = if (rendered.model.kind == RouteMapLineKind.BUS) 4f else 3f
            val placements = RouteMapDirectionPlacementPolicy.place(
                points = rendered.model.points.map { coordinate ->
                    map.projection.toScreenLocation(coordinate.toLatLng()).let { point ->
                        RouteMapScreenPoint(point.x.toFloat(), point.y.toFloat())
                    }
                },
                viewport = viewport,
                style = style,
                maxPlacements = MAX_DIRECTION_ARROWS_PER_LINE
            )
            syncPooledItems(
                existing = rendered.arrows,
                desired = placements,
                create = { placement ->
                    map.addMarker(
                    MarkerOptions()
                        .position(map.projection.fromScreenLocation(placement.point.toAndroidPoint()))
                        .icon(icon)
                        .anchor(0.5f, 0.5f)
                        .flat(true)
                        .rotation(placement.rotation)
                        .zIndex(zIndex)
                    )
                },
                update = { marker, placement ->
                    marker.position = map.projection.fromScreenLocation(placement.point.toAndroidPoint())
                    marker.rotation = placement.rotation
                    marker.zIndex = zIndex
                },
                remove = Marker::remove
            )
        }
    }

    private fun RouteMapScreenPoint.toAndroidPoint() = Point(x.toInt(), y.toInt())

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

    private fun relayoutLabels() {
        val value = presentation ?: return
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        labelRelayoutCount += 1
        runCatching {
            val screenWidth = viewportWidth.toFloat()
            val screenHeight = viewportHeight.toFloat()
            val margin = dp(8f)
            val safeRect = RouteMapLabelRect(
                paddingLeft + margin,
                paddingTop + margin,
                screenWidth - paddingRight - margin,
                screenHeight - paddingBottom - margin
            )
            val markerHalf = dp(16f)
            val markerRects = value.markers.map { model ->
                val point = map.projection.toScreenLocation(model.position.toLatLng())
                RouteMapLabelRect(
                    point.x - markerHalf,
                    point.y - markerHalf,
                    point.x + markerHalf,
                    point.y + markerHalf
                )
            }
            val occupied = (markerRects + reservedLabelRects).toMutableList()
            val acceptedIds = mutableSetOf<String>()
            value.markers.sortedByDescending(::labelPriority).forEach { model ->
                val point = map.projection.toScreenLocation(model.position.toLatLng())
                val displayTitle = model.title.take(MAX_LABEL_CHARACTERS)
                val candidates = labelCandidates(point, displayTitle)
                val critical = model.role != RouteMapMarkerRole.VIA || model.selected
                val chosen = RouteMapLabelPlacementPolicy.choose(
                    candidates = candidates,
                    safeRect = safeRect,
                    occupied = occupied,
                    critical = critical,
                    previousSide = labelSides[model.stableId]
                ) ?: return@forEach
                acceptedIds += model.stableId
                occupied += chosen.rect
                labelSides[model.stableId] = chosen.side
                val icon = createLabelIcon(displayTitle, chosen.side)
                labelMarkers.remove(model.stableId)?.remove()
                labelMarkers[model.stableId] = map.addMarker(
                    MarkerOptions()
                        .position(model.position.toLatLng())
                        .icon(icon.descriptor)
                        .anchor(icon.anchorX, icon.anchorY)
                        .alpha(labelAlpha)
                        .zIndex(8f)
                ) ?: return@forEach
            }
            (labelMarkers.keys - acceptedIds).forEach { id ->
                labelMarkers.remove(id)?.remove()
                labelSides.remove(id)
            }
        }
    }

    private fun labelCandidates(point: Point, value: String): List<RouteMapLabelCandidate> {
        val paint = labelTextPaint()
        val width = paint.measureText(value).coerceAtMost(dp(MAX_LABEL_WIDTH_DP)) + dp(12f)
        val height = dp(25f)
        val gap = dp(20f)
        val x = point.x.toFloat()
        val y = point.y.toFloat()
        return listOf(
            RouteMapLabelCandidate(
                RouteMapLabelSide.RIGHT,
                RouteMapLabelRect(x + gap, y - height / 2f, x + gap + width, y + height / 2f)
            ),
            RouteMapLabelCandidate(
                RouteMapLabelSide.LEFT,
                RouteMapLabelRect(x - gap - width, y - height / 2f, x - gap, y + height / 2f)
            ),
            RouteMapLabelCandidate(
                RouteMapLabelSide.TOP,
                RouteMapLabelRect(x - width / 2f, y - gap - height, x + width / 2f, y - gap)
            ),
            RouteMapLabelCandidate(
                RouteMapLabelSide.BOTTOM,
                RouteMapLabelRect(x - width / 2f, y + gap, x + width / 2f, y + gap + height)
            )
        )
    }

    private fun createLabelIcon(value: String, side: RouteMapLabelSide): LabelIcon {
        val textPaint = labelTextPaint()
        val textWidth = textPaint.measureText(value).coerceAtMost(dp(MAX_LABEL_WIDTH_DP)).toInt()
        val labelWidth = textWidth + dp(12f).toInt()
        val labelHeight = dp(25f).toInt()
        val gap = dp(18f).toInt()
        val bitmapWidth = labelWidth + if (side == RouteMapLabelSide.RIGHT || side == RouteMapLabelSide.LEFT) gap else 0
        val bitmapHeight = labelHeight + if (side == RouteMapLabelSide.TOP || side == RouteMapLabelSide.BOTTOM) gap else 0
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val left = if (side == RouteMapLabelSide.RIGHT) gap.toFloat() else 0f
        val top = if (side == RouteMapLabelSide.BOTTOM) gap.toFloat() else 0f
        val rect = RectF(left, top, left + labelWidth, top + labelHeight)
        canvas.drawRoundRect(rect, dp(6f), dp(6f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.bus_card_surface)
            alpha = 235
            style = Paint.Style.FILL
        })
        canvas.drawRoundRect(rect, dp(6f), dp(6f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.bus_divider)
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
        })
        val baseline = top + labelHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(value, left + dp(6f), baseline, textPaint)
        val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
        return when (side) {
            RouteMapLabelSide.RIGHT -> LabelIcon(descriptor, 0f, 0.5f)
            RouteMapLabelSide.LEFT -> LabelIcon(descriptor, 1f, 0.5f)
            RouteMapLabelSide.TOP -> LabelIcon(descriptor, 0.5f, 1f)
            RouteMapLabelSide.BOTTOM -> LabelIcon(descriptor, 0.5f, 0f)
        }
    }

    private fun labelTextPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.bus_text_primary)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f,
            context.resources.displayMetrics
        )
    }

    private fun labelPriority(marker: RouteMapMarker): Int = when (marker.role) {
        RouteMapMarkerRole.QUERY_ORIGIN,
        RouteMapMarkerRole.QUERY_DESTINATION -> 4
        RouteMapMarkerRole.BOARDING,
        RouteMapMarkerRole.ALIGHTING,
        RouteMapMarkerRole.TRANSFER -> 3
        RouteMapMarkerRole.VIA -> if (marker.selected) 2 else 1
    }

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density

    private fun normalizeDegrees(value: Float): Float = ((value % 360f) + 360f) % 360f

    private data class RenderedMarker(val marker: Marker, val model: RouteMapMarker)

    private data class LabelIcon(
        val descriptor: BitmapDescriptor,
        val anchorX: Float,
        val anchorY: Float
    )

    private data class RenderedLine(
        var model: RouteMapLine,
        val core: Polyline?,
        val outline: Polyline?,
        val arrows: MutableList<Marker> = mutableListOf()
    ) {
        fun remove() {
            core?.remove()
            outline?.remove()
            arrows.forEach(Marker::remove)
        }
    }

    private companion object {
        const val MAX_LABEL_CHARACTERS = 22
        const val MAX_LABEL_WIDTH_DP = 156f
        const val MAX_DIRECTION_ARROWS_PER_LINE = 80
        const val LABEL_FADE_DURATION_MILLIS = 140L
        const val CURRENT_LOCATION_HEADING_ANIMATION_MILLIS = 120L
    }
}

internal class RouteMapMarkerIconFactory(
    private val context: Context,
    private val palette: RouteMapRenderPalette
) {
    private val density = context.resources.displayMetrics.density
    private val cache = mutableMapOf<IconKey, BitmapDescriptor>()

    fun icon(marker: RouteMapMarker): BitmapDescriptor {
        val previousColorSlot = marker.legIndexes.minOrNull() ?: 0
        val nextColorSlot = marker.legIndexes.maxOrNull() ?: previousColorSlot
        val key = IconKey(
            role = marker.role,
            previousColorSlot = previousColorSlot.mod(palette.busColors.size),
            nextColorSlot = nextColorSlot.mod(palette.busColors.size),
            selected = marker.selected,
            densityBucket = (density * 100).toInt()
        )
        return cache.getOrPut(key) { createIcon(key) }
    }

    private fun createIcon(key: IconKey): BitmapDescriptor {
        val size = RouteMapMarkerIconSpec.markerSizePx(key.role, key.selected, density)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val primaryColor = markerColor(key)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = primaryColor }
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
        when (key.role) {
            RouteMapMarkerRole.QUERY_ORIGIN,
            RouteMapMarkerRole.QUERY_DESTINATION -> {
                val centerY = size * 0.39f
                val radius = size * 0.27f
                val path = Path().apply {
                    moveTo(size / 2f - radius * 0.7f, centerY + radius * 0.55f)
                    lineTo(size / 2f, bottom)
                    lineTo(size / 2f + radius * 0.7f, centerY + radius * 0.55f)
                    close()
                }
                canvas.drawPath(path, fill)
                canvas.drawPath(path, outline)
                canvas.drawCircle(size / 2f, centerY, radius, fill)
                canvas.drawCircle(size / 2f, centerY, radius, outline)
                canvas.drawCircle(size / 2f, centerY, radius * 0.36f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.markerOutlineColor
                    style = Paint.Style.FILL
                })
            }
            RouteMapMarkerRole.BOARDING -> {
                canvas.drawCircle(size / 2f, size / 2f, (right - left) / 2f, fill)
                canvas.drawCircle(size / 2f, size / 2f, (right - left) / 2f, outline)
                drawVector(
                    canvas,
                    R.drawable.ic_route_map_bus_front,
                    palette.markerOutlineColor,
                    size,
                    0.55f
                )
            }
            RouteMapMarkerRole.ALIGHTING -> {
                canvas.drawCircle(size / 2f, size / 2f, (right - left) / 2f, fill)
                canvas.drawCircle(size / 2f, size / 2f, (right - left) / 2f, outline)
                drawVector(
                    canvas,
                    R.drawable.ic_route_map_log_out,
                    palette.markerOutlineColor,
                    size,
                    0.58f
                )
            }
            RouteMapMarkerRole.TRANSFER -> {
                val arcBounds = RectF(left, top, right, bottom)
                val transferStyle = RouteMapMarkerIconSpec.transferStyle(
                    previousColor = palette.busColors[key.previousColorSlot],
                    nextColor = palette.busColors[key.nextColorSlot],
                    contrastColor = palette.markerOutlineColor
                )
                val sectorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                }
                transferStyle.fillSectors.forEach { sector ->
                    sectorFill.color = sector.color
                    canvas.drawArc(
                        arcBounds,
                        sector.startAngleDegrees,
                        sector.sweepAngleDegrees,
                        true,
                        sectorFill
                    )
                }
                outline.color = transferStyle.outlineColor
                canvas.drawCircle(size / 2f, size / 2f, (right - left) / 2f, outline)
                drawVector(
                    canvas,
                    R.drawable.ic_route_map_transfer,
                    transferStyle.glyphColor,
                    size,
                    0.52f
                )
            }
            RouteMapMarkerRole.VIA -> {
                val neutral = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = palette.walkingColor
                }
                canvas.drawCircle(size / 2f, size / 2f, (right - left) / 2f, neutral)
                canvas.drawCircle(size / 2f, size / 2f, (right - left) / 2f, outline)
            }
        }
        if (key.selected) {
            canvas.drawCircle(size / 2f, size / 2f, size * 0.47f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * density
                color = palette.selectedColor
            })
        }
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun drawVector(
        canvas: Canvas,
        resource: Int,
        @ColorInt tint: Int,
        size: Int,
        scale: Float
    ) {
        val drawable = AppCompatResources.getDrawable(context, resource)?.mutate() ?: return
        DrawableCompat.setTint(drawable, tint)
        val iconSize = (size * scale).toInt()
        val inset = (size - iconSize) / 2
        drawable.bounds = Rect(inset, inset, inset + iconSize, inset + iconSize)
        drawable.draw(canvas)
    }

    @ColorInt
    private fun markerColor(key: IconKey): Int {
        return when (key.role) {
            RouteMapMarkerRole.QUERY_ORIGIN -> palette.originColor
            RouteMapMarkerRole.QUERY_DESTINATION -> palette.destinationColor
            else -> palette.busColors[key.previousColorSlot]
        }
    }

    private data class IconKey(
        val role: RouteMapMarkerRole,
        val previousColorSlot: Int,
        val nextColorSlot: Int,
        val selected: Boolean,
        val densityBucket: Int
    )
}

private fun RouteMapCoordinate.toLatLng(): LatLng = LatLng(latitude, longitude)
