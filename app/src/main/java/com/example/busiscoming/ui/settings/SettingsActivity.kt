package com.example.busiscoming.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.busiscoming.BuildConfig
import com.example.busiscoming.R
import com.example.busiscoming.ui.common.applyStatusBarPadding

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<View>(R.id.settingsRoot).applyStatusBarPadding()
        findViewById<View>(R.id.settingsBackButton).setOnClickListener { finish() }
        findViewById<TextView>(R.id.settingsVersionText).text =
            "${getString(R.string.settings_version_prefix)} ${BuildConfig.VERSION_NAME}"

        findViewById<View>(R.id.settingsLanguageRow).setOnClickListener {
            Toast.makeText(this, R.string.unsupported_language_switch, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.settingsShareRow).setOnClickListener {
            AppSupportActions.shareApp(this)
        }
        findViewById<View>(R.id.settingsFeedbackRow).setOnClickListener {
            AppSupportActions.sendFeedback(this)
        }
        findViewById<View>(R.id.settingsRatingRow).setOnClickListener {
            Toast.makeText(this, R.string.unsupported_rate_app, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.settingsUpdateRow).setOnClickListener {
            Toast.makeText(this, R.string.unsupported_check_update, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.settingsAboutRow).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        findViewById<View>(R.id.settingsPrivacyRow).setOnClickListener {
            AppSupportActions.openPrivacyPolicy(this)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
