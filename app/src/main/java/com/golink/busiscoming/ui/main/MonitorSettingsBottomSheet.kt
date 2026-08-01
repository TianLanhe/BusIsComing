package com.golink.busiscoming.ui.main

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.golink.busiscoming.R
import com.golink.busiscoming.data.model.WalkingScenarioModifier
import com.golink.busiscoming.data.model.WalkingSpeedPreset
import com.golink.busiscoming.data.model.WalkingTimeEstimate
import com.golink.busiscoming.service.MonitorNotificationHealth
import com.golink.busiscoming.service.MonitorNotificationIssue
import com.golink.busiscoming.service.MonitorNotificationSeverity
import com.golink.busiscoming.ui.common.applyStableShortTextLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.absoluteValue

data class MonitorSettingsResult(
    val walkingMinutes: Int,
    val voiceEnabled: Boolean
)

class MonitorSettingsBottomSheet(
    private val context: Context,
    private val onStart: (MonitorSettingsResult) -> Unit,
    private val onOpenNotificationSettings: (MonitorNotificationHealth) -> Unit = {}
) {
    private var dialog: BottomSheetDialog? = null
    private lateinit var walkingEditor: MonitorWalkingTimeEditor
    private lateinit var walkingMinutesText: TextView
    private lateinit var estimateSourceText: TextView
    private lateinit var notificationStatusText: TextView
    private lateinit var voiceSwitch: SwitchMaterial
    private var notificationHealth = unknownNotificationHealth()

    val isShowing: Boolean
        get() = dialog?.isShowing == true

    fun show(
        inputs: MonitorWalkingInputs,
        health: MonitorNotificationHealth = unknownNotificationHealth()
    ) {
        dispose()
        walkingEditor = MonitorWalkingTimeEditor(inputs)
        notificationHealth = health

        val bottomSheetDialog = BottomSheetDialog(context)
        dialog = bottomSheetDialog
        val root = LayoutInflater.from(context).inflate(
            R.layout.bottom_sheet_monitor_settings,
            null,
            false
        )
        bindViews(root)
        bindWalkingControls(root)
        bindSettingsControls(root)
        applyActionInsets(root.findViewById(R.id.monitor_action_container))

        bottomSheetDialog.setContentView(root)
        bottomSheetDialog.setOnShowListener {
            bottomSheetDialog.behavior.apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
            ViewCompat.requestApplyInsets(root)
        }
        bottomSheetDialog.setOnDismissListener {
            if (dialog == bottomSheetDialog) {
                dialog = null
            }
        }
        refreshEstimate()
        updateNotificationHealth(health)
        bottomSheetDialog.show()
    }

    fun updateNotificationHealth(health: MonitorNotificationHealth) {
        notificationHealth = health
        if (!::notificationStatusText.isInitialized) return
        notificationStatusText.setText(
            when (health.severity) {
                MonitorNotificationSeverity.READY -> R.string.monitor_notification_ready
                MonitorNotificationSeverity.WARNING -> R.string.monitor_notification_warning
                MonitorNotificationSeverity.BLOCKING -> R.string.monitor_notification_blocking
                MonitorNotificationSeverity.UNKNOWN -> R.string.monitor_notification_unknown
            }
        )
    }

    fun dismissAfterStart() {
        dialog?.dismiss()
    }

    fun dispose() {
        dialog?.dismiss()
        dialog = null
    }

    private fun bindViews(root: View) {
        walkingMinutesText = root.findViewById(R.id.monitor_walking_minutes)
        estimateSourceText = root.findViewById(R.id.monitor_estimate_source)
        notificationStatusText = root.findViewById(R.id.monitor_notification_status)
        voiceSwitch = root.findViewById(R.id.monitor_voice_switch)
    }

    private fun bindWalkingControls(root: View) {
        root.findViewById<MaterialButton>(R.id.monitor_decrease_button).setOnClickListener {
            walkingEditor.adjust(-1)
            refreshEstimate()
        }
        root.findViewById<MaterialButton>(R.id.monitor_increase_button).setOnClickListener {
            walkingEditor.adjust(1)
            refreshEstimate()
        }
        bindSpeedChips(root.findViewById(R.id.monitor_speed_group))
        bindScenarioChips(root.findViewById(R.id.monitor_scenario_group))
    }

    private fun bindSpeedChips(group: ChipGroup) {
        WalkingSpeedPreset.values().forEach { preset ->
            val label = context.getString(
                when (preset) {
                    WalkingSpeedPreset.SLOW -> R.string.monitor_speed_slow
                    WalkingSpeedPreset.CHILD -> R.string.monitor_speed_child
                    WalkingSpeedPreset.NORMAL -> R.string.monitor_speed_normal
                    WalkingSpeedPreset.FAST -> R.string.monitor_speed_fast
                }
            )
            group.addView(
                chip(
                    label = context.getString(
                        R.string.monitor_speed_option,
                        label,
                        preset.speedKmh
                    ),
                    checked = preset == WalkingSpeedPreset.NORMAL
                ).apply {
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            walkingEditor.selectSpeed(preset)
                            refreshEstimate()
                        }
                    }
                }
            )
        }
    }

    private fun bindScenarioChips(group: ChipGroup) {
        group.addView(
            modifierChip(
                WalkingScenarioModifier.RAIN,
                context.getString(R.string.monitor_scenario_rain)
            )
        )
        group.addView(
            modifierChip(
                WalkingScenarioModifier.ELEVATOR,
                context.getString(R.string.monitor_scenario_elevator)
            )
        )
        group.addView(
            modifierChip(
                WalkingScenarioModifier.CROSSING,
                context.getString(R.string.monitor_scenario_crossing)
            )
        )
    }

    private fun bindSettingsControls(root: View) {
        root.findViewById<MaterialButton>(
            R.id.monitor_notification_settings_button
        ).setOnClickListener {
            onOpenNotificationSettings(notificationHealth)
        }
        root.findViewById<MaterialButton>(R.id.monitor_start_button).setOnClickListener {
            onStart(
                MonitorSettingsResult(
                    walkingMinutes = walkingEditor.estimate().finalMinutes,
                    voiceEnabled = voiceSwitch.isChecked
                )
            )
        }
    }

    private fun modifierChip(modifier: WalkingScenarioModifier, label: String): Chip {
        return chip(label).apply {
            setOnCheckedChangeListener { _, isChecked ->
                walkingEditor.setModifier(modifier, isChecked)
                refreshEstimate()
            }
        }
    }

    private fun chip(label: String, checked: Boolean = false): Chip {
        return Chip(context).apply {
            text = label
            applyStableShortTextLayout(Gravity.CENTER)
            minHeight = dp(48)
            isCheckable = true
            isChecked = checked
        }
    }

    private fun refreshEstimate() {
        if (!::walkingMinutesText.isInitialized || !::estimateSourceText.isInitialized) return
        val estimate = walkingEditor.estimate()
        walkingMinutesText.text = context.getString(R.string.minutes_count, estimate.finalMinutes)
        estimateSourceText.text = estimate.sourceText()
    }

    private fun WalkingTimeEstimate.sourceText(): String {
        val sources = buildList {
            interfaceDistanceMinutes?.let {
                add(context.getString(R.string.monitor_source_api, it))
            }
            straightLineMinutes?.let {
                add(context.getString(R.string.monitor_source_straight, it))
            }
            when {
                manualAdjustmentMinutes > 0 -> add(
                    context.getString(
                        R.string.monitor_source_manual_increase,
                        manualAdjustmentMinutes
                    )
                )
                manualAdjustmentMinutes < 0 -> add(
                    context.getString(
                        R.string.monitor_source_manual_decrease,
                        manualAdjustmentMinutes.absoluteValue
                    )
                )
            }
        }
        return sources.ifEmpty {
            listOf(context.getString(R.string.monitor_source_manual_only))
        }.joinToString(" · ")
    }

    private fun applyActionInsets(actionContainer: View) {
        val initialBottomPadding = actionContainer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(actionContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = initialBottomPadding + systemBars.bottom)
            insets
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private companion object {
        fun unknownNotificationHealth(): MonitorNotificationHealth {
            return MonitorNotificationHealth(
                severity = MonitorNotificationSeverity.UNKNOWN,
                issues = listOf(MonitorNotificationIssue.PLATFORM_CHANNELS_UNAVAILABLE)
            )
        }
    }
}
