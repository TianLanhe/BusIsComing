package com.golink.busiscoming.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.golink.busiscoming.data.model.InitialInstallChannel

interface InstallSourceReader {
    fun installerPackageName(): String?
}

object InitialInstallChannelDetector {
    fun detect(reader: InstallSourceReader): InitialInstallChannel = try {
        when (reader.installerPackageName()?.trim()) {
            PLAY_PACKAGE_NAME -> InitialInstallChannel.PLAY
            null, "" -> InitialInstallChannel.UNKNOWN_NON_PLAY
            else -> InitialInstallChannel.NON_PLAY
        }
    } catch (_: RuntimeException) {
        InitialInstallChannel.UNKNOWN_NON_PLAY
    }
}

class AndroidInstallSourceReader(context: Context) : InstallSourceReader {
    private val applicationContext = context.applicationContext

    @Suppress("DEPRECATION")
    override fun installerPackageName(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        applicationContext.packageManager
            .getInstallSourceInfo(applicationContext.packageName)
            .installingPackageName
    } else {
        applicationContext.packageManager.getInstallerPackageName(applicationContext.packageName)
    }
}

interface PlayPackageProbe {
    fun isPlayAvailable(): Boolean
}

class AndroidPlayPackageProbe(context: Context) : PlayPackageProbe {
    private val applicationContext = context.applicationContext

    @Suppress("DEPRECATION")
    override fun isPlayAvailable(): Boolean {
        return try {
            val packageManager = applicationContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(PLAY_PACKAGE_NAME, 0)
            if (!applicationInfo.enabled) {
                false
            } else {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=${applicationContext.packageName}")
                ).setPackage(PLAY_PACKAGE_NAME)
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
            }
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }
}

internal const val PLAY_PACKAGE_NAME = "com.android.vending"
