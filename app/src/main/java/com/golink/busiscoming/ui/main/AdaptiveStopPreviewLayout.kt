package com.golink.busiscoming.ui.main

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.golink.busiscoming.R

class AdaptiveStopPreviewLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {
    private val origin: View
        get() = findViewById(R.id.busStopOriginText)
    private val direction: View
        get() = findViewById(R.id.busStopDirectionText)
    private val destination: View
        get() = findViewById(R.id.busStopDestinationText)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureDesired(direction, heightMeasureSpec)
        measureDesired(origin, heightMeasureSpec)
        measureDesired(destination, heightMeasureSpec)

        val horizontalPadding = paddingLeft + paddingRight
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val naturalContentWidth = origin.measuredWidth + direction.measuredWidth + destination.measuredWidth
        val availableTextWidth = when (widthMode) {
            MeasureSpec.UNSPECIFIED -> origin.measuredWidth + destination.measuredWidth
            else -> (widthSize - horizontalPadding - direction.measuredWidth).coerceAtLeast(0)
        }
        val allocation = StopPreviewWidthAllocator.allocate(
            availableWidth = availableTextWidth,
            originDesiredWidth = origin.measuredWidth,
            destinationDesiredWidth = destination.measuredWidth
        )
        measureExactWidth(origin, allocation.originWidth, heightMeasureSpec)
        measureExactWidth(destination, allocation.destinationWidth, heightMeasureSpec)

        val contentWidth = origin.measuredWidth + direction.measuredWidth + destination.measuredWidth
        val measuredWidth = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> (contentWidth + horizontalPadding).coerceAtMost(widthSize)
            else -> naturalContentWidth + horizontalPadding
        }
        val contentHeight = maxOf(origin.measuredHeight, direction.measuredHeight, destination.measuredHeight)
        setMeasuredDimension(
            measuredWidth,
            resolveSize(contentHeight + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var childLeft = paddingLeft
        listOf(origin, direction, destination).forEach { child ->
            val childTop = paddingTop + (measuredHeight - paddingTop - paddingBottom - child.measuredHeight) / 2
            child.layout(
                childLeft,
                childTop,
                childLeft + child.measuredWidth,
                childTop + child.measuredHeight
            )
            childLeft += child.measuredWidth
        }
    }

    private fun measureDesired(child: View, parentHeightMeasureSpec: Int) {
        child.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            getChildMeasureSpec(parentHeightMeasureSpec, paddingTop + paddingBottom, child.layoutParams.height)
        )
    }

    private fun measureExactWidth(child: View, width: Int, parentHeightMeasureSpec: Int) {
        child.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            getChildMeasureSpec(parentHeightMeasureSpec, paddingTop + paddingBottom, child.layoutParams.height)
        )
    }
}
