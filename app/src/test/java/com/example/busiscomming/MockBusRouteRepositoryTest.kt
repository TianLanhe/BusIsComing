package com.example.busiscomming

import com.example.busiscomming.data.model.Place
import com.example.busiscomming.data.repository.MockBusRouteRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class MockBusRouteRepositoryTest {
    private val repository = MockBusRouteRepository()
    private val origin = Place("渔湾村渔进楼", 22.264, 114.248)
    private val destination = Place("兴华二村丰兴楼", 22.262, 114.236)

    @Test
    fun anyRouteReturnsFixedMockRoutes() {
        val routes = repository.searchRoutes(origin, destination)

        assertEquals(3, routes.size)
        assertEquals(listOf("82", "8X", "780"), routes.map { it.routeName })
        assertEquals(listOf(6.0, 7.2, 10.5), routes.map { it.priceHkd })
        assertEquals(listOf(4, 9, 13), routes.map { it.durationMinutes })
        assertEquals(listOf(4, 9, 13), routes.map { it.arrivalMinutes })
        assertEquals(listOf(0, 0, 0), routes.map { it.transferCount })
    }

    @Test
    fun reversedRouteAlsoReturnsFixedMockRoutes() {
        val routes = repository.searchRoutes(destination, origin)

        assertEquals(listOf("82", "8X", "780"), routes.map { it.routeName })
    }
}
