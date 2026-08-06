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

enum class PlayStoreAvailability {
    AVAILABLE,
    DISABLED,
    MISSING,
    UNUSABLE
}

enum class PlayStorePackageState {
    ENABLED,
    DISABLED,
    MISSING
}

interface PlayStoreEnvironment {
    fun packageState(): PlayStorePackageState
    fun canResolveProductPage(): Boolean
}

class PlayStoreAvailabilityDetector(private val environment: PlayStoreEnvironment) {
    constructor(context: Context) : this(AndroidPlayStoreEnvironment(context))

    fun detect(): PlayStoreAvailability = try {
        when (environment.packageState()) {
            PlayStorePackageState.DISABLED -> PlayStoreAvailability.DISABLED
            PlayStorePackageState.MISSING -> PlayStoreAvailability.MISSING
            PlayStorePackageState.ENABLED -> if (environment.canResolveProductPage()) {
                PlayStoreAvailability.AVAILABLE
            } else {
                PlayStoreAvailability.UNUSABLE
            }
        }
    } catch (_: SecurityException) {
        PlayStoreAvailability.UNUSABLE
    } catch (_: RuntimeException) {
        PlayStoreAvailability.UNUSABLE
    }
}

private class AndroidPlayStoreEnvironment(context: Context) : PlayStoreEnvironment {
    private val applicationContext = context.applicationContext

    @Suppress("DEPRECATION")
    override fun packageState(): PlayStorePackageState = try {
        if (applicationContext.packageManager.getApplicationInfo(PLAY_PACKAGE_NAME, 0).enabled) {
            PlayStorePackageState.ENABLED
        } else {
            PlayStorePackageState.DISABLED
        }
    } catch (_: PackageManager.NameNotFoundException) {
        PlayStorePackageState.MISSING
    }

    override fun canResolveProductPage(): Boolean {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(AppUpdateLinks.PLAY_HTTPS_URL)
        ).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(PLAY_PACKAGE_NAME)
        return applicationContext.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        ) != null
    }
}

class AndroidPlayPackageProbe(context: Context) : PlayPackageProbe {
    private val detector = PlayStoreAvailabilityDetector(context)

    override fun isPlayAvailable(): Boolean =
        detector.detect() == PlayStoreAvailability.AVAILABLE
}

internal const val PLAY_PACKAGE_NAME = "com.android.vending"
