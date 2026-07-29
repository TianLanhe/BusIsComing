package com.golink.busiscoming.ui.main

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

object RoutePinSwipeLabelRenderer {
    fun draw(
        canvas: Canvas,
        cardView: View,
        label: String,
        deltaX: Float,
        edgePadding: Float,
        labelPaint: Paint
    ) {
        val textWidth = labelPaint.measureText(label)
        val x = if (deltaX > 0f) {
            cardView.left + edgePadding
        } else {
            cardView.right - edgePadding - textWidth
        }
        val y = cardView.top + cardView.height / 2f -
            (labelPaint.descent() + labelPaint.ascent()) / 2f
        canvas.drawText(label, x, y, labelPaint)
    }
}
