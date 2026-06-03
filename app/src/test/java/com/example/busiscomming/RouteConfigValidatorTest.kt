package com.example.busiscomming

import com.example.busiscomming.data.model.Place
import com.example.busiscomming.data.model.RouteConfigValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteConfigValidatorTest {
    private val origin = Place("渔湾村渔进楼", 22.264, 114.248)
    private val destination = Place("兴华二村丰兴楼", 22.262, 114.236)

    @Test
    fun validRoutePassesValidation() {
        val result = RouteConfigValidator.validate("F", origin, destination)

        assertTrue(result.isValid)
    }

    @Test
    fun missingOriginRequiresCandidateSelection() {
        val result = RouteConfigValidator.validate("F", null, destination)

        assertFalse(result.isValid)
        assertEquals("请选择起点地点", result.originError)
    }

    @Test
    fun missingDestinationRequiresCandidateSelection() {
        val result = RouteConfigValidator.validate("F", origin, null)

        assertFalse(result.isValid)
        assertEquals("请选择终点地点", result.destinationError)
    }

    @Test
    fun sameOriginAndDestinationIsRejected() {
        val result = RouteConfigValidator.validate("F", origin, origin.copy())

        assertFalse(result.isValid)
        assertEquals("起点和终点不能相同", result.destinationError)
    }

    @Test
    fun blankRouteNameIsRejected() {
        val result = RouteConfigValidator.validate("", origin, destination)

        assertFalse(result.isValid)
        assertEquals("必填", result.nameError)
    }
}
