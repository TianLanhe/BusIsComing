package com.golink.busiscoming.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import com.golink.busiscoming.R
import com.golink.busiscoming.data.model.SortField
import com.google.android.material.button.MaterialButton

class RouteResultControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    val sortControls = findAfterInflate<android.view.View>(R.id.sortControls)
    val summaryContainer = findAfterInflate<android.view.View>(R.id.resultSummaryContainer)
    val summaryText = findAfterInflate<TextView>(R.id.resultSummaryText)
    val updatedAtText = findAfterInflate<TextView>(R.id.resultUpdatedAtText)
    val sortButtons: Map<SortField, MaterialButton> = mapOf(
        SortField.ROUTE to findAfterInflate(R.id.sortRouteButton),
        SortField.PRICE to findAfterInflate(R.id.sortPriceButton),
        SortField.DURATION to findAfterInflate(R.id.sortDurationButton),
        SortField.ARRIVAL to findAfterInflate(R.id.sortArrivalButton),
        SortField.WALKING_DISTANCE to findAfterInflate(R.id.sortWalkingDistanceButton)
    )

    private fun <T : android.view.View> findAfterInflate(id: Int): T {
        if (childCount == 0) {
            LayoutInflater.from(context).inflate(R.layout.view_route_result_controls, this, true)
        }
        return findViewById(id)
    }
}
