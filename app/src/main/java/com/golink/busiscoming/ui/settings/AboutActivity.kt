package com.golink.busiscoming.ui.settings

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.golink.busiscoming.BuildConfig
import com.golink.busiscoming.R
import com.golink.busiscoming.ui.common.applyStatusBarPadding

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        title = getString(R.string.settings_about_us)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<View>(R.id.aboutRoot).applyStatusBarPadding()
        findViewById<View>(R.id.aboutBackButton).setOnClickListener { finish() }
        findViewById<TextView>(R.id.aboutVersionText).text =
            "${getString(R.string.settings_version_prefix)} ${BuildConfig.VERSION_NAME}"
        findViewById<View>(R.id.aboutWebsiteLink).setOnClickListener {
            AppSupportActions.openWebsite(this)
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
