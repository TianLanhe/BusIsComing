package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.BusRouteOption

class MockBusRouteRepository : BusRouteRepository {
    override fun searchRoutes(origin: String, destination: String): List<BusRouteOption> {
        return when (origin.trim() to destination.trim()) {
            "渔湾村渔进楼" to "兴华二村丰兴楼" -> listOf(
                BusRouteOption(routeName = "82", priceHkd = 6.0, waitMinutes = 4),
                BusRouteOption(routeName = "8X", priceHkd = 7.2, waitMinutes = 9),
                BusRouteOption(routeName = "780", priceHkd = 10.5, waitMinutes = 13)
            )
            "兴华二村丰兴楼" to "渔湾村渔进楼" -> listOf(
                BusRouteOption(routeName = "82", priceHkd = 6.0, waitMinutes = 6),
                BusRouteOption(routeName = "8X", priceHkd = 7.2, waitMinutes = 11)
            )
            else -> emptyList()
        }
    }
}
