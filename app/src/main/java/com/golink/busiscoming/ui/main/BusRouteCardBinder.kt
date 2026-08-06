package com.golink.busiscoming.ui.main

import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.golink.busiscoming.R
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.EtaArrival
import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.ui.common.localizedText
import com.google.android.material.card.MaterialCardView

class BusRouteCardBinder(private val itemView: View) {
    private val localizedText = itemView.context.localizedText()
    private val routeNameText: TextView = itemView.findViewById(R.id.busRouteNameText)
    private val etaTextColumn: LinearLayout = itemView.findViewById(R.id.busEtaTextColumn)
    private val arrivalText: TextView = itemView.findViewById(R.id.busArrivalText)
    private val nextArrivalText: TextView = itemView.findViewById(R.id.busNextArrivalText)
    private val monitorButton: ImageButton = itemView.findViewById(R.id.busMonitorButton)
    private val stopPreviewLayout: View = itemView.findViewById(R.id.busStopPreviewLayout)
    private val stopOriginText: TextView = itemView.findViewById(R.id.busStopOriginText)
    private val stopDestinationText: TextView = itemView.findViewById(R.id.busStopDestinationText)
    private val routeInfoLayout: View = itemView.findViewById(R.id.busRouteInfoLayout)
    private val routePriceText: TextView = itemView.findViewById(R.id.busRoutePriceText)
    private val routeDurationText: TextView = itemView.findViewById(R.id.busRouteDurationText)
    private val routeWalkingText: TextView = itemView.findViewById(R.id.busRouteWalkingText)
    private val bookmark: View = itemView.findViewById(R.id.busPersistentPinBookmark)
    private val card: MaterialCardView = itemView as MaterialCardView
    private val accessibilityActionIds = mutableListOf<Int>()

    fun bind(route: BusRouteOption, actions: BusRouteCardActions = BusRouteCardActions.Disabled) {
        resetPinPresentation()
        routeNameText.text = route.routeName
        arrivalText.text = RouteResultCardFormatter.waitStatus(route.waitTimeState, localizedText)
        arrivalText.setTextColor(waitStatusColor(route.waitTimeState))
        val nextArrival = RouteResultCardFormatter.nextArrivalStatus(route.waitTimeState, localizedText)
        nextArrivalText.text = nextArrival.orEmpty()
        val shouldShowNextArrival = nextArrival != null &&
            itemView.resources.configuration.fontScale <= LARGE_FONT_SCALE_THRESHOLD
        nextArrivalText.visibility = if (shouldShowNextArrival) View.VISIBLE else View.GONE
        routePriceText.text = RouteResultCardFormatter.price(route.priceHkd, localizedText)
        routeDurationText.text = RouteResultCardFormatter.duration(route.durationMinutes, localizedText)
        routeWalkingText.text = RouteResultCardFormatter.walking(
            route.walkingDistanceDisplayState,
            localizedText
        )
        routeInfoLayout.contentDescription = RouteResultCardFormatter.infoAccessibility(route, localizedText)

        val preview = route.stopPreview
        if (preview == null) {
            stopPreviewLayout.visibility = View.GONE
            stopPreviewLayout.contentDescription = null
        } else {
            stopOriginText.text = preview.boardingStopName
            stopDestinationText.text = preview.alightingStopName
            stopPreviewLayout.contentDescription = preview.displayText()
            stopPreviewLayout.visibility = View.VISIBLE
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
            itemView.context.getString(
                R.string.eta_arrivals_content_description,
                listOfNotNull(arrivalText.text, nextArrival).joinToString(", ")
            )
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

    fun bind(item: RouteCardItem, actions: BusRouteCardActions = BusRouteCardActions.Disabled) {
        bind(item.route, actions)
        val isPinned = item.pinLevel != PinLevel.UNPINNED
        card.strokeWidth = itemView.resources.getDimensionPixelSize(
            if (isPinned) R.dimen.route_pin_stroke_width else R.dimen.route_card_stroke_width
        )
        card.strokeColor = ContextCompat.getColor(
            itemView.context,
            if (isPinned) R.color.bus_chip_selected else R.color.bus_divider
        )
        bookmark.visibility =
            if (item.pinLevel == PinLevel.PERSISTENT) View.VISIBLE else View.GONE
        ViewCompat.setStateDescription(
            itemView,
            when (item.pinLevel) {
                PinLevel.UNPINNED -> null
                PinLevel.TEMPORARY -> itemView.context.getString(R.string.route_pin_state_temporary)
                PinLevel.PERSISTENT -> itemView.context.getString(R.string.route_pin_state_persistent)
            }
        )
        bindPinAccessibilityActions(item, actions.pinAction)
    }

    private fun resetPinPresentation() {
        itemView.translationX = 0f
        card.strokeWidth = itemView.resources.getDimensionPixelSize(R.dimen.route_card_stroke_width)
        card.strokeColor = ContextCompat.getColor(itemView.context, R.color.bus_divider)
        bookmark.visibility = View.GONE
        ViewCompat.setStateDescription(itemView, null)
        accessibilityActionIds.forEach { ViewCompat.removeAccessibilityAction(itemView, it) }
        accessibilityActionIds.clear()
    }

    private fun bindPinAccessibilityActions(
        item: RouteCardItem,
        onPinAction: ((RouteCardItem, RoutePinAction) -> Unit)?
    ) {
        if (onPinAction == null || !item.isPinEligible) return
        when (item.pinLevel) {
            PinLevel.UNPINNED -> addPinAccessibilityAction(
                R.string.route_pin_action_temporary,
                item,
                RoutePinAction.PIN_TEMPORARY,
                onPinAction
            )
            PinLevel.TEMPORARY -> {
                addPinAccessibilityAction(
                    R.string.route_pin_action_persistent,
                    item,
                    RoutePinAction.PIN_PERSISTENT,
                    onPinAction
                )
                addPinAccessibilityAction(
                    R.string.route_pin_action_cancel,
                    item,
                    RoutePinAction.CANCEL,
                    onPinAction
                )
            }
            PinLevel.PERSISTENT -> addPinAccessibilityAction(
                R.string.route_pin_action_cancel,
                item,
                RoutePinAction.CANCEL,
                onPinAction
            )
        }
    }

    private fun addPinAccessibilityAction(
        labelRes: Int,
        item: RouteCardItem,
        action: RoutePinAction,
        onPinAction: (RouteCardItem, RoutePinAction) -> Unit
    ) {
        accessibilityActionIds += ViewCompat.addAccessibilityAction(
            itemView,
            itemView.context.getString(labelRes)
        ) { _, _ ->
            onPinAction(item, action)
            true
        }
    }

    private fun waitStatusColor(waitTimeState: WaitTimeState): Int {
        val colorRes = when (waitTimeState) {
            is WaitTimeState.Available -> R.color.bus_wait_accent
            WaitTimeState.Loading -> R.color.bus_text_secondary
            WaitTimeState.NoArrivals,
            is WaitTimeState.Unavailable -> R.color.bus_wait_unavailable
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
    val monitorClick: ((BusRouteOption) -> Unit)? = null,
    val pinAction: ((RouteCardItem, RoutePinAction) -> Unit)? = null
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
                boardingStopName = "Chai Wan",
                alightingStopName = "Central"
            )
        )
    }
}
