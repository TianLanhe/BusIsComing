package com.golink.busiscoming

import android.os.Bundle
import android.os.Build
import android.os.LocaleList
import android.app.LocaleManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import com.golink.busiscoming.data.local.AppLanguageRepository
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.data.model.InitialInstallChannel
import com.golink.busiscoming.data.update.SharedPreferencesUpdateStateStore
import com.golink.busiscoming.data.update.UpdateStoredState
import org.junit.runner.Description
import org.junit.runner.notification.RunListener

class BusIsComingTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        val listenerName = BusIsComingTestSettingsResetListener::class.java.name
        val configuredListeners = arguments.getString("listener")
            ?.split(',')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        arguments.putString(
            "listener",
            (configuredListeners + listenerName).distinct().joinToString(",")
        )
        arguments.putString("newRunListenerMode", "true")
        super.onCreate(arguments)
    }

    override fun onStart() {
        resetTestSettings(targetContext)
        super.onStart()
    }
}

class BusIsComingTestSettingsResetListener : RunListener() {
    override fun testStarted(description: Description) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        resetTestSettings(instrumentation.targetContext)
        instrumentation.waitForIdleSync()
    }
}

private fun resetTestSettings(context: android.content.Context) {
    AppLanguageRepository(context).setChoice(AppLanguageChoice.TRADITIONAL_CHINESE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags("zh-Hant-HK")
    }
    AppThemePreferenceStore(context).setMode(AppThemeMode.SYSTEM)
    AppCompatDelegate.setDefaultNightMode(AppThemeMode.SYSTEM.nightMode)
    SharedPreferencesUpdateStateStore(context, BuildConfig.VERSION_CODE.toLong()).save(
        UpdateStoredState.initial(BuildConfig.VERSION_CODE.toLong()).copy(
            initialInstallChannel = InitialInstallChannel.UNKNOWN_NON_PLAY,
            lastAutoAttemptAt = System.currentTimeMillis()
        )
    )
}
