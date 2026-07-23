package com.golink.busiscoming.ui.main

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.golink.busiscoming.R

enum class TransitCodeShortcutState {
    PINNED,
    NOT_PINNED,
    UNKNOWN
}

enum class TransitCodeShortcutRequestResult {
    ALREADY_PINNED,
    NEEDS_PERMISSION,
    REQUESTED,
    UNSUPPORTED,
    FAILED
}

object TransitCodeShortcutManager {
    private const val SHORTCUT_ID = "transit_code"
    internal const val ACTION_PINNED =
        "com.golink.busiscoming.action.TRANSIT_CODE_SHORTCUT_PINNED"

    fun currentState(context: Context): TransitCodeShortcutState {
        return try {
            val isPinned = ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_PINNED
            ).any { it.id == SHORTCUT_ID }
            if (isPinned) TransitCodeShortcutState.PINNED else TransitCodeShortcutState.NOT_PINNED
        } catch (_: RuntimeException) {
            TransitCodeShortcutState.UNKNOWN
        }
    }

    fun requestPinnedShortcut(
        context: Context,
        bypassXiaomiPermissionGate: Boolean = false
    ): TransitCodeShortcutRequestResult {
        if (currentState(context) == TransitCodeShortcutState.PINNED) {
            recordPinned(context)
            refreshPublishedShortcut(context)
            return TransitCodeShortcutRequestResult.ALREADY_PINNED
        }
        val permissionStore = XiaomiShortcutPermissionStateStore(context)
        val permissionAction = XiaomiShortcutPermissionPolicy().action(
            gatePassed = permissionStore.isGatePassed(),
            bypassPermissionGate = bypassXiaomiPermissionGate
        )
        if (permissionAction == XiaomiShortcutPermissionAction.OPEN_SETTINGS) {
            return TransitCodeShortcutRequestResult.NEEDS_PERMISSION
        }
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return TransitCodeShortcutRequestResult.UNSUPPORTED
        }
        val shortcut = createShortcut(context)
        refreshPublishedShortcut(context, shortcut)
        val callbackIntent = Intent(context, TransitCodeShortcutPinnedReceiver::class.java)
            .setAction(ACTION_PINNED)
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return try {
            if (ShortcutManagerCompat.requestPinShortcut(context, shortcut, callback.intentSender)) {
                TransitCodeShortcutRequestResult.REQUESTED
            } else {
                TransitCodeShortcutRequestResult.FAILED
            }
        } catch (_: RuntimeException) {
            TransitCodeShortcutRequestResult.FAILED
        }
    }

    fun refreshPublishedShortcut(context: Context) {
        refreshPublishedShortcut(context, createShortcut(context))
    }

    fun recordPinned(context: Context) {
        XiaomiShortcutPermissionStateStore(context).markGatePassed()
    }

    fun recordPinRequestIncomplete(context: Context) {
        if (XiaomiShortcutPermissionPolicy().isXiaomiFamily) {
            XiaomiShortcutPermissionStateStore(context).clearGate()
        }
    }

    private fun createShortcut(context: Context): ShortcutInfoCompat {
        return ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.transit_code))
            .setLongLabel(context.getString(R.string.transit_code_shortcut_long_label))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_transit_code))
            .setIntent(TransitCodeEntryPoint.createShortcutIntent(context))
            .build()
    }

    private fun refreshPublishedShortcut(
        context: Context,
        shortcut: ShortcutInfoCompat
    ) {
        try {
            ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
        } catch (_: RuntimeException) {
            // Static shortcuts can be immutable on some launchers; the manifest update remains authoritative.
        }
    }
}

class TransitCodeShortcutPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TransitCodeShortcutManager.ACTION_PINNED) return
        TransitCodeShortcutManager.recordPinned(context)
        Toast.makeText(context, R.string.transit_code_shortcut_added, Toast.LENGTH_SHORT).show()
    }
}
