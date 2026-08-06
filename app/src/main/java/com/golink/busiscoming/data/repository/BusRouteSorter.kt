package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.SortDirection
import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.model.WalkingDistanceDisplayState

object BusRouteSorter {
    fun sort(
        routes: List<BusRouteOption>,
        field: SortField,
        direction: SortDirection
    ): List<BusRouteOption> {
        val sorted = when (field) {
            SortField.ROUTE -> routes.sortedWith(
                compareBy<BusRouteOption> { it.transferCount }.thenBy { it.routeName }
            )
            SortField.PRICE -> routes.sortedBy { it.priceHkd }
            SortField.DURATION -> routes.sortedBy { it.durationMinutes }
            SortField.ARRIVAL -> sortByWaitTime(routes, direction)
            SortField.WALKING_DISTANCE -> return sortByWalkingDistance(routes, direction)
        }
        if (field == SortField.ARRIVAL) return sorted
        return if (direction == SortDirection.ASC) sorted else sorted.asReversed()
    }

    private fun sortByWalkingDistance(
        routes: List<BusRouteOption>,
        direction: SortDirection
    ): List<BusRouteOption> {
        val numeric = routes.withIndex()
            .filter { it.value.walkingDistanceDisplayState !is WalkingDistanceDisplayState.Loading }
            .sortedWith { first, second ->
                val firstDistance = requireNotNull(
                    first.value.walkingDistanceDisplayState.distanceMetersOrNull
                )
                val secondDistance = requireNotNull(
                    second.value.walkingDistanceDisplayState.distanceMetersOrNull
                )
                val distanceComparison = if (direction == SortDirection.ASC) {
                    firstDistance.compareTo(secondDistance)
                } else {
                    secondDistance.compareTo(firstDistance)
                }
                distanceComparison.takeIf { it != 0 } ?: first.index.compareTo(second.index)
            }
            .map { it.value }
        val loading = routes.filter {
            it.walkingDistanceDisplayState is WalkingDistanceDisplayState.Loading
        }
        return numeric + loading
    }

    private fun sortByWaitTime(
        routes: List<BusRouteOption>,
        direction: SortDirection
    ): List<BusRouteOption> {
        val availableRoutes = routes.filter { it.waitTimeState is WaitTimeState.Available }
        val unavailableRoutes = routes.filterNot { it.waitTimeState is WaitTimeState.Available }
        val sortedAvailableRoutes = if (direction == SortDirection.ASC) {
            availableRoutes.sortedBy { (it.waitTimeState as WaitTimeState.Available).minutes }
        } else {
            availableRoutes.sortedByDescending { (it.waitTimeState as WaitTimeState.Available).minutes }
        }
        return sortedAvailableRoutes + unavailableRoutes
    }
}
