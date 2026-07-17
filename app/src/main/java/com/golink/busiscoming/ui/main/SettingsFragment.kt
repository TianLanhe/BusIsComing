package com.golink.busiscoming.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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
import com.golink.busiscoming.service.BusMonitorService
import com.golink.busiscoming.ui.settings.AboutActivity
import com.golink.busiscoming.ui.settings.AppSupportActions
import com.golink.busiscoming.ui.settings.RouteTransferActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {
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
        view.findViewById<View>(R.id.settingsShareRow).setOnClickListener {
            AppSupportActions.shareApp(requireContext())
        }
        view.findViewById<View>(R.id.settingsFeedbackRow).setOnClickListener {
            AppSupportActions.sendFeedback(requireContext())
        }
        view.findViewById<View>(R.id.settingsRatingRow).setOnClickListener {
            Toast.makeText(requireContext(), R.string.unsupported_rate_app, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.settingsUpdateRow).setOnClickListener {
            Toast.makeText(requireContext(), R.string.unsupported_check_update, Toast.LENGTH_SHORT).show()
        }
        view.findViewById<View>(R.id.settingsAboutRow).setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
        view.findViewById<View>(R.id.settingsPrivacyRow).setOnClickListener {
            AppSupportActions.openPrivacyPolicy(requireContext())
        }
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
}
