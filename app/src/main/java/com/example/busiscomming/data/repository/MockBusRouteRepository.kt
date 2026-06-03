package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.Place

class MockBusRouteRepository : BusRouteRepository {
    override fun searchRoutes(origin: Place, destination: Place): List<BusRouteOption> {
        return listOf(
            BusRouteOption(routeName = "82", priceHkd = 6.0, waitMinutes = 4),
            BusRouteOption(routeName = "8X", priceHkd = 7.2, waitMinutes = 9),
            BusRouteOption(routeName = "780", priceHkd = 10.5, waitMinutes = 13)
        )
    }
}
