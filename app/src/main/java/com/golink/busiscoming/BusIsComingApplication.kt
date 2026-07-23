package com.golink.busiscoming

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.localization.AppLanguageRuntime
import com.golink.busiscoming.ui.main.TransitCodeShortcutManager

class BusIsComingApplication : Application() {
    override fun onCreate() {
        AppLanguageRuntime.initialize(this)
        AppLanguageRepository(this).applyStoredChoice()
        val mode = AppThemePreferenceStore(this).getMode()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
        super.onCreate()
        Thread {
            TransitCodeShortcutManager.refreshPublishedShortcut(applicationContext)
        }.start()
    }
}
