package com.example.busiscomming

import com.example.busiscomming.data.repository.MockBusRouteRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockBusRouteRepositoryTest {
    private val repository = MockBusRouteRepository()

    @Test
    fun yuwanToXinghuaReturnsThreeRoutes() {
        val routes = repository.searchRoutes("渔湾村渔进楼", "兴华二村丰兴楼")

        assertEquals(3, routes.size)
        assertEquals(listOf("82", "8X", "780"), routes.map { it.routeName })
        assertEquals(listOf(6.0, 7.2, 10.5), routes.map { it.priceHkd })
        assertEquals(listOf(4, 9, 13), routes.map { it.waitMinutes })
    }

    @Test
    fun xinghuaToYuwanReturnsTwoRoutes() {
        val routes = repository.searchRoutes("兴华二村丰兴楼", "渔湾村渔进楼")

        assertEquals(2, routes.size)
        assertEquals(listOf("82", "8X"), routes.map { it.routeName })
        assertEquals(listOf(6.0, 7.2), routes.map { it.priceHkd })
        assertEquals(listOf(6, 11), routes.map { it.waitMinutes })
    }

    @Test
    fun unmatchedRouteReturnsEmptyList() {
        val routes = repository.searchRoutes("中环", "铜锣湾")

        assertTrue(routes.isEmpty())
    }
}
