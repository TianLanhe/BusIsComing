package com.golink.busiscoming.ui.main

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.golink.busiscoming.R

/** 依操作區實際寬度安排「本次行程」，正常字體優先讓路徑單行尾部省略。 */
class SearchTripContextLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private var body: LinearLayout? = null
    private var route: TextView? = null
    private var actions: View? = null
    private var showingSingleRow: Boolean? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        body = findViewById(R.id.searchTripContextBody)
        route = findViewById(R.id.searchTripRouteText)
        actions = findViewById(R.id.searchTripActions)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updateBodyOrientation(widthMeasureSpec)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun updateBodyOrientation(widthMeasureSpec: Int) {
        val body = body ?: return
        val route = route ?: return
        val actions = actions ?: return
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        if (availableWidth <= 0) return
        val actionRow = actions as? LinearLayout ?: return
        val buttonWidths = mutableListOf<Int>()
        val buttonMargins = mutableListOf<Int>()
        repeat(actionRow.childCount) { index ->
            val button = actionRow.getChildAt(index)
            if (button.visibility == View.VISIBLE) {
                button.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                )
                val params = button.layoutParams as? LinearLayout.LayoutParams
                buttonWidths += button.measuredWidth
                buttonMargins += (params?.leftMargin ?: 0) + (params?.rightMargin ?: 0)
            }
        }
        val preferredActionsWidth = SearchTripActionWidthPolicy.totalWidth(
            visibleButtonWidthsPx = buttonWidths,
            horizontalMarginsPx = buttonMargins
        )
        val singleRow = SearchTripContextLayoutPolicy.usesSingleRow(
            availableWidthPx = availableWidth,
            actionsWidthPx = preferredActionsWidth,
            fontScale = resources.configuration.fontScale,
            gapPx = dp(8),
            minimumRouteWidthPx = dp(60)
        )
        if (showingSingleRow == singleRow) return
        showingSingleRow = singleRow
        body.orientation = if (singleRow) HORIZONTAL else VERTICAL
        (route.layoutParams as? LinearLayout.LayoutParams)?.apply {
            width = if (singleRow) 0 else LayoutParams.MATCH_PARENT
            weight = if (singleRow) 1f else 0f
            marginEnd = if (singleRow) dp(8) else 0
        }
        (actions.layoutParams as? LinearLayout.LayoutParams)?.apply {
            width = LayoutParams.WRAP_CONTENT
            weight = 0f
            gravity = if (singleRow) {
                android.view.Gravity.CENTER_VERTICAL
            } else {
                android.view.Gravity.END
            }
            topMargin = if (singleRow) 0 else dp(8)
        }
        (actions as? LinearLayout)?.let { actionRow ->
            actionRow.gravity =
                android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            actionRow.isBaselineAligned = false
            repeat(actionRow.childCount) { index ->
                val button = actionRow.getChildAt(index)
                (button.layoutParams as? LinearLayout.LayoutParams)?.apply {
                    width = LayoutParams.WRAP_CONTENT
                    weight = 0f
                }
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

object SearchTripContextLayoutPolicy {
    fun usesSingleRow(
        availableWidthPx: Int,
        actionsWidthPx: Int,
        fontScale: Float,
        gapPx: Int = 0,
        minimumRouteWidthPx: Int = 0
    ): Boolean =
        fontScale <= 1f &&
            minimumRouteWidthPx + actionsWidthPx + gapPx <= availableWidthPx
}

object SearchTripActionWidthPolicy {
    fun totalWidth(
        visibleButtonWidthsPx: List<Int>,
        horizontalMarginsPx: List<Int>
    ): Int = visibleButtonWidthsPx.sum() + horizontalMarginsPx.sum()
}
