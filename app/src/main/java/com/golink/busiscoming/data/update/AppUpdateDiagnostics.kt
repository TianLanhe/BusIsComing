package com.golink.busiscoming.data.update

import android.util.Log
import com.golink.busiscoming.data.model.InitialInstallChannel
import com.golink.busiscoming.data.model.UpdateFailureKind

sealed interface AppUpdateDiagnosticEvent {
    data class PlaySuccess(
        val updateAvailability: Int,
        val availableVersionCode: Int
    ) : AppUpdateDiagnosticEvent

    data class PlayFailure(val errorCode: Int?) : AppUpdateDiagnosticEvent

    data class ChannelDecision(
        val initialInstallChannel: InitialInstallChannel,
        val decision: UpdateChannelDecision
    ) : AppUpdateDiagnosticEvent

    data class CompletedFailure(
        val kind: UpdateFailureKind
    ) : AppUpdateDiagnosticEvent
}

fun interface AppUpdateDiagnostics {
    fun record(event: AppUpdateDiagnosticEvent)
}

object NoOpAppUpdateDiagnostics : AppUpdateDiagnostics {
    override fun record(event: AppUpdateDiagnosticEvent) = Unit
}

object LogcatAppUpdateDiagnostics : AppUpdateDiagnostics {
    override fun record(event: AppUpdateDiagnosticEvent) {
        Log.i(TAG, event.toString())
    }

    private const val TAG = "AppUpdate"
}
