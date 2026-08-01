package com.golink.busiscoming.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

enum class MonitorNotificationSettingsKind {
    CHANNEL,
    APP_NOTIFICATIONS,
    APP_DETAILS
}

data class MonitorNotificationSettingsRequest(
    val kind: MonitorNotificationSettingsKind,
    val action: String,
    val packageName: String,
    val channelId: String? = null,
    val dataUri: String? = null
)

enum class MonitorNotificationSettingsNavigationResult {
    CHANNEL,
    APP_NOTIFICATIONS,
    APP_DETAILS,
    MANUAL_GUIDANCE
}

class MonitorNotificationSettingsNavigator(
    private val packageName: String,
    private val sdkInt: Int,
    private val canOpen: (MonitorNotificationSettingsRequest) -> Boolean,
    private val openRequest: (MonitorNotificationSettingsRequest) -> Unit
) {
    fun open(channelId: String?): MonitorNotificationSettingsNavigationResult {
        requests(channelId).forEach { request ->
            if (canOpenSafely(request) && openSafely(request)) {
                return request.kind.toResult()
            }
        }
        return MonitorNotificationSettingsNavigationResult.MANUAL_GUIDANCE
    }

    private fun requests(channelId: String?): List<MonitorNotificationSettingsRequest> {
        return buildList {
            if (sdkInt >= Build.VERSION_CODES.O && channelId != null) {
                add(
                    MonitorNotificationSettingsRequest(
                        kind = MonitorNotificationSettingsKind.CHANNEL,
                        action = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS,
                        packageName = packageName,
                        channelId = channelId
                    )
                )
            }
            add(
                MonitorNotificationSettingsRequest(
                    kind = MonitorNotificationSettingsKind.APP_NOTIFICATIONS,
                    action = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                    packageName = packageName
                )
            )
            add(
                MonitorNotificationSettingsRequest(
                    kind = MonitorNotificationSettingsKind.APP_DETAILS,
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    packageName = packageName,
                    dataUri = "package:$packageName"
                )
            )
        }
    }

    private fun canOpenSafely(request: MonitorNotificationSettingsRequest): Boolean {
        return try {
            canOpen(request)
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun openSafely(request: MonitorNotificationSettingsRequest): Boolean {
        return try {
            openRequest(request)
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun MonitorNotificationSettingsKind.toResult():
        MonitorNotificationSettingsNavigationResult {
        return when (this) {
            MonitorNotificationSettingsKind.CHANNEL ->
                MonitorNotificationSettingsNavigationResult.CHANNEL
            MonitorNotificationSettingsKind.APP_NOTIFICATIONS ->
                MonitorNotificationSettingsNavigationResult.APP_NOTIFICATIONS
            MonitorNotificationSettingsKind.APP_DETAILS ->
                MonitorNotificationSettingsNavigationResult.APP_DETAILS
        }
    }

    companion object {
        fun forContext(context: Context): MonitorNotificationSettingsNavigator {
            return MonitorNotificationSettingsNavigator(
                packageName = context.packageName,
                sdkInt = Build.VERSION.SDK_INT,
                canOpen = { request ->
                    request.toIntent(context).resolveActivity(context.packageManager) != null
                },
                openRequest = { request -> context.startActivity(request.toIntent(context)) }
            )
        }

        private fun MonitorNotificationSettingsRequest.toIntent(context: Context): Intent {
            val intent = when (kind) {
                MonitorNotificationSettingsKind.CHANNEL -> Intent(action).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                }
                MonitorNotificationSettingsKind.APP_NOTIFICATIONS -> Intent(action).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                MonitorNotificationSettingsKind.APP_DETAILS ->
                    Intent(action, Uri.parse(dataUri))
            }
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return intent
        }
    }
}
