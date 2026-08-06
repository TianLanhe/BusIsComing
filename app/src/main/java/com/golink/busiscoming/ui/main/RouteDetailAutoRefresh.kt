package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailCompleteness
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop

enum class RouteDetailRefreshDomain {
    DYNAMIC_DETAIL,
    FIRST_LEG_ETA
}

data class RouteDetailRefreshCycleResult(
    val accepted: Boolean,
    val finished: Boolean,
    val anyDomainSucceeded: Boolean
)

class RouteDetailRefreshCycleCoordinator {
    private var generation: Int? = null
    private val terminal = linkedMapOf<RouteDetailRefreshDomain, Boolean>()

    fun begin(value: Int) {
        generation = value
        terminal.clear()
    }

    fun finish(
        value: Int,
        domain: RouteDetailRefreshDomain,
        success: Boolean
    ): RouteDetailRefreshCycleResult {
        if (generation != value || domain in terminal) {
            return RouteDetailRefreshCycleResult(false, false, terminal.values.any { it })
        }
        terminal[domain] = success
        return RouteDetailRefreshCycleResult(
            accepted = true,
            finished = terminal.size == RouteDetailRefreshDomain.entries.size,
            anyDomainSucceeded = terminal.values.any { it }
        )
    }

    fun invalidate() {
        generation = null
        terminal.clear()
    }
}

object RouteDetailDynamicMerger {
    fun merge(current: RouteDetail, candidate: RouteDetail): RouteDetail? {
        if (!hasSameReliableStructure(current, candidate)) return null
        return current.copy(
            priceHkd = candidate.priceHkd,
            durationMinutes = candidate.durationMinutes,
            plannedDepartureTime = candidate.plannedDepartureTime ?: current.plannedDepartureTime,
            plannedArrivalTime = candidate.plannedArrivalTime ?: current.plannedArrivalTime,
            legs = current.legs.zip(candidate.legs).map { (stable, dynamic) ->
                stable.copy(
                    fareHkd = dynamic.fareHkd ?: stable.fareHkd,
                    plannedBoardingTime = dynamic.plannedBoardingTime ?: stable.plannedBoardingTime,
                    plannedAlightingTime = dynamic.plannedAlightingTime ?: stable.plannedAlightingTime
                )
            }
        )
    }

    fun hasSameReliableStructure(current: RouteDetail, candidate: RouteDetail): Boolean {
        if (candidate.completeness != RouteDetailCompleteness.COMPLETE) return false
        if (current.routeName != candidate.routeName || current.legs.size != candidate.legs.size) return false
        if (current.transfers.map { it.type } != candidate.transfers.map { it.type }) return false
        return current.legs.zip(candidate.legs).all { (left, right) -> sameLeg(left, right) }
    }

    private fun sameLeg(left: RouteDetailLeg, right: RouteDetailLeg): Boolean =
        left.route == right.route &&
            left.routeVariant == right.routeVariant &&
            sameStop(left.boardingStop, right.boardingStop) &&
            sameStop(left.alightingStop, right.alightingStop) &&
            left.viaStops.size == right.viaStops.size &&
            left.viaStops.zip(right.viaStops).all { (a, b) -> sameStop(a, b) }

    private fun sameStop(left: RouteDetailStop, right: RouteDetailStop): Boolean =
        left.stopId == right.stopId &&
            left.sequence == right.sequence &&
            left.routeVariant == right.routeVariant &&
            left.role == right.role
}
