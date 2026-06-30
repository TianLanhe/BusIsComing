package com.example.busiscoming.ui.main

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.busiscoming.R
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.WalkingScenarioModifier
import com.example.busiscoming.data.model.WalkingSpeedPreset
import com.example.busiscoming.data.model.WalkingTimeCalculator
import com.example.busiscoming.data.model.WalkingTimeEstimate
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
        content.addView(title("通知欄監控"))
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
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun limitNote(): TextView {
        return TextView(context).apply {
            text = "啟動後會每分鐘嘗試更新；省電、鎖屏或網絡限制可能導致延遲。\n" +
                "鎖屏會顯示路線與 ETA；開啟語音後，狀態變更可能在鎖屏時播報。"
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
        root.addView(sectionLabel("步行到站"))
        root.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }

            addView(stepButton("−") { adjustManualMinutes(-1) })
            walkingMinutesText = TextView(context).apply {
                gravity = Gravity.CENTER
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
        root.addView(sectionLabel("行走速度"))
        val chips = ChipGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        WalkingSpeedPreset.values().forEach { preset ->
            chips.addView(chip("${preset.label} ${preset.speedKmh}km/h", checked = preset == selectedSpeedPreset).apply {
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
        root.addView(sectionLabel("常見場景"))
        val chips = ChipGroup(context).apply {
            isSingleSelection = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        chips.addView(modifierChip(WalkingScenarioModifier.RAIN, "雨天 ×80%速度"))
        chips.addView(modifierChip(WalkingScenarioModifier.ELEVATOR, "等電梯 +2"))
        chips.addView(modifierChip(WalkingScenarioModifier.CROSSING, "天橋/過馬路 +2"))
        root.addView(chips)
        return root
    }

    private fun voiceSection(): View {
        val root = sectionContainer(topMargin = 12)
        voiceSwitch = SwitchMaterial(context).apply {
            text = "語音播報"
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
            text = "開始監控"
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
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun stepButton(text: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(context).apply {
            this.text = text
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
        walkingMinutesText.text = "${estimate.finalMinutes} 分鐘"
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
            interfaceDistanceMinutes?.let { add("接口 $it 分鐘") }
            straightLineMinutes?.let { add("直線 $it 分鐘") }
            if (manualBaseMinutes != null) add("手動 $userAdjustedMinutes 分鐘")
        }
        return sources.ifEmpty { listOf("按手動時間估算") }.joinToString(" · ")
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
