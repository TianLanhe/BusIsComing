package com.golink.busiscoming.data.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.golink.busiscoming.BuildConfig

object AppUpdateRuntime {
    lateinit var coordinator: AppUpdateCoordinator
        private set

    fun initialize(context: Context, installedVersionCode: Long) {
        if (::coordinator.isInitialized) return
        val applicationContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())
        val diagnostics = LogcatAppUpdateDiagnostics
        val playSource = GooglePlayUpdateSource(
            applicationContext,
            diagnostics = diagnostics
        )
        coordinator = AppUpdateCoordinator(
            installedVersionCode = installedVersionCode,
            stateStore = SharedPreferencesUpdateStateStore(
                applicationContext,
                installedVersionCode
            ),
            policy = UpdatePolicy(),
            playSource = playSource,
            websiteSource = HttpWebsiteUpdateSource(),
            playPackageProbe = AndroidPlayPackageProbe(applicationContext),
            installSourceReader = AndroidInstallSourceReader(applicationContext),
            playCheckSupported = !BuildConfig.DEBUG,
            diagnostics = diagnostics,
            callbackExecutor = { runnable -> mainHandler.post(runnable) }
        )
    }
}
