package com.example.busiscoming

import com.example.busiscoming.data.model.BusMonitorSpeechFormatter
import com.example.busiscoming.data.model.BusMonitorSpeechPolicy
import com.example.busiscoming.data.model.BusMonitorStateEvaluator
import com.example.busiscoming.data.model.BusMonitorStatus
import com.example.busiscoming.data.model.BusMonitorStopPolicy
import com.example.busiscoming.data.model.WalkingScenarioModifier
import com.example.busiscoming.data.model.WalkingSpeedPreset
import com.example.busiscoming.data.model.WalkingTimeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusMonitorModelsTest {
    @Test
    fun walkingEstimateUsesMaxDistanceEstimateAndUserAdjustmentThenAddsModifiers() {
        val estimate = WalkingTimeCalculator.estimate(
            interfaceDistanceMeters = 420,
            straightLineDistanceMeters = 350,
            userAdjustedMinutes = 7,
            speedPreset = WalkingSpeedPreset.NORMAL,
            modifiers = setOf(WalkingScenarioModifier.ELEVATOR, WalkingScenarioModifier.CROSSING)
        )

        assertEquals(6, estimate.interfaceDistanceMinutes)
        assertEquals(5, estimate.straightLineMinutes)
        assertEquals(11, estimate.finalMinutes)
    }

    @Test
    fun rainReducesEffectiveWalkingSpeed() {
        val normal = WalkingTimeCalculator.estimate(
            interfaceDistanceMeters = 400,
            straightLineDistanceMeters = null,
            userAdjustedMinutes = 1,
            speedPreset = WalkingSpeedPreset.NORMAL,
            modifiers = emptySet()
        )
        val rain = WalkingTimeCalculator.estimate(
            interfaceDistanceMeters = 400,
            straightLineDistanceMeters = null,
            userAdjustedMinutes = 1,
            speedPreset = WalkingSpeedPreset.NORMAL,
            modifiers = setOf(WalkingScenarioModifier.RAIN)
        )

        assertEquals(5, normal.finalMinutes)
        assertEquals(6, rain.finalMinutes)
    }

    @Test
    fun evaluatesMonitorStateThresholds() {
        assertEquals(BusMonitorStatus.PREPARE, BusMonitorStateEvaluator.evaluate(12, 8))
        assertEquals(BusMonitorStatus.LEAVE_NOW, BusMonitorStateEvaluator.evaluate(10, 8))
        assertEquals(BusMonitorStatus.LATE, BusMonitorStateEvaluator.evaluate(8, 8))
    }

    @Test
    fun formatsSpeechForState() {
        assertEquals(
            "當前汽車到站剩餘 10 分鐘，請立即出門",
            BusMonitorSpeechFormatter.phrase(10, BusMonitorStatus.LEAVE_NOW)
        )
    }

    @Test
    fun speaksOnlyWhenMonitorStateChanges() {
        assertTrue(BusMonitorSpeechPolicy.shouldSpeak(null, BusMonitorStatus.PREPARE))
        assertFalse(BusMonitorSpeechPolicy.shouldSpeak(BusMonitorStatus.PREPARE, BusMonitorStatus.PREPARE))
        assertTrue(BusMonitorSpeechPolicy.shouldSpeak(BusMonitorStatus.PREPARE, BusMonitorStatus.LEAVE_NOW))
    }

    @Test
    fun stopsAfterSecondEtaOrFirstEtaFallbackWindow() {
        val now = 10_000L

        assertFalse(
            BusMonitorStopPolicy.shouldAutoStop(
                nowMillis = now,
                firstEtaMillis = 9_000L,
                secondEtaMillis = 10_001L
            )
        )
        assertTrue(
            BusMonitorStopPolicy.shouldAutoStop(
                nowMillis = now,
                firstEtaMillis = 9_000L,
                secondEtaMillis = 10_000L
            )
        )
        assertFalse(
            BusMonitorStopPolicy.shouldAutoStop(
                nowMillis = now,
                firstEtaMillis = now - BusMonitorStopPolicy.FALLBACK_SECOND_ETA_DELAY_MILLIS + 1,
                secondEtaMillis = null
            )
        )
        assertTrue(
            BusMonitorStopPolicy.shouldAutoStop(
                nowMillis = now,
                firstEtaMillis = now - BusMonitorStopPolicy.FALLBACK_SECOND_ETA_DELAY_MILLIS,
                secondEtaMillis = null
            )
        )
    }

    @Test
    fun manualStopRequestAlwaysStopsSession() {
        assertTrue(BusMonitorStopPolicy.shouldStopManually(manualStopRequested = true))
        assertFalse(BusMonitorStopPolicy.shouldStopManually(manualStopRequested = false))
    }
}
