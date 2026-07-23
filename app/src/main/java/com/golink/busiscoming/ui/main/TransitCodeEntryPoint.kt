package com.golink.busiscoming.ui.main

import android.content.Context
import android.content.Intent

object TransitCodeEntryPoint {
    const val ACTION_OPEN_TRANSIT_CODE =
        "com.golink.busiscoming.action.OPEN_TRANSIT_CODE"

    fun isLaunchAction(action: String?): Boolean = action == ACTION_OPEN_TRANSIT_CODE

    fun createIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_TRANSIT_CODE
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    fun createShortcutIntent(context: Context): Intent {
        return Intent(context, TransitCodeShortcutActivity::class.java).apply {
            action = ACTION_OPEN_TRANSIT_CODE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
    }
}
