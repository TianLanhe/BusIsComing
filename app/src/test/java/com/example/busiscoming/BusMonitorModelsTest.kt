package com.example.busiscoming

import com.example.busiscoming.data.model.BusMonitorSpeechFormatter
import com.example.busiscoming.data.model.BusMonitorStateEvaluator
import com.example.busiscoming.data.model.BusMonitorStatus
import com.example.busiscoming.data.model.WalkingScenarioModifier
import com.example.busiscoming.data.model.WalkingSpeedPreset
import com.example.busiscoming.data.model.WalkingTimeCalculator
import org.junit.Assert.assertEquals
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
}
