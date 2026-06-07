package com.example.busiscoming

import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.model.RouteConfigValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteConfigValidatorTest {
    private val origin = Place("漁灣村漁進樓", 22.264, 114.248)
    private val destination = Place("興華二村豐興樓", 22.262, 114.236)

    @Test
    fun validRoutePassesValidation() {
        val result = RouteConfigValidator.validate("F", origin, destination)

        assertTrue(result.isValid)
    }

    @Test
    fun missingOriginRequiresCandidateSelection() {
        val result = RouteConfigValidator.validate("F", null, destination)

        assertFalse(result.isValid)
        assertEquals("請選擇起點地點", result.originError)
    }

    @Test
    fun missingDestinationRequiresCandidateSelection() {
        val result = RouteConfigValidator.validate("F", origin, null)

        assertFalse(result.isValid)
        assertEquals("請選擇終點地點", result.destinationError)
    }

    @Test
    fun sameOriginAndDestinationIsRejected() {
        val result = RouteConfigValidator.validate("F", origin, origin.copy())

        assertFalse(result.isValid)
        assertEquals("起點和終點不能相同", result.destinationError)
    }

    @Test
    fun blankRouteNameIsRejected() {
        val result = RouteConfigValidator.validate("", origin, destination)

        assertFalse(result.isValid)
        assertEquals("必填", result.nameError)
    }
}
