package com.golink.busiscoming.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.golink.busiscoming.BuildConfig
import com.golink.busiscoming.R
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.localization.AppLanguage
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.localization.LanguageSnapshot
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.data.model.AppUpdateState
import com.golink.busiscoming.data.model.UpdateCheckTrigger
import com.golink.busiscoming.data.model.UpdateFailureKind
import com.golink.busiscoming.data.update.AppUpdateExternalActions
import com.golink.busiscoming.data.update.AppUpdateRuntime
import com.golink.busiscoming.service.BusMonitorService
import com.golink.busiscoming.ui.settings.AboutActivity
import com.golink.busiscoming.ui.settings.AppSupportActions
import com.golink.busiscoming.ui.settings.RouteTransferActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {
    private var transitCodeShortcutValue: TextView? = null
    private var updateRow: View? = null
    private var updateSummary: TextView? = null
    private var updateDot: View? = null
    private var updateSubscription: AutoCloseable? = null
    private var manualUpdateCheckRequested = false
    private val shortcutPermissionNavigator = XiaomiShortcutPermissionNavigator()
    private var shortcutRecheckRunnable: Runnable? = null
    private var awaitingShortcutPermissionResult = false
    private val shortcutPermissionSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (!awaitingShortcutPermissionResult) return@registerForActivityResult
        awaitingShortcutPermissionResult = false
        val context = context ?: return@registerForActivityResult
        if (TransitCodeShortcutManager.currentState(context) == TransitCodeShortcutState.PINNED) {
            TransitCodeShortcutManager.recordPinned(context)
            renderTransitCodeShortcutState()
        } else {
            requestTransitCodeShortcut(bypassXiaomiPermissionGate = true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.settingsVersionText).text =
            "${getString(R.string.settings_version_prefix)} ${BuildConfig.VERSION_NAME}"
        val languageRepository = AppLanguageRepository(requireContext())
        val languageValue = view.findViewById<TextView>(R.id.settingsLanguageValue)
        fun renderLanguage(snapshot: LanguageSnapshot) {
            val actualLanguage = getString(snapshot.effectiveLanguage.labelRes())
            languageValue.text = if (snapshot.choice == AppLanguageChoice.FOLLOW_SYSTEM) {
                getString(R.string.language_follow_system_with_actual, actualLanguage)
            } else {
                actualLanguage
            }
        }
        renderLanguage(languageRepository.snapshot())
        val themeStore = AppThemePreferenceStore(requireContext())
        val appearanceValue = view.findViewById<TextView>(R.id.settingsAppearanceValue)
        fun renderThemeMode(mode: AppThemeMode) {
            appearanceValue.setText(mode.labelRes())
        }
        renderThemeMode(themeStore.getMode())
        transitCodeShortcutValue = view.findViewById(R.id.settingsTransitCodeShortcutValue)
        updateRow = view.findViewById(R.id.settingsUpdateRow)
        updateSummary = view.findViewById(R.id.settingsUpdateSummary)
        updateDot = view.findViewById(R.id.settingsUpdateDot)
        renderTransitCodeShortcutState()
        renderUpdateState(AppUpdateRuntime.coordinator.currentState())
        updateSubscription = AppUpdateRuntime.coordinator.observe(::renderUpdateState)
        view.findViewById<View>(R.id.settingsAppearanceRow).setOnClickListener {
            val modes = arrayOf(AppThemeMode.SYSTEM, AppThemeMode.LIGHT, AppThemeMode.DARK)
            val labels = modes.map { getString(it.labelRes()) }.toTypedArray()
            val currentMode = themeStore.getMode()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_appearance)
                .setSingleChoiceItems(labels, modes.indexOf(currentMode)) { dialog, which ->
                    val selectedMode = modes[which]
                    dialog.dismiss()
                    if (selectedMode != currentMode) {
                        themeStore.setMode(selectedMode)
                        renderThemeMode(selectedMode)
                        AppCompatDelegate.setDefaultNightMode(selectedMode.nightMode)
                    }
                }
                .show()
        }
        view.findViewById<View>(R.id.settingsLanguageRow).setOnClickListener {
            val choices = arrayOf(
                AppLanguageChoice.FOLLOW_SYSTEM,
                AppLanguageChoice.TRADITIONAL_CHINESE,
                AppLanguageChoice.SIMPLIFIED_CHINESE,
                AppLanguageChoice.ENGLISH
            )
            val labels = arrayOf(
                getString(R.string.language_follow_system),
                getString(R.string.language_traditional_self),
                getString(R.string.language_simplified_self),
                getString(R.string.language_english_self)
            )
            val currentChoice = languageRepository.getChoice()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(labels, choices.indexOf(currentChoice)) { dialog, which ->
                    val selectedChoice = choices[which]
                    dialog.dismiss()
                    if (selectedChoice != currentChoice) {
                        renderLanguage(languageRepository.setChoice(selectedChoice))
                        requireContext().startService(
                            BusMonitorService.languageChangedIntent(requireContext())
                        )
                    }
                }
                .show()
        }
        view.findViewById<View>(R.id.settingsRouteTransferRow).setOnClickListener {
            startActivity(Intent(requireContext(), RouteTransferActivity::class.java))
        }
        view.findViewById<View>(R.id.settingsTransitCodeShortcutRow).setOnClickListener {
            requestTransitCodeShortcut()
        }
        view.findViewById<View>(R.id.settingsShareRow).setOnClickListener {
            AppSupportActions.shareApp(requireContext())
        }
        view.findViewById<View>(R.id.settingsFeedbackRow).setOnClickListener {
            AppSupportActions.sendFeedback(requireContext())
        }
        view.findViewById<View>(R.id.settingsRatingRow).setOnClickListener {
            Toast.makeText(requireContext(), R.string.unsupported_rate_app, Toast.LENGTH_SHORT).show()
        }
        updateRow?.setOnClickListener {
            manualUpdateCheckRequested = true
            AppUpdateRuntime.coordinator.check(UpdateCheckTrigger.MANUAL)
        }
        view.findViewById<View>(R.id.settingsAboutRow).setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
        view.findViewById<View>(R.id.settingsPrivacyRow).setOnClickListener {
            AppSupportActions.openPrivacyPolicy(requireContext())
        }
    }

    override fun onResume() {
        super.onResume()
        val context = context ?: return
        if (TransitCodeShortcutManager.currentState(context) == TransitCodeShortcutState.PINNED) {
            TransitCodeShortcutManager.recordPinned(context)
        } else if (XiaomiShortcutPermissionStateStore(context).consumePinRequestPending()) {
            TransitCodeShortcutManager.recordPinRequestIncomplete(context)
        }
        renderTransitCodeShortcutState()
    }

    override fun onDestroyView() {
        updateSubscription?.close()
        updateSubscription = null
        shortcutRecheckRunnable?.let { transitCodeShortcutValue?.removeCallbacks(it) }
        shortcutRecheckRunnable = null
        transitCodeShortcutValue = null
        updateRow = null
        updateSummary = null
        updateDot = null
        super.onDestroyView()
    }

    private fun renderUpdateState(state: AppUpdateState) {
        val summaryView = updateSummary ?: return
        val model = UpdateSettingsUiModelFactory.create(state, System.currentTimeMillis())
        val summary = if (model.versionArgument != null) {
            getString(model.summaryRes, model.versionArgument)
        } else {
            getString(model.summaryRes)
        }
        summaryView.text = summary
        updateDot?.visibility = if (model.showDot) View.VISIBLE else View.GONE
        updateRow?.apply {
            isEnabled = model.rowEnabled
            contentDescription = getString(
                R.string.update_row_content_description,
                getString(R.string.settings_check_update),
                summary
            )
        }
        if (manualUpdateCheckRequested && !state.isChecking) {
            manualUpdateCheckRequested = false
            when (state.lastFailure?.kind) {
                UpdateFailureKind.PLAY_DEBUG_BUILD_UNSUPPORTED ->
                    showPlayVerificationFailure(R.string.update_debug_build_unsupported_message)
                UpdateFailureKind.PLAY_APP_NOT_OWNED ->
                    showPlayVerificationFailure(R.string.update_play_not_owned_message)
                UpdateFailureKind.PLAY_UNAVAILABLE -> Toast.makeText(
                    requireContext(),
                    R.string.update_play_unavailable,
                    Toast.LENGTH_SHORT
                ).show()
                null -> Unit
                else -> Toast.makeText(
                    requireContext(),
                    R.string.update_status_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showPlayVerificationFailure(@StringRes messageRes: Int) {
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.update_verification_failed_title)
            .setMessage(messageRes)
            .setNegativeButton(R.string.update_action_cancel, null)
            .setPositiveButton(R.string.update_action_open_play) { _, _ ->
                AppUpdateExternalActions.openPlayListing(context)
            }
            .show()
    }

    private fun requestTransitCodeShortcut(bypassXiaomiPermissionGate: Boolean = false) {
        val context = context ?: return
        when (
            TransitCodeShortcutManager.requestPinnedShortcut(
                context,
                bypassXiaomiPermissionGate
            )
        ) {
            TransitCodeShortcutRequestResult.ALREADY_PINNED -> {
                Toast.makeText(
                    context,
                    R.string.transit_code_shortcut_already_added,
                    Toast.LENGTH_SHORT
                ).show()
                renderTransitCodeShortcutState()
            }
            TransitCodeShortcutRequestResult.NEEDS_PERMISSION -> {
                openXiaomiShortcutPermissionSettings()
            }
            TransitCodeShortcutRequestResult.REQUESTED -> {
                XiaomiShortcutPermissionStateStore(context).markPinRequestPending()
                renderTransitCodeShortcutState(requestPending = true)
                scheduleShortcutStateRecheck()
            }
            TransitCodeShortcutRequestResult.UNSUPPORTED -> {
                Toast.makeText(
                    context,
                    R.string.transit_code_shortcut_unsupported_guide,
                    Toast.LENGTH_LONG
                ).show()
                renderTransitCodeShortcutState()
            }
            TransitCodeShortcutRequestResult.FAILED -> {
                TransitCodeShortcutManager.recordPinRequestIncomplete(context)
                Toast.makeText(
                    context,
                    R.string.transit_code_shortcut_failed_retry,
                    Toast.LENGTH_SHORT
                ).show()
                renderTransitCodeShortcutState()
            }
        }
    }

    private fun openXiaomiShortcutPermissionSettings() {
        val context = context ?: return
        awaitingShortcutPermissionResult = true
        when (
            shortcutPermissionNavigator.open(context) { intent ->
                shortcutPermissionSettingsLauncher.launch(intent)
            }
        ) {
            XiaomiShortcutPermissionNavigationResult.XIAOMI_SETTINGS,
            XiaomiShortcutPermissionNavigationResult.APP_DETAILS -> {
                Toast.makeText(
                    context,
                    R.string.transit_code_shortcut_permission_guide,
                    Toast.LENGTH_LONG
                ).show()
                renderTransitCodeShortcutState()
            }
            XiaomiShortcutPermissionNavigationResult.FAILED -> {
                awaitingShortcutPermissionResult = false
                Toast.makeText(
                    context,
                    R.string.transit_code_shortcut_failed_retry,
                    Toast.LENGTH_SHORT
                ).show()
                renderTransitCodeShortcutState()
            }
        }
    }

    private fun scheduleShortcutStateRecheck() {
        val value = transitCodeShortcutValue ?: return
        shortcutRecheckRunnable?.let(value::removeCallbacks)
        val runnable = Runnable {
            shortcutRecheckRunnable = null
            val context = context ?: return@Runnable
            if (TransitCodeShortcutManager.currentState(context) == TransitCodeShortcutState.PINNED) {
                TransitCodeShortcutManager.recordPinned(context)
            } else if (XiaomiShortcutPermissionStateStore(context).consumePinRequestPending()) {
                TransitCodeShortcutManager.recordPinRequestIncomplete(context)
            }
            renderTransitCodeShortcutState()
        }
        shortcutRecheckRunnable = runnable
        value.postDelayed(runnable, SHORTCUT_STATE_RECHECK_DELAY_MS)
    }

    private fun renderTransitCodeShortcutState(requestPending: Boolean = false) {
        val value = transitCodeShortcutValue ?: return
        val context = context ?: return
        val state = TransitCodeShortcutManager.currentState(context)
        val textRes = when {
            state == TransitCodeShortcutState.PINNED ->
                R.string.transit_code_shortcut_already_added
            requestPending -> R.string.transit_code_shortcut_request_pending
            XiaomiShortcutPermissionPolicy().action(
                gatePassed = XiaomiShortcutPermissionStateStore(context).isGatePassed(),
                bypassPermissionGate = false
            ) == XiaomiShortcutPermissionAction.OPEN_SETTINGS ->
                R.string.transit_code_shortcut_permission_required
            else -> R.string.settings_transit_code_shortcut_summary
        }
        value.setText(textRes)
    }

    private fun AppThemeMode.labelRes(): Int = when (this) {
        AppThemeMode.SYSTEM -> R.string.theme_mode_system
        AppThemeMode.LIGHT -> R.string.theme_mode_light
        AppThemeMode.DARK -> R.string.theme_mode_dark
    }

    private fun AppLanguage.labelRes(): Int = when (this) {
        AppLanguage.TRADITIONAL_CHINESE -> R.string.language_traditional_self
        AppLanguage.SIMPLIFIED_CHINESE -> R.string.language_simplified_self
        AppLanguage.ENGLISH -> R.string.language_english_self
    }

    private companion object {
        const val SHORTCUT_STATE_RECHECK_DELAY_MS = 1_500L
    }
}
