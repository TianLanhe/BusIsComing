package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.WalkingScenarioModifier
import com.golink.busiscoming.data.model.WalkingSpeedPreset
import com.golink.busiscoming.data.model.WalkingTimeCalculator
import com.golink.busiscoming.data.model.WalkingTimeEstimate

data class MonitorWalkingInputs(
    val interfaceDistanceMeters: Int?,
    val straightLineDistanceMeters: Int?
)

class MonitorWalkingTimeEditor(
    private val inputs: MonitorWalkingInputs
) {
    var selectedSpeedPreset: WalkingSpeedPreset = WalkingSpeedPreset.NORMAL
        private set
    val selectedModifiers: Set<WalkingScenarioModifier>
        get() = mutableSelectedModifiers.toSet()
    var manualAdjustmentMinutes: Int = 0
        private set

    private val mutableSelectedModifiers = linkedSetOf<WalkingScenarioModifier>()

    fun estimate(): WalkingTimeEstimate {
        return WalkingTimeCalculator.estimate(
            interfaceDistanceMeters = inputs.interfaceDistanceMeters,
            straightLineDistanceMeters = inputs.straightLineDistanceMeters,
            manualAdjustmentMinutes = manualAdjustmentMinutes,
            speedPreset = selectedSpeedPreset,
            modifiers = mutableSelectedModifiers
        )
    }

    fun adjust(delta: Int) {
        if (delta < 0 && estimate().finalMinutes <= 1) return
        manualAdjustmentMinutes += delta
    }

    fun selectSpeed(preset: WalkingSpeedPreset) {
        selectedSpeedPreset = preset
    }

    fun setModifier(modifier: WalkingScenarioModifier, enabled: Boolean) {
        if (enabled) {
            mutableSelectedModifiers += modifier
        } else {
            mutableSelectedModifiers -= modifier
        }
    }
}
