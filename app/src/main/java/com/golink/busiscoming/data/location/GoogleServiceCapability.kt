package com.golink.busiscoming.data.location

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

enum class GoogleServiceCapability { AVAILABLE, UNAVAILABLE, UNKNOWN }

fun interface GoogleServiceCapabilityProbe {
    fun detect(): GoogleServiceCapability
}

class AndroidGoogleServiceCapabilityProbe(context: Context) : GoogleServiceCapabilityProbe {
    private val context = context.applicationContext

    @Suppress("DEPRECATION")
    override fun detect(): GoogleServiceCapability = try {
        val info = context.packageManager.getPackageInfo("com.google.android.gms", 0)
        val app = info.applicationInfo
        when {
            app == null -> GoogleServiceCapability.UNKNOWN
            app.flags and ApplicationInfo.FLAG_INSTALLED == 0 -> GoogleServiceCapability.UNKNOWN
            !app.enabled || app.flags and ApplicationInfo.FLAG_SUSPENDED != 0 -> GoogleServiceCapability.UNAVAILABLE
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SERVICE_UPDATING -> GoogleServiceCapability.UNKNOWN
            info.versionCode < GoogleApiAvailability.GOOGLE_PLAY_SERVICES_VERSION_CODE -> GoogleServiceCapability.UNAVAILABLE
            // GMS 套件能力獨立於商店；SDK 的 SERVICE_INVALID 也可能只表示商店缺失。
            // 個別 API 的網絡／帳號／服務錯誤仍由原 Google 路徑處理，不啟用系統後備。
            else -> GoogleServiceCapability.AVAILABLE
        }
    } catch (_: PackageManager.NameNotFoundException) {
        val updating = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SERVICE_UPDATING
        }.getOrDefault(false)
        if (updating) GoogleServiceCapability.UNKNOWN else GoogleServiceCapability.UNAVAILABLE
    } catch (_: RuntimeException) {
        GoogleServiceCapability.UNKNOWN
    }
}

/** factory 必須延遲執行，避免在不支援的環境先建立 Google client。 */
fun <T> GoogleServiceCapability.select(google: () -> T, system: () -> T, unknown: () -> T): T = when (this) {
    GoogleServiceCapability.AVAILABLE -> google()
    GoogleServiceCapability.UNAVAILABLE -> system()
    GoogleServiceCapability.UNKNOWN -> unknown()
}
