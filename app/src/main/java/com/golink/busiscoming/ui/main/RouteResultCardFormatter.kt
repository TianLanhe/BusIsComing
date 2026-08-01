package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.R
import com.golink.busiscoming.ui.common.LocalizedText

object RouteResultCardFormatter {
    fun price(priceHkd: Double, text: LocalizedText): String {
        return if (priceHkd == 0.0) {
            text.get(R.string.price_free, emptyArray())
        } else {
            text.get(R.string.price_hkd, arrayOf(priceHkd))
        }
    }

    fun waitStatus(waitTimeState: WaitTimeState, text: LocalizedText): String {
        return when (waitTimeState) {
            is WaitTimeState.Available -> {
                if (waitTimeState.minutes <= 0) {
                    text.get(R.string.eta_due, emptyArray())
                } else {
                    text.get(R.string.eta_wait, arrayOf(waitTimeState.minutes))
                }
            }
            WaitTimeState.Loading -> text.get(R.string.eta_loading, emptyArray())
            WaitTimeState.NoArrivals -> text.get(R.string.eta_unavailable, emptyArray())
            is WaitTimeState.Unavailable -> text.get(R.string.eta_temporarily_unavailable, emptyArray())
        }
    }

    fun nextArrivalStatus(waitTimeState: WaitTimeState, text: LocalizedText): String? {
        val nextArrival = (waitTimeState as? WaitTimeState.Available)?.nextArrival ?: return null
        val minutesText = if (nextArrival.minutes <= 0) {
            text.get(R.string.eta_due, emptyArray())
        } else {
            text.get(R.string.minutes_count, arrayOf(nextArrival.minutes))
        }
        return text.get(R.string.eta_next, arrayOf(minutesText))
    }

    fun info(route: BusRouteOption, text: LocalizedText): String {
        return text.get(
            R.string.route_card_summary,
            arrayOf<Any>(price(route.priceHkd, text), route.durationMinutes, route.walkingDistanceMeters)
        )
    }

    fun duration(durationMinutes: Int, text: LocalizedText): String =
        text.get(R.string.route_card_duration_value, arrayOf(durationMinutes))

    fun walking(walkingDistanceMeters: Int, text: LocalizedText): String =
        text.get(R.string.route_card_walking_value, arrayOf(walkingDistanceMeters))

    fun infoAccessibility(route: BusRouteOption, text: LocalizedText): String {
        return text.get(
            R.string.route_card_info_content_description,
            arrayOf<Any>(price(route.priceHkd, text), route.durationMinutes, route.walkingDistanceMeters)
        )
    }

    fun resultSummary(routes: List<BusRouteOption>, text: LocalizedText): String {
        return text.get(
            R.string.route_results_summary,
            arrayOf(routes.size, routes.count { it.transferCount == 0 })
        )
    }
}

object RouteCardActionPolicy {
    fun canOpenEtaArrivals(waitTimeState: WaitTimeState): Boolean {
        return (waitTimeState as? WaitTimeState.Available)?.arrivals.orEmpty().size >= 2
    }

    fun canStartMonitor(route: BusRouteOption): Boolean {
        return route.waitTimeState is WaitTimeState.Available && route.firstLegEtaQuery != null
    }
}
