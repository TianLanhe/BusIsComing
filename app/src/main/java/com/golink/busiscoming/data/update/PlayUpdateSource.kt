package com.golink.busiscoming.data.update

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.golink.busiscoming.data.model.UpdateFailureKind
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

sealed interface PlayUpdateResult {
    data class Available(
        val availableVersionCode: Long,
        val stalenessDays: Int?,
        val flexibleAllowed: Boolean,
        val downloaded: Boolean
    ) : PlayUpdateResult

    data object NotAvailable : PlayUpdateResult
    data object AppNotOwned : PlayUpdateResult
    data class Failed(val kind: UpdateFailureKind) : PlayUpdateResult
}

object PlayUpdateResultMapper {
    fun success(
        availability: Int,
        availableVersionCode: Int,
        stalenessDays: Int?,
        flexibleAllowed: Boolean,
        downloaded: Boolean
    ): PlayUpdateResult = when (availability) {
        UpdateAvailability.UPDATE_AVAILABLE,
        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> PlayUpdateResult.Available(
            availableVersionCode = availableVersionCode.toLong(),
            stalenessDays = stalenessDays,
            flexibleAllowed = flexibleAllowed,
            downloaded = downloaded
        )
        UpdateAvailability.UPDATE_NOT_AVAILABLE -> PlayUpdateResult.NotAvailable
        else -> PlayUpdateResult.Failed(UpdateFailureKind.PLAY_TEMPORARY)
    }

    fun failure(errorCode: Int?): PlayUpdateResult = if (
        errorCode == InstallErrorCode.ERROR_APP_NOT_OWNED
    ) {
        PlayUpdateResult.AppNotOwned
    } else {
        PlayUpdateResult.Failed(UpdateFailureKind.PLAY_TEMPORARY)
    }
}

interface PlayUpdateSource {
    fun check(callback: (PlayUpdateResult) -> Unit)
    fun startFlexibleUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean
    fun refreshInstallStatus()
    fun completeUpdate(callback: (Boolean) -> Unit)
    fun setDownloadedListener(listener: ((Boolean) -> Unit)?)
}

class GooglePlayUpdateSource(
    context: Context,
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(context.applicationContext),
    private val diagnostics: AppUpdateDiagnostics = NoOpAppUpdateDiagnostics
) : PlayUpdateSource {
    private var latestInfo: AppUpdateInfo? = null
    private var downloadedListener: ((Boolean) -> Unit)? = null
    private var listenerRegistered = false
    private val installStateListener = InstallStateUpdatedListener { state ->
        downloadedListener?.invoke(state.installStatus() == InstallStatus.DOWNLOADED)
    }

    override fun check(callback: (PlayUpdateResult) -> Unit) {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                latestInfo = info
                callback(
                    PlayUpdateResultMapper.success(
                        availability = info.updateAvailability(),
                        availableVersionCode = info.availableVersionCode(),
                        stalenessDays = info.clientVersionStalenessDays(),
                        flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                        downloaded = info.installStatus() == InstallStatus.DOWNLOADED
                    )
                )
            }
            .addOnFailureListener { error ->
                val errorCode = (error as? InstallException)?.errorCode
                callback(PlayUpdateResultMapper.failure(errorCode))
            }
    }

    override fun startFlexibleUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean {
        val info = latestInfo ?: return false
        if (!info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) return false
        return try {
            manager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
            )
        } catch (_: RuntimeException) {
            false
        }
    }

    override fun refreshInstallStatus() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            latestInfo = info
            downloadedListener?.invoke(info.installStatus() == InstallStatus.DOWNLOADED)
        }
    }

    override fun completeUpdate(callback: (Boolean) -> Unit) {
        manager.completeUpdate()
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    override fun setDownloadedListener(listener: ((Boolean) -> Unit)?) {
        downloadedListener = listener
        if (listener != null && !listenerRegistered) {
            manager.registerListener(installStateListener)
            listenerRegistered = true
        }
    }
}
