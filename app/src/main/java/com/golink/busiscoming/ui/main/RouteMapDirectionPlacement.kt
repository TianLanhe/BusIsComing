package com.golink.busiscoming.ui.main

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal data class RouteMapScreenPoint(val x: Float, val y: Float)

internal data class RouteMapScreenRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun expanded(value: Float) = RouteMapScreenRect(
        left - value,
        top - value,
        right + value,
        bottom + value
    )
}

internal data class RouteMapDirectionStyle(
    val spacing: Float,
    val glyphWidth: Float,
    val glyphHeight: Float,
    val strokeWidth: Float,
    val overscan: Float
) {
    companion object {
        fun bus(density: Float) = RouteMapDirectionStyle(
            spacing = 36f * density,
            glyphWidth = 5.5f * density,
            glyphHeight = 4.5f * density,
            strokeWidth = 1.2f * density,
            overscan = 36f * density
        )

        fun walking(density: Float) = RouteMapDirectionStyle(
            spacing = 14f * density,
            glyphWidth = 9f * density,
            glyphHeight = 7f * density,
            strokeWidth = 2.4f * density,
            overscan = 18f * density
        )
    }
}

internal data class RouteMapDirectionPlacement(
    val point: RouteMapScreenPoint,
    val rotation: Float
)

internal object RouteMapDirectionPlacementPolicy {
    fun place(
        points: List<RouteMapScreenPoint>,
        viewport: RouteMapScreenRect,
        style: RouteMapDirectionStyle,
        maxPlacements: Int = 80
    ): List<RouteMapDirectionPlacement> {
        if (points.size < 2 || maxPlacements <= 0 || style.spacing <= 0f) return emptyList()
        val runs = clippedRuns(points, viewport.expanded(style.overscan))
        val result = ArrayList<RouteMapDirectionPlacement>(min(maxPlacements, 32))
        runs.forEach { run ->
            if (result.size >= maxPlacements) return@forEach
            val totalLength = run.sumOf { it.length.toDouble() }.toFloat()
            if (totalLength < 1f) return@forEach
            val targets = if (totalLength <= style.spacing) {
                listOf(totalLength / 2f)
            } else {
                buildList {
                    var target = style.spacing / 2f
                    while (target < totalLength && result.size + size < maxPlacements) {
                        add(target)
                        target += style.spacing
                    }
                }
            }
            val boundaries = run.runningFold(0f) { distance, segment -> distance + segment.length }
                .drop(1)
                .dropLast(1)
            val safetyRadius = style.glyphWidth / 2f + style.strokeWidth
            targets.forEach { target ->
                if (result.size >= maxPlacements) return@forEach
                if (!hasSafeTangent(run, boundaries, target, safetyRadius)) return@forEach
                val before = pointAt(run, (target - safetyRadius).coerceAtLeast(0f)) ?: return@forEach
                val after = pointAt(run, (target + safetyRadius).coerceAtMost(totalLength)) ?: return@forEach
                val center = pointAt(run, target) ?: return@forEach
                val dx = after.x - before.x
                val dy = after.y - before.y
                if (hypot(dx, dy) < 0.5f) return@forEach
                val rotation = normalizeDegrees(Math.toDegrees(atan2(dx, -dy).toDouble()).toFloat())
                result += RouteMapDirectionPlacement(center, rotation)
            }
        }
        return result
    }

    private fun clippedRuns(
        points: List<RouteMapScreenPoint>,
        rect: RouteMapScreenRect
    ): List<List<Segment>> {
        val runs = mutableListOf<MutableList<Segment>>()
        points.zipWithNext().forEach { (start, end) ->
            val clipped = clip(start, end, rect)
            if (clipped == null) return@forEach
            val segment = Segment(clipped.first, clipped.second)
            if (segment.length < 0.5f) return@forEach
            val current = runs.lastOrNull()
            if (current != null && current.last().end.isNear(segment.start)) {
                current += segment
            } else {
                runs += mutableListOf(segment)
            }
        }
        return runs
    }

    private fun clip(
        start: RouteMapScreenPoint,
        end: RouteMapScreenPoint,
        rect: RouteMapScreenRect
    ): Pair<RouteMapScreenPoint, RouteMapScreenPoint>? {
        val dx = end.x - start.x
        val dy = end.y - start.y
        var minimum = 0f
        var maximum = 1f
        fun constrain(p: Float, q: Float): Boolean {
            if (abs(p) < 0.0001f) return q >= 0f
            val ratio = q / p
            if (p < 0f) {
                if (ratio > maximum) return false
                minimum = max(minimum, ratio)
            } else {
                if (ratio < minimum) return false
                maximum = min(maximum, ratio)
            }
            return true
        }
        if (!constrain(-dx, start.x - rect.left)) return null
        if (!constrain(dx, rect.right - start.x)) return null
        if (!constrain(-dy, start.y - rect.top)) return null
        if (!constrain(dy, rect.bottom - start.y)) return null
        if (minimum > maximum) return null
        return RouteMapScreenPoint(start.x + minimum * dx, start.y + minimum * dy) to
            RouteMapScreenPoint(start.x + maximum * dx, start.y + maximum * dy)
    }

    private fun hasSafeTangent(
        segments: List<Segment>,
        boundaries: List<Float>,
        target: Float,
        safetyRadius: Float
    ): Boolean {
        boundaries.forEachIndexed { index, boundary ->
            if (abs(target - boundary) >= safetyRadius) return@forEachIndexed
            val first = segments[index]
            val second = segments[index + 1]
            val dot = ((first.dx * second.dx + first.dy * second.dy) /
                (first.length * second.length)).coerceIn(-1f, 1f)
            val turn = Math.toDegrees(acos(dot).toDouble()).toFloat()
            if (turn > MAX_SAFE_TURN_DEGREES) return false
        }
        return true
    }

    private fun pointAt(segments: List<Segment>, target: Float): RouteMapScreenPoint? {
        var traversed = 0f
        segments.forEach { segment ->
            if (target <= traversed + segment.length) {
                val fraction = ((target - traversed) / segment.length).coerceIn(0f, 1f)
                return RouteMapScreenPoint(
                    segment.start.x + segment.dx * fraction,
                    segment.start.y + segment.dy * fraction
                )
            }
            traversed += segment.length
        }
        return segments.lastOrNull()?.end
    }

    private fun normalizeDegrees(value: Float): Float = (value % 360f + 360f) % 360f

    private data class Segment(
        val start: RouteMapScreenPoint,
        val end: RouteMapScreenPoint
    ) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = hypot(dx, dy)
    }

    private fun RouteMapScreenPoint.isNear(other: RouteMapScreenPoint): Boolean =
        hypot(x - other.x, y - other.y) < 0.75f

    private const val MAX_SAFE_TURN_DEGREES = 18f
}

internal fun <T, D> syncPooledItems(
    existing: MutableList<T>,
    desired: List<D>,
    create: (D) -> T?,
    update: (T, D) -> Unit,
    remove: (T) -> Unit
) {
    while (existing.size > desired.size) remove(existing.removeAt(existing.lastIndex))
    while (existing.size < desired.size) {
        val created = create(desired[existing.size]) ?: break
        existing += created
    }
    repeat(min(existing.size, desired.size)) { index -> update(existing[index], desired[index]) }
}
