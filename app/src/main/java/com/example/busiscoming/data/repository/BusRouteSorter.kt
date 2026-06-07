package com.example.busiscoming.data.repository

import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.SortDirection
import com.example.busiscoming.data.model.SortField
import com.example.busiscoming.data.model.WaitTimeState

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
            SortField.WALKING_DISTANCE -> routes.sortedBy { it.walkingDistanceMeters }
        }
        if (field == SortField.ARRIVAL) return sorted
        return if (direction == SortDirection.ASC) sorted else sorted.asReversed()
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
