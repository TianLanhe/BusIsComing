package com.example.busiscoming.ui.main

import com.example.busiscoming.data.model.RouteConfig

object RouteShortcutSelector {
    fun visibleRoutes(routes: List<RouteConfig>, selectedRoute: RouteConfig?): List<RouteConfig> {
        if (routes.size <= 2) return routes
        val baseTop3 = routes.take(3)
        val selected = selectedRoute?.let { selected ->
            routes.firstOrNull { it.id == selected.id }
        } ?: return baseTop3

        if (baseTop3.any { it.id == selected.id }) {
            return baseTop3
        }

        return listOf(selected) + baseTop3
            .filterNot { it.id == selected.id }
            .take(2)
    }
}
