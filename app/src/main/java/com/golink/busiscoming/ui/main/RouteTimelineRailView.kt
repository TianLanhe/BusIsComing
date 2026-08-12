package com.golink.busiscoming.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import androidx.core.content.ContextCompat
import com.golink.busiscoming.R

class RouteTimelineRailView(context: Context) : View(context) {
    enum class Style { SOLID, DASHED, VIA, ENDPOINT, NODE, ORIGIN, DESTINATION, NONE }

    var style: Style = Style.NONE
        set(value) {
            field = value
            invalidate()
        }
    var railColor: Int = ContextCompat.getColor(context, R.color.route_timeline_walk)
        set(value) {
            field = value
            invalidate()
        }
    var nodeColor: Int = ContextCompat.getColor(context, R.color.route_timeline_stop)
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (style == Style.NONE) return
        val x = width / 2f
        paint.color = railColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(
            if (style in BUS_STYLES) {
                RouteTimelineRailGeometry.BUS_AXIS_WIDTH_DP
            } else {
                RouteTimelineRailGeometry.WALK_AXIS_WIDTH_DP
            }
        )
        paint.pathEffect = if (style == Style.DASHED) DashPathEffect(floatArrayOf(dp(5f), dp(5f)), 0f) else null
        if (style != Style.NODE && style != Style.ORIGIN && style != Style.DESTINATION) {
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
        }
        paint.pathEffect = null
        if (style == Style.VIA) {
            paint.style = Paint.Style.FILL
            paint.color = ContextCompat.getColor(context, R.color.route_position_support)
            canvas.drawCircle(
                x,
                height / 2f,
                dp(RouteTimelineRailGeometry.VIA_DIAMETER_DP / 2f),
                paint
            )
            paint.color = nodeColor
            canvas.drawCircle(
                x,
                height / 2f,
                dp(
                    RouteTimelineRailGeometry.VIA_DIAMETER_DP / 2f -
                        RouteTimelineRailGeometry.VIA_BOUNDARY_DP
                ),
                paint
            )
        } else if (style == Style.ENDPOINT) {
            paint.style = Paint.Style.FILL
            paint.color = ContextCompat.getColor(context, R.color.route_position_support)
            canvas.drawCircle(
                x,
                height / 2f,
                dp(RouteTimelineRailGeometry.ENDPOINT_DIAMETER_DP / 2f),
                paint
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(RouteTimelineRailGeometry.ENDPOINT_OUTLINE_DP)
            paint.color = darken(railColor)
            canvas.drawCircle(x, height / 2f, dp(6.5f), paint)
            paint.style = Paint.Style.FILL
            paint.color = nodeColor
            canvas.drawCircle(
                x,
                height / 2f,
                dp(RouteTimelineRailGeometry.ENDPOINT_CORE_DP / 2f),
                paint
            )
        } else if (style == Style.NODE) {
            paint.style = Paint.Style.FILL
            paint.color = ContextCompat.getColor(context, R.color.route_position_support)
            canvas.drawCircle(x, height / 2f, dp(5f), paint)
            paint.color = ContextCompat.getColor(context, R.color.route_timeline_stop)
            canvas.drawCircle(x, height / 2f, dp(3f), paint)
        } else if (style == Style.ORIGIN || style == Style.DESTINATION) {
            paint.style = Paint.Style.FILL
            paint.color = ContextCompat.getColor(context, R.color.bus_card_surface)
            canvas.drawCircle(x, height / 2f, dp(8f), paint)
            paint.color = railColor
            canvas.drawCircle(x, height / 2f, dp(5f), paint)
            paint.color = ContextCompat.getColor(context, R.color.bus_card_surface)
            canvas.drawCircle(x, height / 2f, dp(1.5f), paint)
        }
    }

    private fun dp(value: Int): Int = dp(value.toFloat()).toInt()
    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun darken(color: Int): Int = android.graphics.Color.rgb(
        (android.graphics.Color.red(color) * 0.72f).toInt(),
        (android.graphics.Color.green(color) * 0.72f).toInt(),
        (android.graphics.Color.blue(color) * 0.72f).toInt()
    )

    private companion object {
        val BUS_STYLES = setOf(Style.SOLID, Style.VIA, Style.ENDPOINT)
    }
}

object RouteTimelineRailGeometry {
    const val BUS_AXIS_WIDTH_DP = 10f
    const val WALK_AXIS_WIDTH_DP = 2f
    const val VIA_DIAMETER_DP = 10f
    const val VIA_BOUNDARY_DP = 2f
    const val ENDPOINT_DIAMETER_DP = 16f
    const val ENDPOINT_OUTLINE_DP = 3f
    const val ENDPOINT_CORE_DP = 4f
}
