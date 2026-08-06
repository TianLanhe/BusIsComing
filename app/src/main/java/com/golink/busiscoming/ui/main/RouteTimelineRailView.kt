package com.golink.busiscoming.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import androidx.core.content.ContextCompat
import com.golink.busiscoming.R

class RouteTimelineRailView(context: Context) : View(context) {
    enum class Style { SOLID, DASHED, NODE, ORIGIN, DESTINATION, NONE }

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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (style == Style.NONE) return
        val x = width / 2f
        paint.color = railColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(if (style == Style.SOLID) 6 else 2).toFloat()
        paint.pathEffect = if (style == Style.DASHED) DashPathEffect(floatArrayOf(dp(5f), dp(5f)), 0f) else null
        if (style != Style.NODE && style != Style.ORIGIN && style != Style.DESTINATION) {
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
        }
        paint.pathEffect = null
        if (style == Style.NODE) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, height / 2f, dp(4f), paint)
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
}
