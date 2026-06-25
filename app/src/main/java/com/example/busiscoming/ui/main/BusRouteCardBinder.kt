package com.example.busiscoming.ui.main

import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.busiscoming.R
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.EtaArrival
import com.example.busiscoming.data.model.RouteCardStopPreview
import com.example.busiscoming.data.model.WaitTimeState

class BusRouteCardBinder(private val itemView: View) {
    private val routeNameText: TextView = itemView.findViewById(R.id.busRouteNameText)
    private val etaTextColumn: LinearLayout = itemView.findViewById(R.id.busEtaTextColumn)
    private val arrivalText: TextView = itemView.findViewById(R.id.busArrivalText)
    private val nextArrivalText: TextView = itemView.findViewById(R.id.busNextArrivalText)
    private val monitorButton: ImageButton = itemView.findViewById(R.id.busMonitorButton)
    private val stopPreviewText: TextView = itemView.findViewById(R.id.busStopPreviewText)
    private val routeInfoText: TextView = itemView.findViewById(R.id.busRouteInfoText)

    fun bind(route: BusRouteOption, actions: BusRouteCardActions = BusRouteCardActions.Disabled) {
        routeNameText.text = route.routeName
        arrivalText.text = RouteResultCardFormatter.waitStatus(route.waitTimeState)
        arrivalText.setTextColor(waitStatusColor(route.waitTimeState))
        val nextArrival = RouteResultCardFormatter.nextArrivalStatus(route.waitTimeState)
        nextArrivalText.text = nextArrival.orEmpty()
        val shouldShowNextArrival = nextArrival != null &&
            itemView.resources.configuration.fontScale <= LARGE_FONT_SCALE_THRESHOLD
        nextArrivalText.visibility = if (shouldShowNextArrival) View.VISIBLE else View.GONE
        routeInfoText.text = RouteResultCardFormatter.info(route)

        val preview = route.stopPreview
        if (preview == null) {
            stopPreviewText.visibility = View.GONE
        } else {
            stopPreviewText.text = preview.displayText()
            stopPreviewText.visibility = View.VISIBLE
        }

        if (actions.routeClick == null) {
            itemView.setOnClickListener(null)
            itemView.isClickable = false
            itemView.isFocusable = false
        } else {
            itemView.setOnClickListener { actions.routeClick.invoke(route) }
            itemView.isClickable = true
            itemView.isFocusable = true
        }

        val canOpenEtaArrivals = actions.etaClick != null &&
            RouteCardActionPolicy.canOpenEtaArrivals(route.waitTimeState)
        etaTextColumn.isEnabled = true
        etaTextColumn.contentDescription = if (canOpenEtaArrivals) {
            listOfNotNull("查看首程候車班次", arrivalText.text, nextArrival).joinToString("，")
        } else {
            arrivalText.text.toString()
        }
        if (canOpenEtaArrivals) {
            etaTextColumn.setOnClickListener { actions.etaClick?.invoke(route) }
            etaTextColumn.isClickable = true
            etaTextColumn.isFocusable = true
        } else {
            etaTextColumn.setOnClickListener(null)
            etaTextColumn.isClickable = false
            etaTextColumn.isFocusable = false
        }

        val canMonitor = actions.monitorClick != null && RouteCardActionPolicy.canStartMonitor(route)
        monitorButton.isEnabled = canMonitor
        monitorButton.alpha = if (canMonitor) 1f else 0.32f
        if (canMonitor) {
            monitorButton.setOnClickListener { actions.monitorClick?.invoke(route) }
            monitorButton.isClickable = true
            monitorButton.isFocusable = true
        } else {
            monitorButton.setOnClickListener(null)
            monitorButton.isClickable = false
            monitorButton.isFocusable = false
        }
    }

    private fun waitStatusColor(waitTimeState: WaitTimeState): Int {
        val colorRes = when (waitTimeState) {
            is WaitTimeState.Available -> R.color.bus_wait_accent
            WaitTimeState.Loading -> R.color.bus_text_secondary
            WaitTimeState.Unavailable -> R.color.bus_wait_unavailable
        }
        return ContextCompat.getColor(itemView.context, colorRes)
    }

    private companion object {
        const val LARGE_FONT_SCALE_THRESHOLD = 1.15f
    }
}

data class BusRouteCardActions(
    val routeClick: ((BusRouteOption) -> Unit)? = null,
    val etaClick: ((BusRouteOption) -> Unit)? = null,
    val monitorClick: ((BusRouteOption) -> Unit)? = null
) {
    companion object {
        val Disabled = BusRouteCardActions()
    }
}

object FirstRunRoutePreview {
    fun route(): BusRouteOption {
        return BusRouteOption(
            routeName = "118",
            routeSegments = listOf("118"),
            priceHkd = 11.8,
            durationMinutes = 38,
            arrivalMinutes = 4,
            transferCount = 0,
            walkingDistanceMeters = 160,
            waitTimeState = WaitTimeState.Available(
                listOf(
                    EtaArrival(sequence = 1, minutes = 4),
                    EtaArrival(sequence = 2, minutes = 11)
                )
            ),
            stopPreview = RouteCardStopPreview(
                boardingStopName = "柴灣",
                alightingStopName = "中環"
            )
        )
    }
}
