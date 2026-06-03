package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.Place

class MockBusRouteRepository : BusRouteRepository {
    override fun searchRoutes(origin: Place, destination: Place): List<BusRouteOption> {
        return listOf(
            BusRouteOption(
                routeName = "82",
                priceHkd = 6.0,
                durationMinutes = 4,
                arrivalMinutes = 4,
                transferCount = 0
            ),
            BusRouteOption(
                routeName = "8X",
                priceHkd = 7.2,
                durationMinutes = 9,
                arrivalMinutes = 9,
                transferCount = 0
            ),
            BusRouteOption(
                routeName = "780",
                priceHkd = 10.5,
                durationMinutes = 13,
                arrivalMinutes = 13,
                transferCount = 0
            )
        )
    }
}
