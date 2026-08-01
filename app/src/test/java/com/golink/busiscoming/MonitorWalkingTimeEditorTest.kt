package com.golink.busiscoming

import com.golink.busiscoming.data.model.WalkingScenarioModifier
import com.golink.busiscoming.data.model.WalkingSpeedPreset
import com.golink.busiscoming.ui.main.MonitorWalkingInputs
import com.golink.busiscoming.ui.main.MonitorWalkingTimeEditor
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitorWalkingTimeEditorTest {
    @Test
    fun decreaseAndIncreaseChangeDisplayedEstimateByOneMinute() {
        val editor = editor()
        assertEquals(6, editor.estimate().finalMinutes)

        editor.adjust(-1)
        assertEquals(-1, editor.manualAdjustmentMinutes)
        assertEquals(5, editor.estimate().finalMinutes)

        editor.adjust(1)
        assertEquals(0, editor.manualAdjustmentMinutes)
        assertEquals(6, editor.estimate().finalMinutes)
    }

    @Test
    fun repeatedDecreaseStopsAtOneMinute() {
        val editor = editor()

        repeat(20) { editor.adjust(-1) }

        assertEquals(1, editor.estimate().finalMinutes)
        assertEquals(-5, editor.manualAdjustmentMinutes)
    }

    @Test
    fun speedAndScenarioRecalculationPreserveManualAdjustment() {
        val editor = editor()
        editor.adjust(-1)

        editor.selectSpeed(WalkingSpeedPreset.SLOW)
        assertEquals(-1, editor.manualAdjustmentMinutes)
        assertEquals(7, editor.estimate().finalMinutes)

        editor.setModifier(WalkingScenarioModifier.ELEVATOR, enabled = true)
        assertEquals(-1, editor.manualAdjustmentMinutes)
        assertEquals(9, editor.estimate().finalMinutes)
    }

    private fun editor() = MonitorWalkingTimeEditor(
        MonitorWalkingInputs(
            interfaceDistanceMeters = 420,
            straightLineDistanceMeters = 350
        )
    )
}
