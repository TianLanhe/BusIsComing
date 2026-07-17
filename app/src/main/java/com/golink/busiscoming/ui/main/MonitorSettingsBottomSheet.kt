package com.golink.busiscoming.ui.main

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.golink.busiscoming.R
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.WalkingScenarioModifier
import com.golink.busiscoming.data.model.WalkingSpeedPreset
import com.golink.busiscoming.data.model.WalkingTimeCalculator
import com.golink.busiscoming.data.model.WalkingTimeEstimate
import com.golink.busiscoming.ui.common.applyStableShortTextLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial

data class MonitorSettingsResult(
    val walkingMinutes: Int,
    val voiceEnabled: Boolean
)

data class MonitorWalkingInputs(
    val interfaceDistanceMeters: Int?,
    val straightLineDistanceMeters: Int?
)

class MonitorSettingsBottomSheet(
    private val context: Context,
    private val onStart: (MonitorSettingsResult) -> Unit
) {
    private var dialog: BottomSheetDialog? = null
    private var selectedSpeedPreset = WalkingSpeedPreset.NORMAL
    private val selectedModifiers = linkedSetOf<WalkingScenarioModifier>()
    private var manualBaseMinutes: Int? = null

    private lateinit var walkingMinutesText: TextView
    private lateinit var estimateSourceText: TextView
    private lateinit var voiceSwitch: SwitchMaterial
    private lateinit var inputs: MonitorWalkingInputs

    fun show(route: BusRouteOption, inputs: MonitorWalkingInputs) {
        dispose()
        this.inputs = inputs
        selectedSpeedPreset = WalkingSpeedPreset.NORMAL
        selectedModifiers.clear()
        manualBaseMinutes = null

        val bottomSheetDialog = BottomSheetDialog(context)
        dialog = bottomSheetDialog
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }
        content.addView(title(context.getString(R.string.monitor_title)))
        content.addView(subtitle(route))
        content.addView(limitNote())
        content.addView(walkingTimeSection())
        content.addView(speedSection())
        content.addView(modifierSection())
        content.addView(voiceSection())
        content.addView(startButton())

        bottomSheetDialog.setContentView(content)
        bottomSheetDialog.setOnDismissListener { dialog = null }
        refreshEstimate()
        bottomSheetDialog.show()
    }

    fun dispose() {
        dialog?.dismiss()
        dialog = null
    }

    private fun title(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            applyStableShortTextLayout(Gravity.START)
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun limitNote(): TextView {
        return TextView(context).apply {
            text = context.getString(R.string.monitor_explanation)
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
            textSize = 12f
            maxLines = 3
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
    }

    private fun subtitle(route: BusRouteOption): TextView {
        return TextView(context).apply {
            text = route.stopPreview?.displayText() ?: route.routeSegments.joinToString(" → ")
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
            textSize = 14f
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
    }

    private fun walkingTimeSection(): View {
        val root = sectionContainer(topMargin = 18)
        root.addView(sectionLabel(context.getString(R.string.monitor_walk_section)))
        root.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }

            addView(stepButton("−") { adjustManualMinutes(-1) })
            walkingMinutesText = TextView(context).apply {
                applyStableShortTextLayout(Gravity.CENTER)
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(walkingMinutesText)
            addView(stepButton("+") { adjustManualMinutes(1) })
        })
        estimateSourceText = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        root.addView(estimateSourceText)
        return root
    }

    private fun speedSection(): View {
        val root = sectionContainer(topMargin = 14)
        root.addView(sectionLabel(context.getString(R.string.monitor_speed_section)))
        val chips = ChipGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        WalkingSpeedPreset.values().forEach { preset ->
            val label = context.getString(
                when (preset) {
                    WalkingSpeedPreset.SLOW -> R.string.monitor_speed_slow
                    WalkingSpeedPreset.CHILD -> R.string.monitor_speed_child
                    WalkingSpeedPreset.NORMAL -> R.string.monitor_speed_normal
                    WalkingSpeedPreset.FAST -> R.string.monitor_speed_fast
                }
            )
            chips.addView(chip(
                context.getString(R.string.monitor_speed_option, label, preset.speedKmh),
                checked = preset == selectedSpeedPreset
            ).apply {
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedSpeedPreset = preset
                        refreshEstimate()
                    }
                }
            })
        }
        root.addView(chips)
        return root
    }

    private fun modifierSection(): View {
        val root = sectionContainer(topMargin = 10)
        root.addView(sectionLabel(context.getString(R.string.monitor_scenario_section)))
        val chips = ChipGroup(context).apply {
            isSingleSelection = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        chips.addView(
            modifierChip(WalkingScenarioModifier.RAIN, context.getString(R.string.monitor_scenario_rain))
        )
        chips.addView(
            modifierChip(
                WalkingScenarioModifier.ELEVATOR,
                context.getString(R.string.monitor_scenario_elevator)
            )
        )
        chips.addView(
            modifierChip(
                WalkingScenarioModifier.CROSSING,
                context.getString(R.string.monitor_scenario_crossing)
            )
        )
        root.addView(chips)
        return root
    }

    private fun voiceSection(): View {
        val root = sectionContainer(topMargin = 12)
        voiceSwitch = SwitchMaterial(context).apply {
            text = context.getString(R.string.monitor_voice)
            applyStableShortTextLayout(Gravity.CENTER_VERTICAL)
            isChecked = true
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(voiceSwitch)
        return root
    }

    private fun startButton(): MaterialButton {
        return MaterialButton(context).apply {
            text = context.getString(R.string.monitor_start)
            applyStableShortTextLayout(Gravity.CENTER)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
            setOnClickListener {
                onStart(
                    MonitorSettingsResult(
                        walkingMinutes = currentEstimate().finalMinutes,
                        voiceEnabled = voiceSwitch.isChecked
                    )
                )
                dialog?.dismiss()
            }
        }
    }

    private fun sectionContainer(topMargin: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { this.topMargin = dp(topMargin) }
        }
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            applyStableShortTextLayout(Gravity.START)
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun stepButton(text: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(context).apply {
            this.text = text
            applyStableShortTextLayout(Gravity.CENTER)
            minWidth = dp(44)
            minHeight = dp(40)
            setOnClickListener { onClick() }
        }
    }

    private fun modifierChip(modifier: WalkingScenarioModifier, label: String): Chip {
        return chip(label).apply {
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedModifiers += modifier else selectedModifiers -= modifier
                refreshEstimate()
            }
        }
    }

    private fun chip(label: String, checked: Boolean = false): Chip {
        return Chip(context).apply {
            text = label
            applyStableShortTextLayout(Gravity.CENTER)
            isCheckable = true
            isChecked = checked
        }
    }

    private fun adjustManualMinutes(delta: Int) {
        val extraMinutes = selectedModifiers.sumOf { it.extraMinutes }
        val currentBase = (currentEstimate().finalMinutes - extraMinutes).coerceAtLeast(1)
        manualBaseMinutes = (currentBase + delta).coerceAtLeast(1)
        refreshEstimate()
    }

    private fun refreshEstimate() {
        if (!::walkingMinutesText.isInitialized || !::estimateSourceText.isInitialized) return
        val estimate = currentEstimate()
        walkingMinutesText.text = context.getString(R.string.minutes_count, estimate.finalMinutes)
        estimateSourceText.text = estimate.sourceText()
    }

    private fun currentEstimate(): WalkingTimeEstimate {
        return WalkingTimeCalculator.estimate(
            interfaceDistanceMeters = inputs.interfaceDistanceMeters,
            straightLineDistanceMeters = inputs.straightLineDistanceMeters,
            userAdjustedMinutes = manualBaseMinutes ?: 1,
            speedPreset = selectedSpeedPreset,
            modifiers = selectedModifiers
        )
    }

    private fun WalkingTimeEstimate.sourceText(): String {
        val sources = buildList {
            interfaceDistanceMinutes?.let {
                add(context.getString(R.string.monitor_source_api, it))
            }
            straightLineMinutes?.let {
                add(context.getString(R.string.monitor_source_straight, it))
            }
            if (manualBaseMinutes != null) {
                add(context.getString(R.string.monitor_source_manual, userAdjustedMinutes))
            }
        }
        return sources.ifEmpty {
            listOf(context.getString(R.string.monitor_source_manual_only))
        }.joinToString(" · ")
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
