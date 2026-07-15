package com.golink.busiscoming.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.golink.busiscoming.BuildConfig
import com.golink.busiscoming.R
import com.golink.busiscoming.ui.settings.AboutActivity
import com.golink.busiscoming.ui.settings.AppSupportActions
import com.golink.busiscoming.ui.settings.RouteTransferActivity

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
        view.findViewById<View>(R.id.settingsLanguageRow).setOnClickListener {
            Toast.makeText(requireContext(), R.string.unsupported_language_switch, Toast.LENGTH_SHORT).show()
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
}
