package com.golink.busiscoming

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.localization.AppLanguageRuntime
import com.golink.busiscoming.data.update.AppUpdateRuntime
import com.golink.busiscoming.data.repository.CrossOperatorEtaRuntime
import com.golink.busiscoming.data.repository.RouteDatabaseForegroundTrigger
import com.golink.busiscoming.data.repository.RouteDatabaseUpdateTrigger
import com.golink.busiscoming.ui.main.TransitCodeShortcutManager

class BusIsComingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLanguageRuntime.initialize(this)
        AppLanguageRepository(this).applyStoredChoice()
        val mode = AppThemePreferenceStore(this).getMode()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
        AppUpdateRuntime.initialize(this, BuildConfig.VERSION_CODE.toLong())
        CrossOperatorEtaRuntime.initialize(this)
        registerActivityLifecycleCallbacks(
            RouteDatabaseForegroundTrigger {
                CrossOperatorEtaRuntime.updateCoordinator()
                    ?.check(RouteDatabaseUpdateTrigger.APP_FOREGROUND)
            }
        )
        Thread {
            TransitCodeShortcutManager.refreshPublishedShortcut(applicationContext)
        }.start()
    }
}
