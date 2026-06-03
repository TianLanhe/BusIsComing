package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.SortDirection
import com.example.busiscomming.data.model.SortField

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
            SortField.ARRIVAL -> routes.sortedBy { it.arrivalMinutes }
        }
        return if (direction == SortDirection.ASC) sorted else sorted.asReversed()
    }
}
