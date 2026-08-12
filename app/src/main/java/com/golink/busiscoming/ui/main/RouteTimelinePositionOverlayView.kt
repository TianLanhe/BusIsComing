package com.golink.busiscoming.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.golink.busiscoming.R
import com.golink.busiscoming.data.model.RouteJourneyAxis

class RouteTimelinePositionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var list: RecyclerView? = null
    private var adapter: RouteDetailAdapter? = null
    private var axis: RouteJourneyAxis? = null
    private var position: RouteCurrentPositionPresentation? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tail = Path()
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            postInvalidateOnAnimation()
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    fun attach(list: RecyclerView, adapter: RouteDetailAdapter) {
        this.list?.removeOnScrollListener(scrollListener)
        this.list = list
        this.adapter = adapter
        list.addOnScrollListener(scrollListener)
        list.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> postInvalidateOnAnimation() }
    }

    fun render(
        axis: RouteJourneyAxis?,
        position: RouteCurrentPositionPresentation?
    ) {
        this.axis = axis
        this.position = position
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val center = resolveCenter() ?: return
        drawIndicator(canvas, center.x, center.y)
    }

    private fun resolveCenter(): RouteTimelineAnchorPoint? {
        val current = position?.timeline ?: return null
        return when (current) {
            is RouteCurrentPositionPresentation.Timeline.AtNode -> current.targetIds
                .firstNotNullOfOrNull(::anchorCenter)
            is RouteCurrentPositionPresentation.Timeline.BetweenNodes -> {
                val currentAxis = axis ?: return null
                val from = currentAxis.nodesById[current.fromNodeId]?.timelineTargetIds
                    ?.firstNotNullOfOrNull(::anchorBounds)
                    ?: return null
                val to = currentAxis.nodesById[current.toNodeId]?.timelineTargetIds
                    ?.firstNotNullOfOrNull(::anchorBounds)
                    ?: return null
                RouteTimelineAnchorGeometry.betweenNodes(from, to)
            }
            is RouteCurrentPositionPresentation.Timeline.Walking -> {
                val bounds = anchorBounds(current.targetId) ?: return null
                RouteTimelineAnchorGeometry.walking(bounds, current.progress)
            }
        }
    }

    private fun anchorCenter(stableId: String): RouteTimelineAnchorPoint? =
        anchorBounds(stableId)?.let(RouteTimelineAnchorGeometry::atNode)

    private fun anchorBounds(stableId: String): RouteTimelineAnchorBounds? {
        val anchor = adapter?.boundTimelineAnchor(stableId) ?: return null
        val host = parent as? ViewGroup ?: return null
        if (!anchor.isShown || !anchor.isLaidOut) return null
        return Rect().also { rect ->
            anchor.getDrawingRect(rect)
            host.offsetDescendantRectToMyCoords(anchor, rect)
        }.takeIf { it.bottom > 0 && it.top < height }?.let {
            RouteTimelineAnchorBounds(
                it.left.toFloat(),
                it.top.toFloat(),
                it.right.toFloat(),
                it.bottom.toFloat()
            )
        }
    }

    private fun drawIndicator(canvas: Canvas, x: Float, y: Float) {
        val blue = ContextCompat.getColor(context, R.color.route_current_position)
        paint.style = Paint.Style.FILL
        paint.color = (blue and 0x00FFFFFF) or 0x26000000
        canvas.drawCircle(x, y, dp(RoutePositionIndicatorGeometry.HALO_DIAMETER_DP / 2f), paint)

        val ringRadius = dp(RoutePositionIndicatorGeometry.RING_DIAMETER_DP / 2f)
        val tipX = x + ringRadius + dp(RoutePositionIndicatorGeometry.TAIL_LENGTH_DP)
        tail.reset()
        tail.moveTo(x + ringRadius - dp(1f), y - dp(3.5f))
        tail.lineTo(tipX + dp(1f), y)
        tail.lineTo(x + ringRadius - dp(1f), y + dp(3.5f))
        tail.close()
        val support = ContextCompat.getColor(context, R.color.route_position_support)
        paint.color = support
        canvas.drawPath(tail, paint)
        tail.reset()
        tail.moveTo(x + ringRadius - dp(1f), y - dp(RoutePositionIndicatorGeometry.TAIL_HALF_BASE_DP))
        tail.lineTo(tipX, y)
        tail.lineTo(x + ringRadius - dp(1f), y + dp(RoutePositionIndicatorGeometry.TAIL_HALF_BASE_DP))
        tail.close()
        paint.color = blue
        canvas.drawPath(tail, paint)

        paint.color = support
        canvas.drawCircle(x, y, dp(RoutePositionIndicatorGeometry.SUPPORT_DIAMETER_DP / 2f), paint)
        paint.color = blue
        canvas.drawCircle(x, y, ringRadius, paint)
        paint.color = support
        canvas.drawCircle(x, y, dp(6f), paint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
