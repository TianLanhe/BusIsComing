package com.golink.busiscoming

import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfigValidator
import com.golink.busiscoming.data.model.RouteConfigValidationError
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
        assertEquals(RouteConfigValidationError.ORIGIN_REQUIRED, result.originError)
    }

    @Test
    fun missingDestinationRequiresCandidateSelection() {
        val result = RouteConfigValidator.validate("F", origin, null)

        assertFalse(result.isValid)
        assertEquals(RouteConfigValidationError.DESTINATION_REQUIRED, result.destinationError)
    }

    @Test
    fun sameOriginAndDestinationIsRejected() {
        val result = RouteConfigValidator.validate("F", origin, origin.copy())

        assertFalse(result.isValid)
        assertEquals(RouteConfigValidationError.SAME_PLACES, result.destinationError)
    }

    @Test
    fun blankRouteNameIsRejected() {
        val result = RouteConfigValidator.validate("", origin, destination)

        assertFalse(result.isValid)
        assertEquals(RouteConfigValidationError.REQUIRED, result.nameError)
    }
}
