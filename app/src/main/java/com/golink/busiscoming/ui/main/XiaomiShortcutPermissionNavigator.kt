package com.golink.busiscoming.ui.main

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

enum class XiaomiShortcutPermissionNavigationResult {
    XIAOMI_SETTINGS,
    APP_DETAILS,
    FAILED
}

class XiaomiShortcutPermissionNavigator(
    private val resolver: (Context, Intent) -> Boolean = { context, intent ->
        intent.resolveActivity(context.packageManager) != null
    },
    private val starter: (Context, Intent) -> Unit = { context, intent ->
        context.startActivity(intent)
    }
) {
    fun open(
        context: Context,
        activityStarter: ((Intent) -> Unit)? = null
    ): XiaomiShortcutPermissionNavigationResult {
        val xiaomiIntent = Intent(MIUI_APP_PERMISSION_ACTION)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .setPackage(MIUI_SECURITY_CENTER_PACKAGE)
            .putExtra(MIUI_PACKAGE_EXTRA, context.packageName)
        if (canResolve(context, xiaomiIntent) && start(context, xiaomiIntent, activityStarter)) {
            return XiaomiShortcutPermissionNavigationResult.XIAOMI_SETTINGS
        }

        val appDetailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        return if (
            canResolve(context, appDetailsIntent) &&
            start(context, appDetailsIntent, activityStarter)
        ) {
            XiaomiShortcutPermissionNavigationResult.APP_DETAILS
        } else {
            XiaomiShortcutPermissionNavigationResult.FAILED
        }
    }

    private fun canResolve(context: Context, intent: Intent): Boolean {
        return try {
            resolver(context, intent)
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun start(
        context: Context,
        intent: Intent,
        activityStarter: ((Intent) -> Unit)?
    ): Boolean {
        if (activityStarter == null && context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (activityStarter != null) {
                activityStarter(intent)
            } else {
                starter(context, intent)
            }
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private companion object {
        const val MIUI_APP_PERMISSION_ACTION = "miui.intent.action.APP_PERM_EDITOR"
        const val MIUI_SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
        const val MIUI_PACKAGE_EXTRA = "extra_pkgname"
    }
}
